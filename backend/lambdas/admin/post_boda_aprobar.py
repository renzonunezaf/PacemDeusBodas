"""
post_boda_aprobar.py
POST /v1/admin/bodas/{id_boda}/aprobar
Header: Authorization: Bearer <token> (rol: ADMIN)

Body: { "accion": "aprobar" | "rechazar", "notas": "..." }

aprobar  -> estado pasa a APPROVED
rechazar -> estado vuelve a DRAFT con notas

En ambos casos, notifica a la pareja para que vea el resultado en su
dashboard y reciba push al device.
"""

from shared import db
from shared import auth
from shared import responses
from shared import notifications


def handle_post_boda_aprobar(event, context):
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

    accion = body.get("accion")
    notas = body.get("notas")

    if accion not in ("aprobar", "rechazar"):
        return responses.bad_request("accion debe ser 'aprobar' o 'rechazar'")

    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    if boda["estado"] not in ("DRAFT", "SUBMITTED"):
        return responses.bad_request(
            "Solo se pueden aprobar/rechazar eventos en DRAFT o SUBMITTED"
        )

    nuevo_estado = "APPROVED" if accion == "aprobar" else "DRAFT"
    db.execute("usp_boda_cambiar_estado", (id_boda, nuevo_estado, notas))

    # Notificar a la pareja del resultado. Best effort: si el push o
    # la insercion fallan, no rompemos el flujo del admin (el cambio
    # de estado ya quedo persistido y la novia vera el nuevo estado
    # cuando refresque la app o por polling).
    try:
        if accion == "aprobar":
            notifications.notify_couple(
                id_boda=id_boda,
                tipo="BODA_APPROVED",
                titulo="Tu evento fue aprobado",
                mensaje="El coro aprobo tu evento. Cuando estes lista, pueden firmar el contrato."
            )
        else:
            notifications.notify_couple(
                id_boda=id_boda,
                tipo="BODA_REJECTED",
                titulo="El coro devolvio tu evento",
                mensaje="El coro pide ajustes en tu evento. Revisa las observaciones y vuelvelo a enviar cuando estes lista."
            )
    except Exception:
        pass

    mensaje = (
        "Setlist aprobado. Se puede generar el contrato."
        if accion == "aprobar"
        else "Setlist devuelto a borrador con observaciones."
    )
    return responses.ok({
        "id_boda": id_boda,
        "estado": nuevo_estado,
        "mensaje": mensaje,
    })
