"""
post_boda_enviar.py
POST /v1/bodas/{id_boda}/enviar
Header: Authorization: Bearer <token>

Cambia el estado de DRAFT a SUBMITTED para revision por el coro.

v07: ademas de cambiar el estado, crea una notificacion para cada admin
activo para que reciba la alerta en su app via polling.
"""

from shared import db
from shared import auth
from shared import responses
from shared import notifications


def handle_post_boda_enviar(event, context):
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
    if rol == "COUPLE":
        couple = db.fetch_one("usp_novios_obtener_por_usuario", (payload["id_usuario"],))
        if not couple or couple["id_novios"] != boda["id_novios"]:
            return responses.forbidden("No tienes acceso a esta boda")
        if boda["estado"] not in ("DRAFT", "SUBMITTED"):
            return responses.bad_request("Solo se puede enviar desde DRAFT o SUBMITTED")
    elif rol != "ADMIN":
        return responses.forbidden("Solo novios o admin pueden enviar")

    db.execute("usp_boda_cambiar_estado", (id_boda, "SUBMITTED", None))

    # Notificar a todos los admins. Si la insercion falla no rompemos
    # el flujo: el cambio de estado ya quedo persistido y el admin se
    # enterara en el siguiente refresh manual.
    try:
        couple_label = _couple_label(boda["id_novios"])
        fecha = boda.get("fecha_boda")
        fecha_str = fecha.strftime("%d/%m/%Y") if fecha else "sin fecha"
        notifications.notify_admins(
            tipo="BODA_SUBMITTED",
            titulo="Nueva boda enviada para revision",
            mensaje=f"{couple_label} envio su ensamble del {fecha_str}.",
            id_boda=id_boda
        )
    except Exception:
        pass

    return responses.ok({
        "id_boda": id_boda,
        "estado": "SUBMITTED",
        "mensaje": "Tu ensamble ha sido enviado. El coro lo revisara pronto.",
    })


def _couple_label(id_novios):
    """Devuelve 'Novio y Novia' o 'Una pareja' si falta info."""
    n = db.fetch_one("usp_novios_obtener_por_id", (id_novios,))
    if not n:
        return "Una pareja"
    novio = (n.get("nombre_novio") or "").strip()
    novia = (n.get("nombre_novia") or "").strip()
    if novio and novia:
        return f"{novio} y {novia}"
    return novio or novia or "Una pareja"
