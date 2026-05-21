"""
post_boda_planner_couple.py
POST /v1/bodas/{id_boda}/planner
Header: Authorization: Bearer <token> (rol: COUPLE)

La novia asigna un wedding planner a su boda. Solo se permite en estado
DRAFT (no se cambia el planner una vez enviado al coro).

Body: { "idPlanner": "2" }    // o null para desasignar

Respuesta 200:
  { "id_boda": 12, "id_planner": "2", "mensaje": "..." }
"""

from shared import db
from shared import auth
from shared import responses


def handle_post_boda_planner_couple(event, context):
    try:
        payload = auth.authenticate(event, allowed_roles=["COUPLE"])
    except auth.AuthError as e:
        if e.status == 401:
            return responses.unauthorized(e.message)
        return responses.forbidden(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    id_planner_raw = body.get("idPlanner")
    if id_planner_raw is None:
        return responses.bad_request("idPlanner es requerido")

    try:
        id_planner = int(id_planner_raw)
    except (TypeError, ValueError):
        return responses.bad_request("idPlanner debe ser numerico")

    # Validar ownership y estado
    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    couple = db.fetch_one("usp_novios_obtener_por_usuario", (payload["id_usuario"],))
    if not couple or couple["id_novios"] != boda["id_novios"]:
        return responses.forbidden("No tienes acceso a esta boda")

    if boda["estado"] != "DRAFT":
        return responses.bad_request(
            "Solo puedes cambiar el wedding planner mientras tu evento esta "
            "en estado Borrador."
        )

    try:
        db.execute("usp_boda_asignar_planner_couple", (id_boda, id_planner))
    except Exception as e:
        return responses.server_error(f"Error al asignar planner: {e}")

    return responses.ok({
        "id_boda": id_boda,
        "id_planner": str(id_planner),
        "mensaje": "Wedding planner asignado al evento",
    })
