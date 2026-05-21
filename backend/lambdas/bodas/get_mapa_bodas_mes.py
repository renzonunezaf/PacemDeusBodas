"""
get_mapa_bodas_mes.py
GET /v1/mapa/bodas?anio=YYYY&mes=MM
Solo ADMIN o WEDDING_PLANNER.

Devuelve las bodas de un mes especifico con coordenadas (para pintar
markers en el mapa) e incluye fecha, hora, local, pareja, movilidad
y estado.

Filtra solo bodas con coordenadas validas y estados >= SUBMITTED
(las DRAFT no aparecen porque la novia aun no las confirmo).
"""

from shared import db
from shared import auth
from shared import responses


def handle_get_mapa_bodas_mes(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    if payload["rol"] not in ("ADMIN", "WEDDING_PLANNER"):
        return responses.forbidden("Solo el coro o wedding planners pueden ver el mapa")

    qs = event.get("queryStringParameters") or {}
    try:
        anio = int(qs.get("anio", "0"))
        mes = int(qs.get("mes", "0"))
    except (TypeError, ValueError):
        return responses.bad_request("anio y mes deben ser numericos")

    if anio < 2025 or mes < 1 or mes > 12:
        return responses.bad_request("anio invalido o mes fuera de rango 1-12")

    bodas = db.fetch_all("usp_mapa_bodas_mes", (anio, mes))

    items = []
    for b in (bodas or []):
        # hora puede venir como timedelta
        hora_raw = b.get("hora_boda")
        hora_str = str(hora_raw)[:5] if hora_raw is not None else ""
        groom = (b.get("nombre_novio") or "").strip()
        bride = (b.get("nombre_novia") or "").strip()
        pareja = (f"{groom} y {bride}"
                  if groom and bride else (groom or bride or "—"))

        items.append({
            "id_boda": b.get("id_boda"),
            "fecha": b.get("fecha"),
            "hora": hora_str,
            "pareja": pareja,
            "nombre_local": b.get("nombre_local"),
            "direccion_local": b.get("direccion_local"),
            "latitud": float(b.get("latitud") or 0),
            "longitud": float(b.get("longitud") or 0),
            "precio_movilidad": float(b.get("precio_movilidad") or 0),
            "estado": b.get("estado"),
        })

    return responses.ok({
        "anio": anio,
        "mes": mes,
        "bodas": items
    })
