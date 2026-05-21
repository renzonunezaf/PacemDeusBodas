"""
get_boda_fotos.py
GET /v1/bodas/{id_boda}/fotos
Header: Authorization: Bearer <token>

Devuelve la lista de fotos del local con caption y nombre del autor.

Respuesta 200:
  {
    "id_boda": 12,
    "total": 3,
    "fotos": [
      {
        "id_foto": 7,
        "url": "https://...",
        "orden": 1,
        "fecha_subida": "...",
        "caption": "Vista de la nave central",
        "autor_nombre": "Carlos y Maria",
        "autor_rol": "COUPLE",
        "creado_por_id_usuario": 5
      },
      ...
    ]
  }

autor_nombre puede ser null si la foto se subio antes de la actualizacion
de schema (v5). La UI lo oculta cuando es null.
"""

from shared import db
from shared import auth
from shared import responses


def handle_get_boda_fotos(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    if not _tiene_acceso(payload, boda):
        return responses.forbidden("No tienes acceso a esta boda")

    fotos = db.fetch_all("usp_boda_foto_listar", (id_boda,))

    # Sanitizamos para no exponer s3_key al cliente
    fotos_payload = [
        {
            "id_foto": f["id_foto"],
            "url": f["url"],
            "orden": f["orden"],
            "fecha_subida": _format_dt(f["fecha_subida"]),
            "caption": f.get("caption"),
            "autor_nombre": f.get("autor_nombre"),
            "autor_rol": f.get("autor_rol"),
            "creado_por_id_usuario": f.get("creado_por_id_usuario"),
        }
        for f in fotos
    ]

    return responses.ok({
        "id_boda": id_boda,
        "total": len(fotos_payload),
        "fotos": fotos_payload,
    })


def _format_dt(value):
    if value is None:
        return None
    return value.isoformat()


def _tiene_acceso(payload, boda):
    rol = payload["rol"]
    id_usuario = payload["id_usuario"]
    if rol == "ADMIN":
        return True
    if rol == "COUPLE":
        couple = db.fetch_one("usp_novios_obtener_por_usuario", (id_usuario,))
        return couple and couple["id_novios"] == boda["id_novios"]
    if rol == "WEDDING_PLANNER":
        if not boda.get("id_planner"):
            return False
        planner = db.fetch_one("usp_planner_obtener_por_usuario", (id_usuario,))
        return planner and planner["id_planner"] == boda["id_planner"]
    return False
