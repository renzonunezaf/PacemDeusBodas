"""
post_boda_desenviar.py
POST /v1/bodas/{id_boda}/desenviar
Header: Authorization: Bearer <token> (rol: COUPLE)

"Deshacer envio" mientras el admin todavia no reviso. Solo funciona desde
SUBMITTED y solo lo usa la novia/novio. Una vez aprobado o rechazado, ya
no aplica (el flujo correcto es cancelacion o esperar respuesta).

Body: { } (no requiere)

Respuesta 200:
  { "id_boda": 12, "estado": "DRAFT", "mensaje": "..." }

Errores:
  - 400 si el estado != SUBMITTED
  - 403 si el usuario no es duena del evento

Side effect: notifica a los admins para que la boda desaparezca de su
lista de "Por aprobar" sin necesidad de refrescar manualmente.
"""

from shared import db
from shared import auth
from shared import responses
from shared import notifications


def handle_post_boda_desenviar(event, context):
    try:
        payload = auth.authenticate(event, allowed_roles=["COUPLE"])
    except auth.AuthError as e:
        if e.status == 401:
            return responses.unauthorized(e.message)
        return responses.forbidden(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    # Solo la duena del evento puede desenviar
    couple = db.fetch_one("usp_novios_obtener_por_usuario", (payload["id_usuario"],))
    if not couple or couple["id_novios"] != boda["id_novios"]:
        return responses.forbidden("No tienes acceso a esta boda")

    if boda["estado"] != "SUBMITTED":
        return responses.bad_request(
            "Solo puedes deshacer el envio si el evento esta en estado "
            "Enviado. Si ya fue aprobado o rechazado, debes seguir el flujo "
            "correspondiente."
        )

    # Volver a DRAFT y limpiar las notas (si las hubiera, no deberian).
    # Pasamos None como segundo parametro de cambiar_estado para que las
    # notas viejas no contaminen el regreso a borrador.
    db.execute("usp_boda_cambiar_estado", (id_boda, "DRAFT", None))

    # Avisar al coro para que la boda salga de su panel "Por aprobar".
    try:
        couple_label = notifications.couple_label(boda["id_novios"])
        notifications.notify_admins(
            tipo="BODA_UNSUBMITTED",
            titulo="Una pareja volvio a borrador",
            mensaje=f"{couple_label} retiro su evento de revision. Lo esta editando antes de volver a enviarlo.",
            id_boda=id_boda
        )
    except Exception:
        pass

    return responses.ok({
        "id_boda": id_boda,
        "estado": "DRAFT",
        "mensaje": "Envio deshecho. El evento volvio a Borrador y puedes "
                   "editarlo de nuevo.",
    })
