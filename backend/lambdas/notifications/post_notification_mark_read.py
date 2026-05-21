"""
post_notification_mark_read.py
POST /v1/notifications/{id_notificacion}/leer
Header: Authorization: Bearer <token>

Marca una notificacion como leida. La app la llama cuando muestra la
alerta al usuario o cuando este navega al detalle correspondiente.
"""

from shared import db
from shared import auth
from shared import responses


def handle_post_notification_mark_read(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    id_notif = responses.parse_int_param(
        responses.get_path_param(event, "id_notificacion")
    )
    if not id_notif:
        return responses.bad_request("id_notificacion requerido")

    db.execute(
        "usp_notificacion_marcar_leida",
        (id_notif, payload["id_usuario"])
    )

    return responses.ok({"id_notificacion": id_notif, "marcada": True})
