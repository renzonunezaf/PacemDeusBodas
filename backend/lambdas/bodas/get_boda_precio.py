"""
get_boda_precio.py
GET /v1/bodas/{id_boda}/precio?latitud=X&longitud=Y&instrumentos=piano,voz_femenina

Devuelve el desglose de precio. Si se pasan los query params, hace un
calculo "what-if" con esos valores. Sin params, usa los datos actuales de la boda.
"""

from datetime import datetime
from shared import db
from shared import auth
from shared import responses
from shared import pricing


def handle_get_boda_precio(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    if not _tiene_acceso(payload, boda):
        return responses.forbidden("No tienes acceso a esta boda")

    # Override via query params (what-if)
    lat_str = responses.get_query_param(event, "latitud")
    lng_str = responses.get_query_param(event, "longitud")
    instr_csv = responses.get_query_param(event, "instrumentos")

    if lat_str is not None and lng_str is not None:
        try:
            lat = float(lat_str)
            lng = float(lng_str)
        except ValueError:
            return responses.bad_request("latitud / longitud deben ser numericos")
    else:
        lat = boda["latitud"]
        lng = boda["longitud"]

    if instr_csv:
        slugs = [s.strip() for s in instr_csv.split(",") if s.strip()]
    else:
        rows = db.fetch_all("usp_boda_instrumentos_listar", (id_boda,))
        slugs = [r["slug"] for r in rows]

    departure_dt = datetime.combine(
        boda["fecha_boda"],
        datetime.strptime(boda["hora_boda"], "%H:%M").time()
    )

    breakdown = pricing.calculate_price_breakdown(lat, lng, slugs, departure_dt)
    return responses.ok(breakdown)


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
