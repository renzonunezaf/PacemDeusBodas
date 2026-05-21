"""
get_instrumentos_listar.py
GET /v1/instrumentos
Catalogo publico, no requiere auth.
"""

from shared import db
from shared import responses


def handle_get_instrumentos_listar(event, context):
    instruments = db.fetch_all("usp_instrumentos_listar")
    return responses.ok({"instrumentos": instruments})
