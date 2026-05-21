"""
get_notifications_poll.py
GET /v1/notifications/poll?since=<iso8601>&unread_only=<0|1>
Header: Authorization: Bearer <token>

Devuelve notificaciones del usuario autenticado mas recientes que `since`.
Si `since` no se pasa, devuelve todas las no leidas.

Diseñado para polling cada 10 segundos desde la app cliente. Las apps
guardan la fecha de la respuesta mas reciente y la mandan en el siguiente
poll para reducir trafico.
"""

from datetime import datetime
from shared import db
from shared import auth
from shared import responses


def handle_get_notifications_poll(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    id_usuario = payload["id_usuario"]
    params = event.get("queryStringParameters") or {}

    since_str = params.get("since")
    since_dt = None
    if since_str:
        try:
            # Acepta ISO 8601 con offset (ej: "2026-05-15T10:30:00+00:00")
            # o "Z" al final que Python pre-3.11 no entiende natively.
            normalized = since_str.replace("Z", "+00:00")
            since_dt = datetime.fromisoformat(normalized)
        except (ValueError, AttributeError):
            return responses.bad_request(
                "Parametro 'since' invalido. Esperado ISO 8601 con offset."
            )

    unread_only_raw = params.get("unread_only", "1")
    unread_only = 1 if unread_only_raw not in ("0", "false", "False") else 0

    notifs = db.fetch_all(
        "usp_notificacion_poll",
        (id_usuario, since_dt, unread_only)
    )

    items = [
        {
            "id_notificacion": n["id_notificacion"],
            "tipo": n["tipo"],
            "titulo": n["titulo"],
            "mensaje": n["mensaje"],
            "id_boda": n.get("id_boda"),
            "creado_en": _iso(n["creado_en"]),
            "leido_en": _iso(n.get("leido_en")),
        }
        for n in notifs
    ]

    return responses.ok({
        "total": len(items),
        "items": items,
        # Devuelve el server_time para que el cliente lo use como `since`
        # en el siguiente poll. Asi no nos preocupamos por desfaces de
        # reloj entre el dispositivo y el servidor.
        "server_time": datetime.now().astimezone().isoformat(),
    })


def _iso(value):
    if value is None:
        return None
    return value.isoformat()
