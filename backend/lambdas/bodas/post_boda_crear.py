"""
post_boda_crear.py
POST /v1/bodas
Header: Authorization: Bearer <token> (rol: COUPLE o ADMIN)

Body:
  {
    "fechaBoda":       "2026-12-15",
    "horaBoda":        "16:00",
    "nombreLocal":     "Parroquia Sagrada Familia",
    "direccionLocal":  "Av. Comandante Espinar 800, Bellavista",
    "latitud":         -12.0541,
    "longitud":        -77.0933,
    "instrumentos":    ["violin_1"],     // opcional: instrumentos adicionales
    "idNovios":        5                  // opcional, solo ADMIN
  }

Notas del modelo nuevo:
  - Piano y voz_femenina YA NO se piden como obligatorios. Vienen incluidos
    en el paquete base (S/. 650) y se marcan en la tabla instrumento con
    incluido_en_paquete_base = 1.
  - Como conveniencia y para no romper datos legacy, si el cliente NO envia
    "instrumentos" en el body, asumimos lista vacia (solo el paquete base).
  - Aun asi guardamos piano y voz_femenina en boda_instrumentos para que
    aparezcan en el ensamble musical y el setlist (con precio 0 en la
    cotizacion).
"""

from datetime import datetime
from shared import auth
from shared import db
from shared import pricing
from shared import responses


# Instrumentos que SIEMPRE estan en una boda (incluidos en paquete base).
# Se guardan en boda_instrumentos para que el setlist los reconozca como
# disponibles, pero su cargo es S/. 0 en la cotizacion.
INSTRUMENTOS_INCLUIDOS_EN_BASE = ["piano", "voz_femenina"]


def handle_post_boda_crear(event, context):
    # ─── Autenticacion ─────────────────────────────────────────────
    try:
        payload = auth.authenticate(event, allowed_roles=["COUPLE", "ADMIN"])
    except auth.AuthError as e:
        if e.status == 401:
            return responses.unauthorized(e.message)
        return responses.forbidden(e.message)

    # ─── Body ──────────────────────────────────────────────────────
    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    fecha_str = body.get("fechaBoda")
    hora_str = body.get("horaBoda")
    nombre_local = (body.get("nombreLocal") or "").strip()
    direccion = (body.get("direccionLocal") or "").strip()
    latitud = body.get("latitud")
    longitud = body.get("longitud")
    instrumentos_extra = body.get("instrumentos") or []

    if not fecha_str or not hora_str:
        return responses.bad_request("Fecha y hora son requeridos")
    if not nombre_local or not direccion:
        return responses.bad_request("Nombre y direccion del local son requeridos")
    if latitud is None or longitud is None:
        return responses.bad_request("Latitud y longitud son requeridos")

    try:
        latitud = float(latitud)
        longitud = float(longitud)
    except (TypeError, ValueError):
        return responses.bad_request("Latitud y longitud deben ser numericos")

    if not isinstance(instrumentos_extra, list):
        return responses.bad_request("instrumentos debe ser una lista de slugs")

    # ─── Combinar instrumentos: incluidos en base + extras ────────
    # Garantizamos que piano y voz_femenina queden en boda_instrumentos
    # (aunque no se cobren). Esto facilita el ensamble musical.
    instrumentos_finales = list(INSTRUMENTOS_INCLUIDOS_EN_BASE)
    for slug in instrumentos_extra:
        if slug not in instrumentos_finales:
            instrumentos_finales.append(slug)

    # ─── Resolver id_novios segun rol ─────────────────────────────
    if payload["rol"] == "ADMIN":
        id_novios = body.get("idNovios")
        if not id_novios:
            return responses.bad_request(
                "idNovios es requerido cuando ADMIN crea una boda"
            )
    else:
        couple = db.fetch_one(
            "usp_novios_obtener_por_usuario", (payload["id_usuario"],)
        )
        if not couple:
            return responses.bad_request("Tu perfil de novios no esta completo")
        id_novios = couple["id_novios"]

    # ─── Parsear fecha/hora + validar fecha futura ────────────────
    try:
        fecha_part = datetime.strptime(fecha_str, "%Y-%m-%d").date()
        h, mn = hora_str.split(":")
        departure_dt = datetime(
            fecha_part.year, fecha_part.month, fecha_part.day,
            int(h), int(mn)
        )
    except (ValueError, IndexError):
        return responses.bad_request(
            "Formato invalido. Fecha YYYY-MM-DD y hora HH:MM"
        )

    if fecha_part < datetime.now().date():
        return responses.bad_request(
            "La fecha del evento debe ser igual o posterior a hoy"
        )

    # ─── Calcular cotizacion ─────────────────────────────────────
    breakdown = pricing.calculate_price_breakdown(
        latitud, longitud, instrumentos_finales, departure_dt
    )

    # ─── Crear la boda via SP ────────────────────────────────────
    boda_result = db.execute_returning_id(
        "usp_boda_crear",
        (
            id_novios, fecha_part, hora_str,
            nombre_local, direccion, latitud, longitud,
            1 if breakdown["fuera_de_lima"] else 0,
            breakdown["precio_base"],
            breakdown["precio_instrumentos"],
            breakdown["precio_movilidad"],
            breakdown["precio_total"],
        )
    )
    id_boda = boda_result["id_boda"]

    # ─── Insertar instrumentos (incluye los del paquete base) ─────
    db.execute(
        "usp_boda_instrumentos_reemplazar",
        (id_boda, ",".join(instrumentos_finales))
    )

    return responses.created({
        "id_boda": id_boda,
        "id_novios": id_novios,
        "fecha_boda": fecha_str,
        "hora_boda": hora_str,
        "nombre_local": nombre_local,
        "direccion_local": direccion,
        "latitud": latitud,
        "longitud": longitud,
        "estado": "DRAFT",
        "instrumentos": instrumentos_finales,
        "desglose_precio": breakdown,
    })
