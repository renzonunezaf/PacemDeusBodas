"""
shared/push.py
Envia push notifications via Firebase Cloud Messaging API V1.

Autenticacion: genera un access token OAuth2 a partir del service
account JSON, usando JWT RS256. Cachea el token en memoria entre warm
starts del Lambda (TTL de Google es 1h).

Variables de entorno:
  FCM_PROJECT_ID                  -> project_id del proyecto Firebase
  FCM_SERVICE_ACCOUNT_PATH        -> ruta al JSON dentro del layer
                                     (default: /opt/python/shared/firebase-service-account.json)
"""

import json
import os
import time
import urllib.request
import urllib.error

import jwt  # PyJWT con cryptography para RS256

from shared import db


# Cache del access token y su expiracion (epoch seconds)
_token_cache = {"token": None, "expires_at": 0}

# URL base de la API V1 de FCM
_FCM_BASE = "https://fcm.googleapis.com/v1/projects/{project}/messages:send"
_OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token"
_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"


def _load_service_account():
    """Carga y parsea el JSON del service account desde el layer."""
    path = os.environ.get(
        "FCM_SERVICE_ACCOUNT_PATH",
        "/opt/python/shared/firebase-service-account.json"
    )
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def _get_access_token():
    """
    Obtiene un OAuth2 access token de Google para autenticarse con la API
    de FCM. Cachea en memoria: el token vive 1h, lo refrescamos 5min antes.
    """
    now = int(time.time())
    if _token_cache["token"] and _token_cache["expires_at"] > now + 60:
        return _token_cache["token"]

    sa = _load_service_account()
    client_email = sa["client_email"]
    private_key = sa["private_key"]

    # JWT firmado con la private key del service account.
    # Google intercambia esto por un access token Bearer.
    iat = now
    exp = iat + 3600  # 1 hora
    claims = {
        "iss": client_email,
        "scope": _SCOPE,
        "aud": _OAUTH_TOKEN_URL,
        "iat": iat,
        "exp": exp,
    }
    assertion = jwt.encode(claims, private_key, algorithm="RS256")

    # POST al endpoint OAuth2 de Google con el JWT como assertion.
    data = (
        "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer"
        f"&assertion={assertion}"
    ).encode("utf-8")
    req = urllib.request.Request(
        _OAUTH_TOKEN_URL,
        data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Fallo al obtener OAuth token ({e.code}): {err_body}")

    access_token = body["access_token"]
    _token_cache["token"] = access_token
    _token_cache["expires_at"] = iat + int(body.get("expires_in", 3600))
    return access_token


def send_to_token(fcm_token, title, body, data=None):
    """
    Envia un push a un FCM token especifico. `data` opcional es un dict
    de strings que viaja como payload custom (la app lo recibe en
    RemoteMessage.data y puede usarlo para deep links).

    Devuelve True si el envio fue exitoso, False si Firebase rechazo el
    token (token invalido, app desinstalada, etc). El caller decide si
    limpiar el token de BD.
    """
    if not fcm_token:
        return False

    sa = _load_service_account()
    project_id = sa.get("project_id") or os.environ.get("FCM_PROJECT_ID")
    if not project_id:
        raise RuntimeError("FCM project_id no configurado")

    access_token = _get_access_token()
    url = _FCM_BASE.format(project=project_id)

    message = {
        "message": {
            "token": fcm_token,
            "notification": {
                "title": title,
                "body": body,
            },
            # Android-specific config: priority HIGH bypassa Doze mode.
            #
            # Sin esto, FCM API V1 manda con priority "normal" por default,
            # y los push a devices en Doze (pantalla apagada, idle) quedan
            # bufferados hasta la proxima "maintenance window" — que puede
            # tardar 5-15 minutos en Doze ligero, mucho mas en profundo.
            #
            # Las notifs de cambio de estado de boda son acciones que el
            # admin/pareja esperan ver en cuanto ocurren, asi que HIGH es
            # apropiado. No es spam: el backend solo manda push cuando
            # realmente hay novedad accionable.
            #
            # notification_priority HIGH (= PRIORITY_HIGH del NotificationCompat)
            # asegura ademas que la notif se muestre con heads-up display
            # en lugar de quedar silenciosa en la bandeja.
            "android": {
                "priority": "HIGH",
                "notification": {
                    "notification_priority": "PRIORITY_HIGH",
                    "default_sound": True,
                    "default_vibrate_timings": True,
                },
            },
        }
    }
    if data:
        # FCM exige que data sea Dict[str, str]
        message["message"]["data"] = {k: str(v) for k, v in data.items()}

    req = urllib.request.Request(
        url,
        data=json.dumps(message).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json; UTF-8",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            resp.read()
            return True
    except urllib.error.HTTPError as e:
        # 404 / UNREGISTERED = token invalido (app desinstalada). 400
        # con INVALID_ARGUMENT a veces indica token malformado.
        # Otros codigos: error real, lo logueamos pero no rompemos el
        # flujo del handler que disparo el push.
        try:
            err_body = e.read().decode("utf-8", errors="replace")
        except Exception:
            err_body = "<no body>"
        print(f"FCM error {e.code}: {err_body}")
        return False
    except Exception as e:
        print(f"FCM exception: {e}")
        return False


def send_to_user(id_usuario, title, body, data=None):
    """
    Envia push al token del usuario indicado. Si el usuario no tiene
    token (no instalo la app, no la abrio aun), no hace nada.
    """
    row = db.fetch_one("usp_usuario_obtener_fcm_token", (id_usuario,))
    if not row or not row.get("fcm_token"):
        return False
    return send_to_token(row["fcm_token"], title, body, data)


def send_to_admins(title, body, data=None):
    """
    Envia push a todos los admins activos con FCM token. Lo usa el
    helper notifications.notify_admins despues de insertar la fila en
    la tabla `notificacion`.
    """
    admins = db.fetch_all("usp_admin_listar_fcm_tokens", ())
    sent = 0
    for admin in admins:
        token = admin.get("fcm_token")
        if token and send_to_token(token, title, body, data):
            sent += 1
    return sent
