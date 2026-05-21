"""
put_boda_instrumentos.py
PUT /v1/bodas/{id_boda}/instrumentos
Header: Authorization: Bearer <token>

Body: { "instrumentos": ["piano", "voz_femenina", "violin_1"] }

Reemplaza el set completo de instrumentos y recalcula precios.
"""

from datetime import datetime
from shared import db
from shared import auth
from shared import responses
from shared import pricing


REQUERIDOS = ["piano", "voz_femenina"]


def handle_put_boda_instrumentos(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    requested = body.get("instrumentos") or []
    final_slugs = list(set(REQUERIDOS + requested))

    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    rol = payload["rol"]
    if rol == "COUPLE":
        couple = db.fetch_one("usp_novios_obtener_por_usuario", (payload["id_usuario"],))
        if not couple or couple["id_novios"] != boda["id_novios"]:
            return responses.forbidden("No tienes acceso a esta boda")
        if boda["estado"] not in ("DRAFT", "SUBMITTED"):
            return responses.bad_request("El ensamble ya fue aprobado y no se puede modificar")
    elif rol != "ADMIN":
        return responses.forbidden("No tienes permiso")

    # Calcular nuevos precios
    departure_dt = datetime.combine(
        boda["fecha_boda"],
        datetime.strptime(boda["hora_boda"], "%H:%M").time()
    )
    breakdown = pricing.calculate_price_breakdown(
        boda["latitud"], boda["longitud"], final_slugs, departure_dt
    )

    # Reemplazar instrumentos
    db.execute("usp_boda_instrumentos_reemplazar", (id_boda, ",".join(final_slugs)))

    # Actualizar precios
    db.execute(
        "usp_boda_actualizar_precios",
        (id_boda,
         1 if breakdown["fuera_de_lima"] else 0,
         breakdown["precio_base"], breakdown["precio_instrumentos"],
         breakdown["precio_movilidad"], breakdown["precio_total"])
    )

    instrumentos = db.fetch_all("usp_boda_instrumentos_listar", (id_boda,))
    return responses.ok({
        "id_boda": id_boda,
        "instrumentos": instrumentos,
        "desglose_precio": breakdown,
    })
