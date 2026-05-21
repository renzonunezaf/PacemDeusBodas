"""
shared/responses.py
Helpers para construir respuestas estandar para API Gateway (REST API).

Todas las respuestas incluyen los headers de CORS necesarios para que
el cliente Android (Volley/Retrofit) consuma sin problema.
"""

import json
from decimal import Decimal
from datetime import datetime, date


CORS_HEADERS = {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type,Authorization,X-Amz-Date,X-Api-Key,X-Amz-Security-Token",
    "Access-Control-Allow-Methods": "GET,POST,PUT,DELETE,OPTIONS",
}


class _JsonEncoder(json.JSONEncoder):
    """Serializa tipos comunes de SQL Server (Decimal, datetime, date, bytes)."""

    def default(self, obj):
        if isinstance(obj, Decimal):
            return float(obj)
        if isinstance(obj, (datetime, date)):
            return obj.isoformat()
        if isinstance(obj, bytes):
            return obj.decode("utf-8", errors="ignore")
        return super().default(obj)


def _response(status_code, body):
    """Construye la respuesta en el formato que espera API Gateway."""
    return {
        "statusCode": status_code,
        "headers": CORS_HEADERS,
        "body": json.dumps(body, cls=_JsonEncoder, ensure_ascii=False),
    }


def ok(data=None, status_code=200):
    return _response(status_code, data if data is not None else {})


def created(data=None):
    return _response(201, data if data is not None else {})


def bad_request(message, details=None):
    body = {"error": message}
    if details:
        body["details"] = details
    return _response(400, body)


def unauthorized(message="No autorizado"):
    return _response(401, {"error": message})


def forbidden(message="No tienes permiso para esta accion"):
    return _response(403, {"error": message})


def not_found(message="Recurso no encontrado"):
    return _response(404, {"error": message})


def conflict(message):
    return _response(409, {"error": message})


def server_error(message="Error interno del servidor"):
    return _response(500, {"error": message})


def parse_body(event):
    """Extrae el body de un evento de API Gateway como dict."""
    body = event.get("body")
    if not body:
        return {}
    if isinstance(body, dict):
        return body
    try:
        return json.loads(body)
    except json.JSONDecodeError as e:
        raise ValueError(f"Body no es JSON valido: {e}")


def get_path_param(event, name):
    """Extrae un parametro de path (ej. /bodas/{id_boda} -> get_path_param(event, 'id_boda'))."""
    params = event.get("pathParameters") or {}
    return params.get(name)


def get_query_param(event, name, default=None):
    """Extrae un query string parameter."""
    params = event.get("queryStringParameters") or {}
    return params.get(name, default)


def parse_int_param(value, default=None):
    """Parsea un parametro entero. Devuelve default si es None o invalido."""
    if value is None or value == "":
        return default
    try:
        return int(value)
    except (TypeError, ValueError):
        return default
