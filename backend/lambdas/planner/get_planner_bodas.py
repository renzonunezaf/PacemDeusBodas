"""
get_planner_bodas.py
GET /v1/planner/bodas
Header: Authorization: Bearer <token> (rol: WEDDING_PLANNER)

Devuelve la lista de bodas asignadas al wedding planner autenticado.
"""

from shared import db
from shared import auth
from shared import responses


def handle_get_planner_bodas(event, context):
    try:
        payload = auth.authenticate(event, allowed_roles=["WEDDING_PLANNER"])
    except auth.AuthError as e:
        return responses.unauthorized(e.message) if e.status == 401 else responses.forbidden(e.message)

    planner = db.fetch_one("usp_planner_obtener_por_usuario", (payload["id_usuario"],))
    if not planner:
        return responses.bad_request("Tu perfil de planner no esta completo")

    bodas = db.fetch_all("usp_bodas_listar", (None, planner["id_planner"], None))

    return responses.ok({
        "id_planner": planner["id_planner"],
        "total": len(bodas),
        "bodas": bodas,
    })
