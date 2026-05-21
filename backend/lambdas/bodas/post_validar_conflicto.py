"""
post_validar_conflicto.py
POST /v1/bodas/validar-conflicto
Header: Authorization: Bearer <token>

Body: {
  "fecha": "2026-06-06",  -- YYYY-MM-DD
  "hora": "17:00",        -- HH:MM
  "id_boda_excluir": 12   -- opcional, para editar la propia boda
}

Devuelve si la fecha+hora propuesta entra en conflicto con bodas
CONTRACTED existentes. Util para que la app pre-valide antes de
guardar y muestre mensaje al usuario.

Respuesta 200:
  {
    "conflicto": true,
    "razon": "Ya existe una boda contratada ese dia a las 14:00...",
    "horas_disponibles": "19:00, 20:00"
  }
"""

from shared import db
from shared import auth
from shared import responses


def handle_post_validar_conflicto(event, context):
    try:
        auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    fecha = (body.get("fecha") or "").strip()
    hora = (body.get("hora") or "").strip()
    if not fecha or not hora:
        return responses.bad_request("fecha y hora son requeridos")

    if len(hora) == 5:
        hora = hora + ":00"

    excluir = body.get("id_boda_excluir")
    lat = body.get("latitud")
    lng = body.get("longitud")
    # Normalizar floats opcionales
    try:
        lat = float(lat) if lat is not None else None
        lng = float(lng) if lng is not None else None
    except (TypeError, ValueError):
        lat = None
        lng = None

    try:
        row = db.fetch_one(
            "usp_boda_validar_conflicto",
            (fecha, hora, lat, lng, excluir)
        )
    except Exception as e:
        return responses.server_error(f"Error al validar: {e}")

    if not row:
        return responses.server_error("SP no devolvio resultado")

    return responses.ok({
        "conflicto": bool(row["conflicto"]),
        "razon": row.get("razon") or "",
        "horas_disponibles": row.get("horas_disponibles") or "",
    })
