"""
put_auth_fcm_token.py
PUT /v1/auth/fcm-token
Header: Authorization: Bearer <token>

Body: { "fcm_token": "..." }

Guarda el FCM token del dispositivo en usuario.fcm_token. La app lo
llama (a) tras un login exitoso si ya tenia un token cacheado y
(b) cuando el SDK de Firebase notifica un onNewToken.

Es idempotente: si el token es el mismo que ya estaba guardado, no
hace nada visible.
"""

from shared import db
from shared import auth
from shared import responses


def handle_put_auth_fcm_token(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    fcm_token = body.get("fcm_token")
    if not fcm_token or not isinstance(fcm_token, str):
        return responses.bad_request("fcm_token requerido")

    fcm_token = fcm_token.strip()
    if not fcm_token:
        return responses.bad_request("fcm_token vacio")
    if len(fcm_token) > 512:
        return responses.bad_request("fcm_token excede 512 caracteres")

    db.execute(
        "usp_usuario_guardar_fcm_token",
        (payload["id_usuario"], fcm_token)
    )

    return responses.ok({
        "id_usuario": payload["id_usuario"],
        "mensaje": "FCM token registrado"
    })
