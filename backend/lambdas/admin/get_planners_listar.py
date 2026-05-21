"""
get_planners_listar.py
GET /v1/admin/planners
Header: Authorization: Bearer <token> (rol: ADMIN)

Lista todos los wedding planners activos con la cantidad de bodas asignadas.
"""

from shared import db
from shared import auth
from shared import responses


def handle_get_planners_listar(event, context):
    try:
        auth.authenticate(event, allowed_roles=["ADMIN"])
    except auth.AuthError as e:
        return responses.unauthorized(e.message) if e.status == 401 else responses.forbidden(e.message)

    planners = db.fetch_all("usp_planner_listar")
    return responses.ok({"planners": planners})
