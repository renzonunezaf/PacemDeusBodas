"""
post_setlist_agregar.py
POST /v1/bodas/{id_boda}/setlist
Header: Authorization: Bearer <token>

Body: { "idMomento": N, "idCancion": N }

Agrega un canto al setlist en un momento especifico.
El SP usp_setlist_agregar valida automaticamente:
  - duplicados (mismo canto, mismo momento)
  - limite de canciones por momento (max_canciones)

Antes del SP, validamos compatibilidad cancion-momento via usp_setlist_validar_compatibilidad.
"""

from shared import db
from shared import auth
from shared import responses


def handle_post_setlist_agregar(event, context):
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

    id_momento = responses.parse_int_param(body.get("idMomento"))
    id_cancion = responses.parse_int_param(body.get("idCancion"))

    if not id_momento or not id_cancion:
        return responses.bad_request("idMomento y idCancion son requeridos")

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

    # Validar compatibilidad cancion-momento
    compat = db.fetch_one("usp_setlist_validar_compatibilidad", (id_cancion, id_momento))
    if not compat or compat["compatible"] == 0:
        return responses.bad_request("Esta cancion no esta disponible para este momento")

    # Agregar via SP (valida duplicado y limite internamente)
    result = db.execute_returning_id(
        "usp_setlist_agregar",
        (id_boda, id_momento, id_cancion)
    )

    if result.get("error") == "duplicado":
        return responses.conflict("Esta cancion ya fue seleccionada para este momento")
    if result.get("error") == "limite_alcanzado":
        return responses.bad_request("Este momento alcanzo su limite maximo de canciones")

    # Devolver el item completo
    item = db.fetch_one("usp_setlist_obtener", (result["id_setlist"],))
    return responses.created(item)
