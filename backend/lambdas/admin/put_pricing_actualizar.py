"""
put_pricing_actualizar.py
PUT /v1/admin/pricing
Header: Authorization: Bearer <token> (rol: ADMIN)

Body: cualquier subconjunto de los 20 parametros configurables.
Se completan los faltantes con los valores actuales (no se requiere mandar todo).

Cero hardcoded: cambiar precios = editar aqui, sin tocar codigo.
"""

from shared import db
from shared import auth
from shared import responses


# Campos editables y sus tipos para validacion
CAMPOS = {
    "precio_base_lima": float,
    "precio_base_fuera": float,
    "precio_instrumento_lima": float,
    "precio_instrumento_fuera": float,
    "movilidad_minima": float,
    "movilidad_maxima": float,
    "latitud_base": float,
    "longitud_base": float,
    "radio_lima_km": float,
    "movilidad_km_libres": float,
    "movilidad_minutos_libres": float,
    "movilidad_tarifa_km": float,
    "movilidad_tarifa_minuto": float,
    "movilidad_grupo_grande": float,
    "movilidad_umbral_grupo": int,
    "movilidad_centro_historico": float,
    "centro_norte_lat": float,
    "centro_sur_lat": float,
    "centro_oeste_lng": float,
    "centro_este_lng": float,
}


def handle_put_pricing_actualizar(event, context):
    try:
        auth.authenticate(event, allowed_roles=["ADMIN"])
    except auth.AuthError as e:
        return responses.unauthorized(e.message) if e.status == 401 else responses.forbidden(e.message)

    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    if not body:
        return responses.bad_request("Body vacio")

    # Cargar config actual
    actual = db.fetch_one("usp_pricing_obtener")
    if not actual:
        return responses.not_found("configuracion_precios no inicializada")

    # Construir parametros: usar valor del body si esta presente, sino el actual
    params = []
    for campo, tipo in CAMPOS.items():
        valor = body.get(campo, actual[campo])
        try:
            params.append(tipo(valor))
        except (TypeError, ValueError):
            return responses.bad_request(f"Campo {campo} debe ser de tipo {tipo.__name__}")

    db.execute("usp_pricing_actualizar", tuple(params))

    config = db.fetch_one("usp_pricing_obtener")
    return responses.ok(config)
