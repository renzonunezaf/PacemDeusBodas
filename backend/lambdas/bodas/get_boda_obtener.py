"""
get_boda_obtener.py
GET /v1/bodas/{id_boda}
Header: Authorization: Bearer <token>

Devuelve el detalle completo de una boda con instrumentos, setlist, contrato y pagos.
Verifica permisos segun rol.
"""

from shared import db
from shared import auth
from shared import responses


def handle_get_boda_obtener(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido en path")

    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    if not _tiene_acceso(payload, boda):
        return responses.forbidden("No tienes acceso a esta boda")

    instrumentos = db.fetch_all("usp_boda_instrumentos_listar", (id_boda,))
    setlist = db.fetch_all("usp_setlist_listar", (id_boda,))
    contrato = db.fetch_one("usp_contrato_obtener", (id_boda,))
    pagos = db.fetch_all("usp_pagos_listar", (id_boda,))

    return responses.ok({
        "boda": boda,
        "instrumentos": instrumentos,
        "setlist": setlist,
        "contrato": contrato,
        "pagos": pagos,
    })


def _tiene_acceso(payload, boda):
    rol = payload["rol"]
    id_usuario = payload["id_usuario"]

    if rol == "ADMIN":
        return True
    if rol == "COUPLE":
        couple = db.fetch_one("usp_novios_obtener_por_usuario", (id_usuario,))
        return couple and couple["id_novios"] == boda["id_novios"]
    if rol == "WEDDING_PLANNER":
        if not boda.get("id_planner"):
            return False
        planner = db.fetch_one("usp_planner_obtener_por_usuario", (id_usuario,))
        return planner and planner["id_planner"] == boda["id_planner"]
    return False
