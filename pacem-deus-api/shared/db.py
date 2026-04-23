# ═══════════════════════════════════════════════════════════════
# Pacem Deus Bodas — Módulo compartido
# IS276 — Plataformas Móviles y Análisis Cloud — Grupo 2
# ═══════════════════════════════════════════════════════════════
# Conexión a RDS PostgreSQL, autenticación JWT y utilidades.
# Se despliega como Lambda Layer para ser usado por todas las funciones.
#
# Las credenciales se leen exclusivamente desde variables de entorno.
# En desarrollo local se cargan desde pacem-deus-api/.env (ver README).
# En producción (AWS Lambda) se inyectan como Environment Variables.
# ═══════════════════════════════════════════════════════════════

import os
import json
import psycopg
import psycopg.rows
import jwt
import bcrypt
from datetime import datetime, timedelta

# ─── CONFIGURACIÓN DE BASE DE DATOS ────────────────────────
# Los valores sensibles (password) NUNCA tienen default hardcodeado.
# Si falta DB_PASSWORD, la aplicación falla al primer get_connection()
# con un mensaje claro, en lugar de exponer credenciales en el código.

DB_CONFIG = {
    "host": os.environ.get("DB_HOST", "localhost"),
    "port": int(os.environ.get("DB_PORT", 5432)),
    "dbname": os.environ.get("DB_NAME", "pacem_deus_android"),
    "user": os.environ.get("DB_USER", "postgres"),
    "password": os.environ.get("DB_PASSWORD"),
}

JWT_SECRET = os.environ.get("JWT_SECRET", "pacem-deus-dev-secret-change-in-prod")
JWT_EXPIRATION_HOURS = 72


def get_connection():
    """Crea y retorna una conexión a la base de datos PostgreSQL.

    Valida que la contraseña haya sido inyectada vía variable de entorno.
    Si falta, levanta RuntimeError con instrucciones claras.
    """
    if not DB_CONFIG["password"]:
        raise RuntimeError(
            "DB_PASSWORD no configurada. Copie pacem-deus-api/.env.example "
            "como pacem-deus-api/.env y complete sus credenciales locales."
        )
    conn = psycopg.connect(**DB_CONFIG)
    conn.autocommit = False
    return conn


def query(sql, params=None, fetch_one=False):
    """
    Ejecuta una consulta SELECT y retorna los resultados como lista de diccionarios.
    Si fetch_one=True, retorna solo el primer registro o None.
    """
    conn = get_connection()
    try:
        with conn.cursor(row_factory=psycopg.rows.dict_row) as cur:
            cur.execute(sql, params)
            rows = cur.fetchall()
            # Convertir a dicts serializables (fechas a string)
            result = [_serialize_row(row) for row in rows]
            return result[0] if fetch_one and result else (None if fetch_one else result)
    finally:
        conn.close()


def execute(sql, params=None, returning=False):
    """
    Ejecuta una consulta INSERT/UPDATE/DELETE.
    Si returning=True, retorna el registro afectado como diccionario.
    """
    conn = get_connection()
    try:
        with conn.cursor(row_factory=psycopg.rows.dict_row) as cur:
            cur.execute(sql, params)
            conn.commit()
            if returning:
                row = cur.fetchone()
                return _serialize_row(row) if row else None
            return cur.rowcount
    finally:
        conn.close()


def _serialize_row(row):
    """Convierte un registro de la BD a dict serializable (mantiene snake_case para uso interno)."""
    if row is None:
        return None
    result = dict(row)
    for key, value in result.items():
        if isinstance(value, datetime):
            result[key] = value.isoformat()
        elif hasattr(value, 'isoformat'):
            result[key] = value.isoformat()
    return result


def _to_camel_case(data):
    """Convierte recursivamente las claves de un dict/lista de snake_case a camelCase."""
    if isinstance(data, list):
        return [_to_camel_case(item) for item in data]
    if isinstance(data, dict):
        result = {}
        for key, value in data.items():
            parts = key.split('_')
            camel_key = parts[0] + ''.join(p.capitalize() for p in parts[1:])
            result[camel_key] = _to_camel_case(value)
        return result
    return data


# ─── AUTENTICACIÓN JWT ──────────────────────────────────────

def hash_password(password):
    """Genera el hash bcrypt de una contraseña."""
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(password, hashed):
    """Verifica una contraseña contra su hash bcrypt."""
    return bcrypt.checkpw(password.encode("utf-8"), hashed.encode("utf-8"))


def generate_token(user_id, email, role):
    """Genera un token JWT con los datos del usuario."""
    payload = {
        "userId": user_id,
        "email": email,
        "role": role,
        "exp": datetime.utcnow() + timedelta(hours=JWT_EXPIRATION_HOURS),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm="HS256")


def decode_token(token):
    """Decodifica y valida un token JWT. Retorna el payload o None."""
    try:
        return jwt.decode(token, JWT_SECRET, algorithms=["HS256"])
    except (jwt.ExpiredSignatureError, jwt.InvalidTokenError):
        return None


def get_auth(event):
    """
    Extrae y valida la autenticación del evento de API Gateway.
    Retorna el payload del token o None si no es válido.
    """
    headers = event.get("headers", {}) or {}
    # Los headers pueden venir en minúscula o con capitalización
    auth_header = headers.get("Authorization") or headers.get("authorization", "")
    if not auth_header.startswith("Bearer "):
        return None
    token = auth_header[7:]
    return decode_token(token)


def require_auth(event):
    """
    Valida la autenticación. Retorna el payload del token.
    Si falla, levanta una excepción con el mensaje de error.
    """
    auth = get_auth(event)
    if not auth:
        raise PermissionError("No autorizado")
    return auth


def require_admin(event):
    """Valida que el usuario sea ADMIN. Retorna el payload del token."""
    auth = require_auth(event)
    if auth["role"] != "ADMIN":
        raise PermissionError("Acceso denegado: se requiere rol ADMIN")
    return auth


# ─── RESPUESTAS HTTP ────────────────────────────────────────

# Headers CORS para todas las respuestas
CORS_HEADERS = {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type,Authorization",
    "Access-Control-Allow-Methods": "GET,POST,PUT,DELETE,OPTIONS",
}


def response(status_code, body):
    """Construye la respuesta HTTP estándar para API Gateway."""
    return {
        "statusCode": status_code,
        "headers": CORS_HEADERS,
        "body": json.dumps(body, ensure_ascii=False, default=str),
    }


def success(data, status_code=200):
    """Respuesta exitosa. Convierte automáticamente claves a camelCase."""
    return response(status_code, _to_camel_case(data))


def error(message, status_code=400):
    """Respuesta de error."""
    return response(status_code, {"error": message})


def get_body(event):
    """Extrae y parsea el body JSON del evento de API Gateway."""
    body = event.get("body", "{}")
    if isinstance(body, str):
        return json.loads(body) if body else {}
    return body or {}


def get_path_param(event, name):
    """Extrae un parámetro de ruta (ej: /weddings/{id})."""
    params = event.get("pathParameters") or {}
    return params.get(name)


def get_query_param(event, name, default=None):
    """Extrae un parámetro de query string (ej: ?momentId=xxx)."""
    params = event.get("queryStringParameters") or {}
    return params.get(name, default)
