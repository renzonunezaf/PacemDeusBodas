"""
delete_setlist_quitar.py
DELETE /v1/bodas/{id_boda}/setlist/{id_setlist}
Header: Authorization: Bearer <token>

Elimina un item del setlist. El SP reordena automaticamente los items
restantes en el mismo momento.
"""

from shared import db
from shared import auth
from shared import responses


def handle_delete_setlist_quitar(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    id_setlist = responses.parse_int_param(responses.get_path_param(event, "id_setlist"))

    if not id_boda or not id_setlist:
        return responses.bad_request("id_boda e id_setlist son requeridos")

    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    rol = payload["rol"]
    if rol == "COUPLE":
        couple = db.fetch_one("usp_novios_obtener_por_usuario", (payload["id_usuario"],))
        if not couple or couple["id_novios"] != boda["id_novios"]:
            return responses.forbidden("No tienes acceso a esta boda")
        if boda["estado"] not in ("DRAFT", "SUBMITTED"):
            return responses.bad_request("El ensamble ya fue aprobado y no se puede modificar")
    elif rol != "ADMIN":
        return responses.forbidden("No tienes permiso")

    # Verificar que el item pertenece a esta boda
    item = db.fetch_one("usp_setlist_obtener", (id_setlist,))
    if not item or item["id_boda"] != id_boda:
        return responses.not_found("Item de setlist no encontrado")

    result = db.execute_returning_id("usp_setlist_quitar", (id_setlist,))
    if not result or not result.get("eliminado"):
        return responses.not_found("Item de setlist no encontrado")

    return responses.ok({"eliminado": True, "id_setlist": id_setlist})
