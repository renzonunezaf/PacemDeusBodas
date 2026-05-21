"""
get_bodas_listar.py
GET /v1/bodas?estado=...
Header: Authorization: Bearer <token>

Devuelve la lista de bodas visibles segun el rol:
  - ADMIN  -> todas
  - COUPLE -> solo sus propias bodas
  - WEDDING_PLANNER -> solo las asignadas a el
"""

from shared import db
from shared import auth
from shared import responses


VALID_STATUS = (
    "DRAFT", "SUBMITTED", "APPROVED", "CONTRACTED",
    "CANCELLATION_REQUESTED", "COMPLETED",
)


def handle_get_bodas_listar(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    rol = payload["rol"]
    id_usuario = payload["id_usuario"]
    estado = responses.get_query_param(event, "estado")

    if estado and estado not in VALID_STATUS:
        return responses.bad_request(f"estado invalido: {estado}")

    # Determina los filtros por rol
    id_novios = None
    id_planner = None

    if rol == "COUPLE":
        couple = db.fetch_one("usp_novios_obtener_por_usuario", (id_usuario,))
        if not couple:
            return responses.ok({"bodas": [], "total": 0})
        id_novios = couple["id_novios"]
    elif rol == "WEDDING_PLANNER":
        planner = db.fetch_one("usp_planner_obtener_por_usuario", (id_usuario,))
        if not planner:
            return responses.ok({"bodas": [], "total": 0})
        id_planner = planner["id_planner"]
    # ADMIN no filtra (NULL en ambos)

    bodas = db.fetch_all("usp_bodas_listar", (id_novios, id_planner, estado))

    return responses.ok({"total": len(bodas), "bodas": bodas})
