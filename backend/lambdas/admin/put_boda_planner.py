"""
put_boda_planner.py
PUT /v1/admin/bodas/{id_boda}/planner
Header: Authorization: Bearer <token> (rol: ADMIN)

Body: { "idPlanner": N }   o   { "idPlanner": null } para desasignar

Asigna o desasigna un wedding planner a una boda.
"""

from shared import db
from shared import auth
from shared import responses


def handle_put_boda_planner(event, context):
    try:
        auth.authenticate(event, allowed_roles=["ADMIN"])
    except auth.AuthError as e:
        return responses.unauthorized(e.message) if e.status == 401 else responses.forbidden(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    id_planner = responses.parse_int_param(body.get("idPlanner"))  # puede ser None

    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    if id_planner:
        # Verifica que el planner exista y este activo
        planners = db.fetch_all("usp_planner_listar")
        if not any(p["id_planner"] == id_planner for p in planners):
            return responses.not_found("Wedding planner no encontrado o inactivo")

    db.execute("usp_boda_asignar_planner", (id_boda, id_planner))

    return responses.ok({
        "id_boda": id_boda,
        "id_planner": id_planner,
        "mensaje": (
            "Planner asignado correctamente" if id_planner
            else "Asignacion de planner removida"
        ),
    })
