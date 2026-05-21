"""
get_auth_me.py
GET /v1/auth/me
Header: Authorization: Bearer <token>

Devuelve datos del usuario autenticado y su perfil.
"""

from shared import db
from shared import auth
from shared import responses


def handle_get_auth_me(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message) if e.status == 401 else responses.forbidden(e.message)

    id_usuario = payload["id_usuario"]
    rol = payload["rol"]

    user = db.fetch_one("usp_usuario_obtener_por_id", (id_usuario,))
    if not user or not user["activo"]:
        return responses.unauthorized("Usuario no encontrado o inactivo")

    profile = None
    if rol == "COUPLE":
        profile = db.fetch_one("usp_novios_obtener_por_usuario", (id_usuario,))
    elif rol == "WEDDING_PLANNER":
        profile = db.fetch_one("usp_planner_obtener_por_usuario", (id_usuario,))

    return responses.ok({
        "usuario": {
            "id_usuario": user["id_usuario"],
            "email": user["email"],
            "rol": user["rol"],
            "fecha_creacion": user["fecha_creacion"],
        },
        "perfil": profile,
    })
