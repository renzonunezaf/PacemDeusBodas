"""
post_boda_cancelar.py
POST /v1/bodas/{id_boda}/cancelar
Header: Authorization: Bearer <token>

Logica de cancelacion en dos pasos:
  COUPLE -> solicita cancelacion (estado pasa a CANCELLATION_REQUESTED)
            -> notifica admins
  ADMIN  -> con body { "accion": "aprobar" } borra la boda completa
            -> notifica couple (antes de borrar para tener id_novios)
           con body { "accion": "rechazar" } vuelve a APPROVED
            -> notifica couple
"""

from shared import db
from shared import auth
from shared import responses
from shared import notifications


def handle_post_boda_cancelar(event, context):
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

    rol = payload["rol"]

    if rol != "ADMIN":
        # ─── Novio/a solicita cancelacion ─────────────────────────
        couple = db.fetch_one("usp_novios_obtener_por_usuario", (payload["id_usuario"],))
        if not couple or couple["id_novios"] != boda["id_novios"]:
            return responses.forbidden("No tienes acceso a esta boda")
        if boda["estado"] not in ("APPROVED", "CONTRACTED"):
            return responses.bad_request(
                "Solo puedes solicitar cancelacion de eventos aprobados o contratados"
            )

        db.execute("usp_boda_cambiar_estado", (id_boda, "CANCELLATION_REQUESTED", None))

        # Avisar al coro para que revise la solicitud.
        try:
            couple_label = notifications.couple_label(boda["id_novios"])
            notifications.notify_admins(
                tipo="CANCELLATION_REQUESTED",
                titulo="Solicitud de cancelacion",
                mensaje=f"{couple_label} solicita cancelar su evento. Revisa cuando puedas para confirmar o denegar.",
                id_boda=id_boda
            )
        except Exception:
            pass

        return responses.ok({
            "id_boda": id_boda,
            "estado": "CANCELLATION_REQUESTED",
            "mensaje": "Solicitud de cancelacion enviada. El coro la revisara.",
        })

    # ─── ADMIN procesa la solicitud ───────────────────────────────
    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    accion = body.get("accion")
    notas = body.get("notas")

    if accion == "aprobar":
        # Notificar a la pareja ANTES de borrar la boda — una vez
        # eliminada, notify_couple ya no encuentra el id_novios.
        # Tambien guardamos el id_usuario del couple para usar
        # notify_user directo en lugar de notify_couple (que requiere
        # que la boda exista).
        try:
            novios = db.fetch_one(
                "usp_novios_obtener_por_id", (boda["id_novios"],)
            ) if boda.get("id_novios") else None
            id_usuario_couple = novios.get("id_usuario") if novios else None
        except Exception:
            id_usuario_couple = None

        db.execute("usp_boda_eliminar", (id_boda,))

        try:
            if id_usuario_couple:
                notifications.notify_user(
                    id_usuario=id_usuario_couple,
                    tipo="CANCELLATION_APPROVED",
                    titulo="Tu cancelacion fue aprobada",
                    mensaje="El coro confirmo la cancelacion de tu evento. Lo lamentamos mucho y te agradecemos haber pensado en nosotros.",
                    id_boda=None  # ya no existe la boda en BD
                )
        except Exception:
            pass

        return responses.ok({
            "eliminado": True,
            "mensaje": "Evento cancelado y eliminado.",
        })

    if accion == "rechazar":
        db.execute(
            "usp_boda_cambiar_estado",
            (id_boda, "APPROVED", notas or "Solicitud de cancelacion rechazada")
        )

        try:
            notifications.notify_couple(
                id_boda=id_boda,
                tipo="CANCELLATION_REJECTED",
                titulo="Tu evento continua en pie",
                mensaje="El coro reviso tu solicitud y prefiere mantener el compromiso. Conversa con tu coordinador si necesitas ajustar algo."
            )
        except Exception:
            pass

        return responses.ok({
            "id_boda": id_boda,
            "estado": "APPROVED",
            "mensaje": "Solicitud rechazada. El evento continua.",
        })

    return responses.bad_request("accion debe ser 'aprobar' o 'rechazar'")
