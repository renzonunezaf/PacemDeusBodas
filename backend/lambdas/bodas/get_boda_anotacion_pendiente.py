"""
get_boda_anotacion_pendiente.py
GET /v1/bodas/{id_boda}/anotaciones/pendiente
Solo COUPLE.

Devuelve la ultima anotacion en estado PENDIENTE para esta boda,
con el snapshot del estado anterior y el precio nuevo. La app la usa
para mostrar el comparativo "antes vs ahora" cuando la boda esta en
RETURNED_WITH_NOTES.

Respuesta 200:
  {
    "id_boda_anotacion": 12,
    "id_boda": 22,
    "nombre_autor": "Coro Pacem Deus",
    "texto_nota": "...",
    "snapshot_antes": {...},
    "campos_modificados": "venue,setlist",
    "precio_anterior": 950.00,
    "precio_nuevo": 1100.00,
    "fecha_creacion": "2026-05-14T..."
  }

Respuesta 404: no hay anotacion pendiente.
"""

import json
from shared import db
from shared import auth
from shared import responses


def handle_get_boda_anotacion_pendiente(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    # COUPLE solo ve sus propias bodas
    if payload["rol"] == "COUPLE":
        boda = db.fetch_one("usp_boda_obtener", (id_boda,))
        if not boda:
            return responses.not_found("Boda no encontrada")
        couple = db.fetch_one("usp_novios_obtener_por_usuario",
                              (payload["id_usuario"],))
        if not couple or couple["id_novios"] != boda["id_novios"]:
            return responses.forbidden("No tienes acceso a esta boda")

    anotacion = db.fetch_one("usp_anotacion_obtener_pendiente", (id_boda,))
    if not anotacion:
        return responses.not_found("No hay anotaciones pendientes")

    try:
        snapshot = json.loads(anotacion.get("snapshot_antes") or "{}")
    except (ValueError, TypeError):
        snapshot = {}

    fecha = anotacion.get("fecha_creacion")
    fecha_str = fecha.isoformat() if hasattr(fecha, "isoformat") else str(fecha)

    return responses.ok({
        "id_boda_anotacion": anotacion.get("id_boda_anotacion"),
        "id_boda": anotacion.get("id_boda"),
        "nombre_autor": anotacion.get("nombre_autor"),
        "texto_nota": anotacion.get("texto_nota"),
        "snapshot_antes": snapshot,
        "campos_modificados": anotacion.get("campos_modificados") or "",
        "precio_anterior": float(anotacion.get("precio_anterior") or 0),
        "precio_nuevo": float(anotacion.get("precio_nuevo") or 0),
        "fecha_creacion": fecha_str,
    })
