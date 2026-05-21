"""
get_pricing_obtener.py
GET /v1/admin/pricing
Header: Authorization: Bearer <token> (rol: ADMIN)

Devuelve la configuracion actual de precios (single-row).
"""

from shared import db
from shared import auth
from shared import responses


def handle_get_pricing_obtener(event, context):
    try:
        auth.authenticate(event, allowed_roles=["ADMIN"])
    except auth.AuthError as e:
        return responses.unauthorized(e.message) if e.status == 401 else responses.forbidden(e.message)

    config = db.fetch_one("usp_pricing_obtener")
    if not config:
        return responses.not_found("configuracion_precios no inicializada")

    return responses.ok(config)
