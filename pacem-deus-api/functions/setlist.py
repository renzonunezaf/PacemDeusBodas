# ═══════════════════════════════════════════════════════════════
# Pacem Deus Bodas — Lambda: Gestión del Setlist
# IS276 — Plataformas Móviles y Análisis Cloud — Grupo 2
# ═══════════════════════════════════════════════════════════════
# Endpoints:
#   GET    /weddings/{id}/setlist          → Obtener setlist completo
#   POST   /weddings/{id}/setlist          → Agregar canto al setlist
#   DELETE /weddings/{id}/setlist/{itemId} → Quitar canto del setlist
# ═══════════════════════════════════════════════════════════════

from shared.db import (
    query, execute, get_body, get_path_param, success, error, require_auth
)


def handler(event, context):
    """Punto de entrada del Lambda. Enruta según método y recurso."""
    method = event.get("httpMethod", "")
    resource = event.get("resource", "")

    if method == "OPTIONS":
        return success({"message": "OK"})

    if resource == "/weddings/{id}/setlist" and method == "GET":
        return get_setlist(event)
    elif resource == "/weddings/{id}/setlist" and method == "POST":
        return add_to_setlist(event)
    elif resource == "/weddings/{id}/setlist/{itemId}" and method == "DELETE":
        return remove_from_setlist(event)

    return error("Recurso no encontrado", 404)


# ─── OBTENER SETLIST ─────────────────────────────────────────

def get_setlist(event):
    """Retorna el setlist completo de un evento, ordenado por momento."""
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    wedding_id = get_path_param(event, "id")

    # Verificar que el evento existe
    wedding = query("SELECT id FROM weddings WHERE id = %s", (wedding_id,), fetch_one=True)
    if not wedding:
        return error("Evento no encontrado", 404)

    # Obtener items del setlist con datos de canción y momento
    items = query("""
        SELECT si.id, si.display_order,
               s.id AS song_id, s.title AS song_title, s.author AS song_author, s.language,
               m.id AS moment_id, m.slug AS moment_slug, m.name AS moment_name,
               m.display_order AS moment_order
        FROM setlist_items si
        JOIN songs s ON s.id = si.song_id
        JOIN liturgical_moments m ON m.id = si.moment_id
        WHERE si.wedding_id = %s
        ORDER BY m.display_order ASC, si.display_order ASC
    """, (wedding_id,))

    return success(items)


# ─── AGREGAR CANTO AL SETLIST ────────────────────────────────

def add_to_setlist(event):
    """Agrega un canto a un momento específico del setlist."""
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    wedding_id = get_path_param(event, "id")
    body = get_body(event)
    song_id = body.get("songId")
    moment_id = body.get("momentId")

    if not song_id or not moment_id:
        return error("songId y momentId son requeridos")

    # Verificar que el evento existe y está en estado editable
    wedding = query("SELECT id, status FROM weddings WHERE id = %s", (wedding_id,), fetch_one=True)
    if not wedding:
        return error("Evento no encontrado", 404)

    # Solo admin puede editar en cualquier estado; novios solo en DRAFT/SUBMITTED
    if auth["role"] != "ADMIN" and wedding["status"] not in ("DRAFT", "SUBMITTED"):
        return error("El ensamble ya fue aprobado y no se puede modificar")

    # Verificar que la canción existe y está asociada al momento
    song_moment = query(
        "SELECT id FROM song_moments WHERE song_id = %s AND moment_id = %s",
        (song_id, moment_id), fetch_one=True
    )
    if not song_moment:
        return error("Esta canción no está disponible para este momento litúrgico")

    # Verificar que no se exceda el máximo de cantos por momento
    moment = query(
        "SELECT max_songs FROM liturgical_moments WHERE id = %s",
        (moment_id,), fetch_one=True
    )
    current_count = query(
        "SELECT COUNT(*) AS count FROM setlist_items WHERE wedding_id = %s AND moment_id = %s",
        (wedding_id, moment_id), fetch_one=True
    )
    if current_count and current_count["count"] >= moment["max_songs"]:
        return error(f"Este momento admite máximo {moment['max_songs']} canto(s)")

    # Verificar que no esté duplicado
    existing = query(
        "SELECT id FROM setlist_items WHERE wedding_id = %s AND moment_id = %s AND song_id = %s",
        (wedding_id, moment_id, song_id), fetch_one=True
    )
    if existing:
        return error("Este canto ya está en el setlist para este momento")

    # Calcular orden de inserción
    next_order = (current_count["count"] if current_count else 0) + 1

    # Insertar
    item = execute(
        """INSERT INTO setlist_items (wedding_id, moment_id, song_id, display_order)
           VALUES (%s, %s, %s, %s) RETURNING id""",
        (wedding_id, moment_id, song_id, next_order), returning=True
    )

    return success({"message": "Canto agregado al setlist", "id": item["id"]}, 201)


# ─── QUITAR CANTO DEL SETLIST ────────────────────────────────

def remove_from_setlist(event):
    """Quita un canto del setlist por su ID de item."""
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    wedding_id = get_path_param(event, "id")
    item_id = get_path_param(event, "itemId")

    # Verificar que el evento existe y está en estado editable
    wedding = query("SELECT id, status FROM weddings WHERE id = %s", (wedding_id,), fetch_one=True)
    if not wedding:
        return error("Evento no encontrado", 404)

    if auth["role"] != "ADMIN" and wedding["status"] not in ("DRAFT", "SUBMITTED"):
        return error("El ensamble ya fue aprobado y no se puede modificar")

    # Eliminar el item
    deleted = execute(
        "DELETE FROM setlist_items WHERE id = %s AND wedding_id = %s",
        (item_id, wedding_id)
    )

    if deleted == 0:
        return error("Item no encontrado en el setlist", 404)

    return success({"message": "Canto removido del setlist"})
