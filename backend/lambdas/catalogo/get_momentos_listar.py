"""
get_momentos_listar.py
GET /v1/momentos?fecha=YYYY-MM-DD
Catalogo publico. Si se pasa fecha, aplica las restricciones del tiempo
liturgico vigente para esa fecha.
"""

from datetime import datetime
from shared import db
from shared import responses
from shared import seasons


def handle_get_momentos_listar(event, context):
    fecha_str = responses.get_query_param(event, "fecha")

    if not fecha_str:
        # Sin fecha, devuelve catalogo plano sin restricciones
        moments = db.fetch_all("usp_momentos_listar")
        return responses.ok({"temporada": None, "momentos": moments})

    try:
        fecha_boda = datetime.strptime(fecha_str, "%Y-%m-%d").date()
    except ValueError:
        return responses.bad_request("fecha debe estar en formato YYYY-MM-DD")

    result = seasons.get_moments_with_status(fecha_boda)
    return responses.ok(result)
