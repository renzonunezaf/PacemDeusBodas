"""
lambda_function.py
Lambda unica para todo el backend Pacem Deus Bodas.

Recibe el evento de API Gateway y enruta al handler correspondiente
segun (httpMethod, path). El path se compara con patrones que contienen
placeholders {id_boda}, {id_setlist}, etc.

Para que API Gateway funcione con un solo recurso catch-all {proxy+},
matcheamos el path real del request contra patrones de rutas definidas.

Nota: API Gateway REST API entrega el path SIN el stage (ej: '/bodas/15'),
no con '/v1/bodas/15' como podria sugerir la URL publica.
"""

import re

# Importar todos los handlers
from delete_boda_foto import handle_delete_boda_foto
from delete_setlist_quitar import handle_delete_setlist_quitar
from get_auth_me import handle_get_auth_me
from get_boda_contrato_pdf import handle_get_boda_contrato_pdf
from get_boda_setlist_pdf import handle_get_boda_setlist_pdf
from get_boda_fotos import handle_get_boda_fotos
from get_boda_obtener import handle_get_boda_obtener
from get_boda_precio import handle_get_boda_precio
from get_bodas_listar import handle_get_bodas_listar
from get_canciones_listar import handle_get_canciones_listar
from get_contrato_obtener import handle_get_contrato_obtener
from get_disponibilidad_mes import handle_get_disponibilidad_mes
from get_boda_anotacion_pendiente import handle_get_boda_anotacion_pendiente
from get_mapa_bodas_mes import handle_get_mapa_bodas_mes
from get_instrumentos_listar import handle_get_instrumentos_listar
from get_momentos_listar import handle_get_momentos_listar
from get_pagos_listar import handle_get_pagos_listar
from get_planner_bodas import handle_get_planner_bodas
from get_planners_listar import handle_get_planners_listar
from get_planners_publico import handle_get_planners_publico
from get_pricing_obtener import handle_get_pricing_obtener
from get_setlist_listar import handle_get_setlist_listar
from post_auth_login import handle_post_auth_login
from post_auth_registrar import handle_post_auth_registrar
from post_boda_aprobar import handle_post_boda_aprobar
from post_boda_cancelar import handle_post_boda_cancelar
from post_boda_crear import handle_post_boda_crear
from post_boda_desenviar import handle_post_boda_desenviar
from post_boda_enviar import handle_post_boda_enviar
from post_boda_foto import handle_post_boda_foto
from post_boda_foto_agregar import handle_post_boda_foto_agregar
from post_boda_planner_couple import handle_post_boda_planner_couple
from post_bodas_cotizar import handle_post_bodas_cotizar
from post_contrato_firmar import handle_post_contrato_firmar
from post_validar_conflicto import handle_post_validar_conflicto
from post_boda_devolver_anotaciones import handle_post_boda_devolver_anotaciones
from post_boda_anotacion_responder import handle_post_boda_anotacion_responder
from post_pago_crear import handle_post_pago_crear
from post_setlist_agregar import handle_post_setlist_agregar
from put_auth_fcm_token import handle_put_auth_fcm_token
from put_boda_editar import handle_put_boda_editar
from put_boda_foto_caption import handle_put_boda_foto_caption
from put_boda_instrumentos import handle_put_boda_instrumentos
from put_boda_planner import handle_put_boda_planner
from put_pricing_actualizar import handle_put_pricing_actualizar

# Notificaciones (polling, sin Firebase)
from get_notifications_poll import handle_get_notifications_poll
from post_notification_mark_read import handle_post_notification_mark_read

from shared import responses


# Tabla de ruteo: (httpMethod, route_pattern, handler).
# Los patrones usan {param} para captar segmentos del path.
ROUTES = [
    # Auth
    ("POST", "/auth/login",       handle_post_auth_login),
    ("POST", "/auth/registrar",   handle_post_auth_registrar),
    ("GET",  "/auth/me",          handle_get_auth_me),
    ("PUT",  "/auth/fcm-token",   handle_put_auth_fcm_token),

    # Catalogo
    ("GET",  "/instrumentos",     handle_get_instrumentos_listar),
    ("GET",  "/momentos",         handle_get_momentos_listar),
    ("GET",  "/canciones",        handle_get_canciones_listar),
    ("GET",  "/planners",         handle_get_planners_publico),

    # Bodas
    ("POST", "/bodas/cotizar",                           handle_post_bodas_cotizar),
    ("POST", "/bodas/validar-conflicto",                 handle_post_validar_conflicto),
    ("GET",  "/disponibilidad/{anio}/{mes}",             handle_get_disponibilidad_mes),
    ("GET",  "/mapa/bodas",                              handle_get_mapa_bodas_mes),
    ("GET",  "/bodas/{id_boda}/anotaciones/pendiente",   handle_get_boda_anotacion_pendiente),
    ("POST", "/bodas/{id_boda}/devolver-con-anotaciones",handle_post_boda_devolver_anotaciones),
    ("POST", "/bodas/{id_boda}/anotaciones/responder",   handle_post_boda_anotacion_responder),
    ("GET",  "/bodas",                                   handle_get_bodas_listar),
    ("POST", "/bodas",                                   handle_post_boda_crear),
    ("GET",  "/bodas/{id_boda}",                         handle_get_boda_obtener),
    ("PUT",  "/bodas/{id_boda}",                         handle_put_boda_editar),
    ("POST", "/bodas/{id_boda}/enviar",                  handle_post_boda_enviar),
    ("POST", "/bodas/{id_boda}/desenviar",               handle_post_boda_desenviar),
    ("POST", "/bodas/{id_boda}/cancelar",                handle_post_boda_cancelar),
    ("POST", "/bodas/{id_boda}/planner",                 handle_post_boda_planner_couple),
    # Legacy single-photo (mantenido por compatibilidad)
    ("POST", "/bodas/{id_boda}/foto",                    handle_post_boda_foto),
    # Multi-photo nuevo
    ("GET",  "/bodas/{id_boda}/fotos",                   handle_get_boda_fotos),
    ("POST", "/bodas/{id_boda}/fotos",                   handle_post_boda_foto_agregar),
    ("DELETE", "/bodas/{id_boda}/fotos/{id_foto}",       handle_delete_boda_foto),
    ("PUT", "/bodas/{id_boda}/fotos/{id_foto}/caption",  handle_put_boda_foto_caption),
    ("PUT",  "/bodas/{id_boda}/instrumentos",            handle_put_boda_instrumentos),
    ("GET",  "/bodas/{id_boda}/precio",                  handle_get_boda_precio),
    ("GET",  "/bodas/{id_boda}/setlist",                 handle_get_setlist_listar),
    ("POST", "/bodas/{id_boda}/setlist",                 handle_post_setlist_agregar),
    ("DELETE", "/bodas/{id_boda}/setlist/{id_setlist}",  handle_delete_setlist_quitar),
    ("GET",  "/bodas/{id_boda}/contrato",                handle_get_contrato_obtener),
    ("GET",  "/bodas/{id_boda}/setlist/pdf",            handle_get_boda_setlist_pdf),
    ("GET",  "/bodas/{id_boda}/contrato/pdf",            handle_get_boda_contrato_pdf),
    ("POST", "/bodas/{id_boda}/contrato/firmar",         handle_post_contrato_firmar),

    # Planner
    ("GET",  "/planner/bodas",    handle_get_planner_bodas),

    # Admin
    ("GET",  "/admin/pricing",                  handle_get_pricing_obtener),
    ("PUT",  "/admin/pricing",                  handle_put_pricing_actualizar),
    ("GET",  "/admin/planners",                 handle_get_planners_listar),
    ("PUT",  "/admin/bodas/{id_boda}/planner",  handle_put_boda_planner),
    ("POST", "/admin/bodas/{id_boda}/aprobar",  handle_post_boda_aprobar),
    ("GET",  "/admin/bodas/{id_boda}/pagos",    handle_get_pagos_listar),
    ("POST", "/admin/bodas/{id_boda}/pagos",    handle_post_pago_crear),

    # Notificaciones (polling, sin Firebase). Cualquier rol autenticado
    # consulta sus notificaciones aqui cada ~10s desde la app.
    ("GET",  "/notifications/poll",                            handle_get_notifications_poll),
    ("POST", "/notifications/{id_notificacion}/leer",          handle_post_notification_mark_read),
]


def _pattern_to_regex(pattern):
    """
    Convierte un patron como '/bodas/{id_boda}/setlist' a un regex que
    matchea '/bodas/15/setlist' y captura {id_boda: '15'}.
    """
    regex_pattern = re.sub(r"\{(\w+)\}", r"(?P<\1>[^/]+)", pattern)
    return re.compile("^" + regex_pattern + "$")


# Pre-compila los patrones una sola vez al cargar la Lambda (warm starts)
_COMPILED_ROUTES = [
    (method, _pattern_to_regex(pattern), handler)
    for method, pattern, handler in ROUTES
]


def _match_route(method, path):
    """Busca handler que matchee (method, path) y captura path params."""
    for route_method, regex, handler in _COMPILED_ROUTES:
        if route_method != method:
            continue
        match = regex.match(path)
        if match:
            return handler, match.groupdict()
    return None, None


def lambda_handler(event, context):
    """Entry point de la Lambda. Enruta al handler correcto."""

    method = (
        event.get("httpMethod")
        or event.get("requestContext", {}).get("http", {}).get("method", "")
    )

    # API Gateway REST API entrega el path SIN el stage (ej: '/bodas/15')
    path = (
        event.get("path")
        or event.get("rawPath")
        or event.get("requestContext", {}).get("path", "")
    )

    # CORS preflight: respondemos directamente
    if method == "OPTIONS":
        return responses.ok({})

    handler, path_params = _match_route(method, path)

    if handler is None:
        return responses.not_found(f"Ruta no encontrada: {method} {path}")

    # Inyectar los path params capturados en el event para que los handlers
    # puedan usar responses.get_path_param() sin cambios
    if path_params:
        existing = event.get("pathParameters") or {}
        event["pathParameters"] = {**existing, **path_params}

    try:
        return handler(event, context)
    except Exception as e:
        import traceback
        print(f"ERROR en {method} {path}: {e}")
        print(traceback.format_exc())
        return responses.server_error(f"Error procesando la solicitud: {str(e)}")
