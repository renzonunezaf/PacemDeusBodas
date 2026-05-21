"""
shared/auth.py
Autenticacion y autorizacion: hash de passwords y JWT.

JWT payload:
  { "id_usuario": int, "rol": "ADMIN|COUPLE|WEDDING_PLANNER", "iat": int, "exp": int }

Variables de entorno:
  JWT_SECRET     -> string aleatorio fuerte
  JWT_EXP_HOURS  -> default 168 (7 dias)
"""

import os
import time
import bcrypt
import jwt as pyjwt


def hash_password(plain_password):
    """Hashea con bcrypt cost 12. Devuelve string UTF-8."""
    if not plain_password:
        raise ValueError("Password no puede estar vacio")
    hashed = bcrypt.hashpw(plain_password.encode("utf-8"), bcrypt.gensalt(rounds=12))
    return hashed.decode("utf-8")


def verify_password(plain_password, hashed_password):
    """Devuelve True si el password coincide."""
    if not plain_password or not hashed_password:
        return False
    try:
        return bcrypt.checkpw(
            plain_password.encode("utf-8"),
            hashed_password.encode("utf-8")
        )
    except (ValueError, TypeError):
        return False


def _get_secret():
    secret = os.environ.get("JWT_SECRET")
    if not secret:
        raise RuntimeError("JWT_SECRET no configurado")
    return secret


def _get_exp_hours():
    return int(os.environ.get("JWT_EXP_HOURS", "168"))


def sign_token(id_usuario, rol):
    """Genera un JWT firmado."""
    now = int(time.time())
    payload = {
        "id_usuario": int(id_usuario),
        "rol": rol,
        "iat": now,
        "exp": now + _get_exp_hours() * 3600,
    }
    return pyjwt.encode(payload, _get_secret(), algorithm="HS256")


def verify_token(token):
    """Verifica un JWT. Lanza pyjwt.PyJWTError si invalido o expirado."""
    if not token:
        raise pyjwt.InvalidTokenError("Token vacio")
    return pyjwt.decode(token, _get_secret(), algorithms=["HS256"])


def extract_token_from_event(event):
    """Extrae el JWT del header Authorization. Soporta 'Bearer xxx' y directo."""
    headers = event.get("headers") or {}
    auth = headers.get("Authorization") or headers.get("authorization")
    if not auth:
        return None
    if auth.startswith("Bearer "):
        return auth[7:].strip()
    return auth.strip()


def authenticate(event, allowed_roles=None):
    """Verifica el JWT y devuelve el payload. Lanza AuthError si no autorizado."""
    token = extract_token_from_event(event)
    if not token:
        raise AuthError("Token requerido")
    try:
        payload = verify_token(token)
    except pyjwt.ExpiredSignatureError:
        raise AuthError("Token expirado")
    except pyjwt.PyJWTError as e:
        raise AuthError(f"Token invalido: {e}")

    if allowed_roles and payload.get("rol") not in allowed_roles:
        raise AuthError(
            f"Tu rol ({payload.get('rol')}) no tiene permiso para esta accion",
            status=403
        )
    return payload


class AuthError(Exception):
    """Error de autenticacion/autorizacion con status HTTP asociado."""

    def __init__(self, message, status=401):
        super().__init__(message)
        self.status = status
        self.message = message
