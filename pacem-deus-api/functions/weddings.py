# ═══════════════════════════════════════════════════════════════
# Pacem Deus Bodas — Lambda: Gestión de Bodas
# IS276 — Plataformas Móviles y Análisis Cloud — Grupo 2
# ═══════════════════════════════════════════════════════════════
# Endpoints:
#   POST   /weddings               → Crear evento (couple)
#   GET    /weddings               → Lista de eventos (filtrado por rol)
#   GET    /weddings/{id}          → Detalle de un evento
#   PATCH  /weddings/{id}          → Editar datos del evento (couple en DRAFT)
#   POST   /weddings/{id}/submit   → Enviar al coro (DRAFT→SUBMITTED)
#   POST   /weddings/{id}/cancel   → Solicitar cancelación
#   POST   /weddings/{id}/approve  → Aprobar o devolver evento (admin)
#   POST   /weddings/{id}/photo    → Subir foto del local
#   PUT    /weddings/{id}/planner  → Asignar wedding planner
#   POST   /weddings/{id}/instruments → Elegir instrumentos
#   GET    /weddings/{id}/contract → Preview HTML del contrato
#   GET    /wedding-planners       → Lista de planners registrados (admin)
# ═══════════════════════════════════════════════════════════════

import base64
import os
from shared.db import (
    query, execute, get_body, get_path_param, success, error,
    require_auth, require_admin
)


def handler(event, context):
    """Punto de entrada del Lambda. Enruta según método y recurso."""
    method = event.get("httpMethod", "")
    resource = event.get("resource", "")

    if method == "OPTIONS":
        return success({"message": "OK"})

    # Rutas
    if resource == "/weddings" and method == "GET":
        return list_weddings(event)
    elif resource == "/weddings" and method == "POST":
        return create_wedding(event)
    elif resource == "/weddings/{id}" and method == "GET":
        return get_wedding(event)
    elif resource == "/weddings/{id}" and method == "PATCH":
        return update_wedding(event)
    elif resource == "/weddings/{id}/submit" and method == "POST":
        return submit_wedding(event)
    elif resource == "/weddings/{id}/cancel" and method == "POST":
        return cancel_wedding(event)
    elif resource == "/weddings/{id}/approve" and method == "POST":
        return approve_wedding(event)
    elif resource == "/weddings/{id}/photo" and method == "POST":
        return upload_photo(event)
    elif resource == "/weddings/{id}/planner" and method == "PUT":
        return assign_planner(event)
    elif resource == "/weddings/{id}/instruments" and method == "POST":
        return set_instruments(event)
    elif resource == "/weddings/{id}/contract" and method == "GET":
        return get_contract_html(event)
    elif resource == "/wedding-planners" and method == "GET":
        return list_planners(event)

    return error("Recurso no encontrado", 404)


# ─── LISTAR EVENTOS ─────────────────────────────────────────

def list_weddings(event):
    """
    Lista eventos según el rol del usuario:
    - ADMIN: todos los eventos
    - COUPLE: solo su evento
    - WEDDING_PLANNER: solo eventos asignados
    """
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    if auth["role"] == "ADMIN":
        # Admin ve todos los eventos
        weddings = query("""
            SELECT w.id, w.wedding_date, w.wedding_time, w.venue_name, w.venue_address,
                   w.venue_lat, w.venue_lng, w.venue_photo_url, w.status, w.total_price,
                   w.notes, w.created_at,
                   c.groom_name, c.bride_name, c.phone,
                   wp.name AS planner_name, wp.company AS planner_company
            FROM weddings w
            JOIN couples c ON c.id = w.couple_id
            LEFT JOIN wedding_planners wp ON wp.id = w.planner_id
            ORDER BY w.wedding_date ASC
        """)

    elif auth["role"] == "COUPLE":
        # Novios ven solo su evento
        couple = query(
            "SELECT id FROM couples WHERE user_id = %s", (auth["userId"],), fetch_one=True
        )
        if not couple:
            return success([])
        weddings = query("""
            SELECT w.id, w.wedding_date, w.wedding_time, w.venue_name, w.venue_address,
                   w.venue_lat, w.venue_lng, w.venue_photo_url, w.status, w.total_price,
                   w.notes, w.created_at,
                   c.groom_name, c.bride_name, c.phone,
                   wp.name AS planner_name
            FROM weddings w
            JOIN couples c ON c.id = w.couple_id
            LEFT JOIN wedding_planners wp ON wp.id = w.planner_id
            WHERE w.couple_id = %s
            ORDER BY w.wedding_date ASC
        """, (couple["id"],))

    else:
        # Planner ve solo eventos asignados (se maneja en planner.py)
        return error("Use /planner/weddings para wedding planners", 403)

    return success(weddings)


# ─── DETALLE DE EVENTO ───────────────────────────────────────

def get_wedding(event):
    """Retorna el detalle completo de un evento con instrumentos y setlist."""
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    wedding_id = get_path_param(event, "id")

    # Datos de la boda
    wedding = query("""
        SELECT w.*, c.groom_name, c.bride_name, c.phone, c.groom_dni, c.bride_dni,
               u.email AS couple_email,
               wp.name AS planner_name, wp.company AS planner_company
        FROM weddings w
        JOIN couples c ON c.id = w.couple_id
        JOIN users u ON u.id = c.user_id
        LEFT JOIN wedding_planners wp ON wp.id = w.planner_id
        WHERE w.id = %s
    """, (wedding_id,), fetch_one=True)

    if not wedding:
        return error("Evento no encontrado", 404)

    # Verificar acceso según rol
    if auth["role"] == "COUPLE":
        couple = query("SELECT id FROM couples WHERE user_id = %s", (auth["userId"],), fetch_one=True)
        if not couple or couple["id"] != wedding["couple_id"]:
            return error("Sin acceso", 403)
    elif auth["role"] == "WEDDING_PLANNER":
        planner = query("SELECT id FROM wedding_planners WHERE user_id = %s", (auth["userId"],), fetch_one=True)
        if not planner or planner["id"] != wedding.get("planner_id"):
            return error("Sin acceso", 403)

    # Instrumentos seleccionados
    instruments = query("""
        SELECT i.slug, i.name, i.icon, i.price_lima
        FROM wedding_instruments wi
        JOIN instruments i ON i.id = wi.instrument_id
        WHERE wi.wedding_id = %s
        ORDER BY i.sort_order
    """, (wedding_id,))

    # Setlist
    setlist = query("""
        SELECT si.id, si.display_order,
               s.title AS song_title, s.author AS song_author,
               m.name AS moment_name, m.slug AS moment_slug, m.display_order AS moment_order
        FROM setlist_items si
        JOIN songs s ON s.id = si.song_id
        JOIN liturgical_moments m ON m.id = si.moment_id
        WHERE si.wedding_id = %s
        ORDER BY m.display_order, si.display_order
    """, (wedding_id,))

    # Contrato (si existe)
    contract = query(
        "SELECT id, couple_signed, admin_signed FROM contracts WHERE wedding_id = %s",
        (wedding_id,), fetch_one=True
    )

    wedding["instruments"] = instruments
    wedding["setlist"] = setlist
    wedding["contract"] = contract

    return success(wedding)


# ─── APROBAR / DEVOLVER EVENTO ──────────────────────────────

def approve_wedding(event):
    """Aprueba o devuelve un evento (solo admin)."""
    try:
        auth = require_admin(event)
    except PermissionError as e:
        return error(str(e), 403)

    wedding_id = get_path_param(event, "id")
    body = get_body(event)
    action = body.get("action")  # "approve" o "reject"

    if action not in ("approve", "reject"):
        return error("Acción inválida. Usa 'approve' o 'reject'.")

    # Verificar que el evento existe
    wedding = query("SELECT id, status FROM weddings WHERE id = %s", (wedding_id,), fetch_one=True)
    if not wedding:
        return error("Evento no encontrado", 404)

    if wedding["status"] not in ("DRAFT", "SUBMITTED"):
        return error("Solo se pueden aprobar eventos en borrador o enviados")

    new_status = "APPROVED" if action == "approve" else "DRAFT"
    notes = body.get("notes")

    execute(
        "UPDATE weddings SET status = %s, notes = COALESCE(%s, notes), updated_at = now() WHERE id = %s",
        (new_status, notes, wedding_id)
    )

    return success({
        "message": "Evento aprobado" if action == "approve" else "Evento devuelto a borrador",
        "status": new_status,
    })


# ─── SUBIR FOTO DEL LOCAL ───────────────────────────────────

def upload_photo(event):
    """
    Recibe la foto como base64 en el body (enviada por Android)
    y la guarda en el servidor. Registra la referencia en la BD.
    """
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    wedding_id = get_path_param(event, "id")

    # Verificar que el evento existe
    wedding = query("SELECT id FROM weddings WHERE id = %s", (wedding_id,), fetch_one=True)
    if not wedding:
        return error("Evento no encontrado", 404)

    body = get_body(event)
    photo_base64 = body.get("photo")
    content_type = body.get("contentType", "image/jpeg")

    if not photo_base64:
        return error("No se recibió la foto")

    # Decodificar y guardar en /tmp (Lambda tiene acceso de escritura a /tmp)
    try:
        photo_bytes = base64.b64decode(photo_base64)
    except Exception:
        return error("Formato de imagen inválido")

    # Generar nombre y guardar archivo
    ext = "png" if "png" in content_type else "jpg"
    filename = f"{wedding_id}.{ext}"
    filepath = f"/tmp/{filename}"

    with open(filepath, "wb") as f:
        f.write(photo_bytes)

    # Registrar referencia en la BD
    photo_url = f"/uploads/venues/{filename}"
    execute(
        "UPDATE weddings SET venue_photo_url = %s, updated_at = now() WHERE id = %s",
        (photo_url, wedding_id)
    )

    return success({
        "message": "Foto subida exitosamente",
        "photoUrl": photo_url,
    })


# ─── ASIGNAR WEDDING PLANNER ────────────────────────────────

def assign_planner(event):
    """Asigna o cambia el wedding planner de un evento (solo admin)."""
    try:
        auth = require_admin(event)
    except PermissionError as e:
        return error(str(e), 403)

    wedding_id = get_path_param(event, "id")
    body = get_body(event)
    planner_id = body.get("plannerId")  # Puede ser None para quitar

    # Verificar que el evento existe
    wedding = query("SELECT id FROM weddings WHERE id = %s", (wedding_id,), fetch_one=True)
    if not wedding:
        return error("Evento no encontrado", 404)

    # Si se asigna un planner, verificar que existe
    if planner_id:
        planner = query("SELECT id, name FROM wedding_planners WHERE id = %s", (planner_id,), fetch_one=True)
        if not planner:
            return error("Wedding planner no encontrado", 404)

    execute(
        "UPDATE weddings SET planner_id = %s, updated_at = now() WHERE id = %s",
        (planner_id, wedding_id)
    )

    return success({"message": "Wedding planner asignado exitosamente"})


# ─── LISTA DE WEDDING PLANNERS ──────────────────────────────

def list_planners(event):
    """Retorna la lista de wedding planners registrados (solo admin)."""
    try:
        auth = require_admin(event)
    except PermissionError as e:
        return error(str(e), 403)

    planners = query("""
        SELECT wp.id, wp.name, wp.company, wp.phone, u.email,
               (SELECT COUNT(*) FROM weddings w WHERE w.planner_id = wp.id) AS wedding_count
        FROM wedding_planners wp
        JOIN users u ON u.id = wp.user_id
        WHERE u.is_active = true
        ORDER BY wp.name
    """)

    return success(planners)


# ─── CREAR EVENTO (POR EL COUPLE) ───────────────────────────

def create_wedding(event):
    """
    Crea un evento en estado DRAFT para el couple autenticado.
    El novio/novia solo puede tener una boda activa a la vez.
    """
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    if auth["role"] != "COUPLE":
        return error("Solo los novios pueden crear su evento", 403)

    # Obtener el perfil de couple del usuario
    couple = query(
        "SELECT id FROM couples WHERE user_id = %s",
        (auth["userId"],), fetch_one=True
    )
    if not couple:
        return error("Perfil de novio no encontrado", 404)

    # Verificar que no tenga ya una boda activa (no completada/cancelada)
    existing = query(
        "SELECT id FROM weddings WHERE couple_id = %s AND status NOT IN ('COMPLETED')",
        (couple["id"],), fetch_one=True
    )
    if existing:
        return error("Ya tienes un evento registrado. Solo puedes tener uno activo.")

    body = get_body(event)
    wedding_date = body.get("weddingDate")
    wedding_time = body.get("weddingTime")
    venue_name = body.get("venueName")
    venue_address = body.get("venueAddress")
    venue_lat = body.get("venueLat")
    venue_lng = body.get("venueLng")

    # Validación de campos obligatorios
    if not all([wedding_date, wedding_time, venue_name, venue_address]):
        return error("Faltan campos obligatorios: fecha, hora, lugar y dirección")

    # Precio base por defecto (el admin lo ajustará después)
    base_price = 1800.00  # S/. 1800 precio base típico del coro

    result = execute("""
        INSERT INTO weddings (
            couple_id, wedding_date, wedding_time, venue_name, venue_address,
            venue_lat, venue_lng, status, base_price, total_price
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, 'DRAFT', %s, %s)
        RETURNING id
    """, (
        couple["id"], wedding_date, wedding_time, venue_name, venue_address,
        venue_lat, venue_lng, base_price, base_price
    ), returning=True)

    return success({
        "message": "Evento creado exitosamente",
        "weddingId": result["id"],
    }, status_code=201)


# ─── EDITAR DATOS DEL EVENTO ────────────────────────────────

def update_wedding(event):
    """
    Permite al couple editar fecha, hora y lugar de su boda
    mientras siga en DRAFT. El admin puede editar en cualquier estado.
    """
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    wedding_id = get_path_param(event, "id")
    wedding = query(
        "SELECT id, couple_id, status FROM weddings WHERE id = %s",
        (wedding_id,), fetch_one=True
    )
    if not wedding:
        return error("Evento no encontrado", 404)

    # Verificar propiedad si es couple
    if auth["role"] == "COUPLE":
        couple = query(
            "SELECT id FROM couples WHERE user_id = %s",
            (auth["userId"],), fetch_one=True
        )
        if not couple or couple["id"] != wedding["couple_id"]:
            return error("Sin acceso", 403)
        # Couple solo edita en DRAFT
        if wedding["status"] != "DRAFT":
            return error("Solo puedes editar mientras tu evento esté en borrador")

    body = get_body(event)

    # Campos editables y validación básica
    fields = {
        "wedding_date": body.get("weddingDate"),
        "wedding_time": body.get("weddingTime"),
        "venue_name": body.get("venueName"),
        "venue_address": body.get("venueAddress"),
        "venue_lat": body.get("venueLat"),
        "venue_lng": body.get("venueLng"),
    }
    updates = {k: v for k, v in fields.items() if v is not None}

    if not updates:
        return error("No hay campos para actualizar")

    set_clause = ", ".join(f"{k} = %s" for k in updates.keys())
    params = list(updates.values()) + [wedding_id]

    execute(
        f"UPDATE weddings SET {set_clause}, updated_at = now() WHERE id = %s",
        tuple(params)
    )

    return success({"message": "Evento actualizado"})


# ─── ENVIAR AL CORO (DRAFT → SUBMITTED) ─────────────────────

def submit_wedding(event):
    """
    El couple envía su ensamble al coro para aprobación.
    Pasa de DRAFT a SUBMITTED. Solo si tiene al menos una canción.
    """
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    if auth["role"] != "COUPLE":
        return error("Solo los novios pueden enviar su ensamble", 403)

    wedding_id = get_path_param(event, "id")
    wedding = query(
        "SELECT id, couple_id, status FROM weddings WHERE id = %s",
        (wedding_id,), fetch_one=True
    )
    if not wedding:
        return error("Evento no encontrado", 404)

    # Verificar propiedad
    couple = query(
        "SELECT id FROM couples WHERE user_id = %s",
        (auth["userId"],), fetch_one=True
    )
    if not couple or couple["id"] != wedding["couple_id"]:
        return error("Sin acceso", 403)

    if wedding["status"] != "DRAFT":
        return error("Tu evento ya fue enviado")

    # Validar que tenga al menos una canción
    count = query(
        "SELECT COUNT(*) AS n FROM setlist_items WHERE wedding_id = %s",
        (wedding_id,), fetch_one=True
    )
    if count["n"] == 0:
        return error("Agrega al menos una canción antes de enviar")

    execute(
        "UPDATE weddings SET status = 'SUBMITTED', updated_at = now() WHERE id = %s",
        (wedding_id,)
    )

    return success({"message": "Ensamble enviado al coro para aprobación"})


# ─── SOLICITAR CANCELACIÓN ──────────────────────────────────

def cancel_wedding(event):
    """
    El couple solicita cancelar su evento.
    El estado pasa a CANCELLATION_REQUESTED; el admin puede confirmar después.
    """
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    wedding_id = get_path_param(event, "id")
    wedding = query(
        "SELECT id, couple_id, status FROM weddings WHERE id = %s",
        (wedding_id,), fetch_one=True
    )
    if not wedding:
        return error("Evento no encontrado", 404)

    if auth["role"] == "COUPLE":
        couple = query(
            "SELECT id FROM couples WHERE user_id = %s",
            (auth["userId"],), fetch_one=True
        )
        if not couple or couple["id"] != wedding["couple_id"]:
            return error("Sin acceso", 403)

    if wedding["status"] in ("COMPLETED", "CANCELLATION_REQUESTED"):
        return error("No puedes cancelar este evento en su estado actual")

    body = get_body(event)
    reason = body.get("reason", "")

    execute(
        "UPDATE weddings SET status = 'CANCELLATION_REQUESTED', notes = %s, updated_at = now() WHERE id = %s",
        (reason, wedding_id)
    )

    return success({"message": "Solicitud de cancelación registrada"})


# ─── ELEGIR INSTRUMENTOS ────────────────────────────────────

def set_instruments(event):
    """
    Reemplaza la lista de instrumentos de un evento con los proporcionados.
    Recalcula el precio total sumando el precio de cada instrumento.
    """
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    wedding_id = get_path_param(event, "id")
    wedding = query(
        "SELECT id, couple_id, status, base_price FROM weddings WHERE id = %s",
        (wedding_id,), fetch_one=True
    )
    if not wedding:
        return error("Evento no encontrado", 404)

    # Verificar acceso
    if auth["role"] == "COUPLE":
        couple = query(
            "SELECT id FROM couples WHERE user_id = %s",
            (auth["userId"],), fetch_one=True
        )
        if not couple or couple["id"] != wedding["couple_id"]:
            return error("Sin acceso", 403)
        if wedding["status"] not in ("DRAFT", "SUBMITTED"):
            return error("Ya no puedes editar los instrumentos")

    body = get_body(event)
    instrument_ids = body.get("instrumentIds", [])

    # Borrar instrumentos actuales
    execute("DELETE FROM wedding_instruments WHERE wedding_id = %s", (wedding_id,))

    # Insertar nuevos y calcular precio
    instruments_total = 0.0
    for inst_id in instrument_ids:
        inst = query(
            "SELECT id, price_lima FROM instruments WHERE id = %s AND is_active = true",
            (inst_id,), fetch_one=True
        )
        if not inst:
            continue
        execute(
            "INSERT INTO wedding_instruments (wedding_id, instrument_id) VALUES (%s, %s)",
            (wedding_id, inst["id"])
        )
        instruments_total += float(inst["price_lima"])

    # Actualizar precios de la boda
    base = float(wedding["base_price"])
    total = base + instruments_total
    execute("""
        UPDATE weddings
        SET instruments_price = %s, total_price = %s, updated_at = now()
        WHERE id = %s
    """, (instruments_total, total, wedding_id))

    return success({
        "message": "Instrumentos actualizados",
        "instrumentsPrice": instruments_total,
        "totalPrice": total,
    })


# ─── PREVIEW HTML DEL CONTRATO ──────────────────────────────

def get_contract_html(event):
    """
    Genera un preview HTML sencillo del contrato del evento,
    con los datos principales. Pensado para ser mostrado en WebView Android.
    """
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    wedding_id = get_path_param(event, "id")

    wedding = query("""
        SELECT w.*, c.groom_name, c.bride_name, c.phone,
               c.groom_dni, c.bride_dni
        FROM weddings w
        JOIN couples c ON c.id = w.couple_id
        WHERE w.id = %s
    """, (wedding_id,), fetch_one=True)

    if not wedding:
        return error("Evento no encontrado", 404)

    # Instrumentos contratados
    instruments = query("""
        SELECT i.name, i.price_lima
        FROM wedding_instruments wi
        JOIN instruments i ON i.id = wi.instrument_id
        WHERE wi.wedding_id = %s
        ORDER BY i.sort_order
    """, (wedding_id,))

    return success({
        "weddingId": wedding_id,
        "groomName": wedding["groom_name"],
        "brideName": wedding["bride_name"],
        "groomDni": wedding["groom_dni"],
        "brideDni": wedding["bride_dni"],
        "phone": wedding["phone"],
        "weddingDate": str(wedding["wedding_date"]),
        "weddingTime": wedding["wedding_time"],
        "venueName": wedding["venue_name"],
        "venueAddress": wedding["venue_address"],
        "basePrice": float(wedding["base_price"]),
        "instrumentsPrice": float(wedding["instruments_price"]),
        "totalPrice": float(wedding["total_price"]),
        "status": wedding["status"],
        "instruments": instruments,
    })
