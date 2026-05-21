"""
post_boda_devolver_anotaciones.py
POST /v1/bodas/{id_boda}/devolver-con-anotaciones
Solo ADMIN.

Body: {
  "texto_nota": "Cambiamos el venue por cercania, ajustamos setlist...",
  "campos_modificados": ["venue","setlist","instrumentos"]
}

Flujo:
1. Captura snapshot del estado actual de la boda (venue, instrumentos,
   setlist, precios).
2. Inserta una fila en boda_anotacion con el snapshot y la nota.
3. Cambia el estado de la boda a RETURNED_WITH_NOTES.
4. La novia vera la anotacion pendiente y podra aceptar/rechazar.

Asume que el admin YA hizo los cambios antes de devolver (usando los
endpoints existentes de editar venue/setlist/instrumentos). El snapshot
guarda lo que habia ANTES de esos cambios para poder revertir si la
novia rechaza.

NOTA IMPORTANTE: el admin debe guardar el snapshot ANTES de empezar a
editar. El frontend debe llamar a /bodas/{id}/snapshot al entrar al modo
edicion, y al final llamar a este endpoint. Pero como simplificacion
inicial, este endpoint toma el snapshot del estado actual y asume que
los precios "anterior" se pasan en el body si la novia los necesita.
"""

import json
from shared import db
from shared import auth
from shared import responses
from shared import notifications


def handle_post_boda_devolver_anotaciones(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    if payload["rol"] != "ADMIN":
        return responses.forbidden("Solo el coro puede devolver bodas con anotaciones")

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    texto_nota = (body.get("texto_nota") or "").strip()
    if not texto_nota:
        return responses.bad_request("texto_nota es requerido")

    campos = body.get("campos_modificados") or []
    if isinstance(campos, list):
        campos_str = ",".join(campos)
    else:
        campos_str = str(campos)

    # Snapshot del estado actual ANTES de los cambios del admin.
    # En el flujo correcto, el admin llamo previamente a
    # /bodas/{id}/preparar-anotacion para guardar el snapshot, y
    # despues edito. Aqui asumimos que el body trae los datos del
    # snapshot generado en ese paso anterior.
    snapshot_antes_raw = body.get("snapshot_antes")
    if snapshot_antes_raw:
        snapshot_json = (json.dumps(snapshot_antes_raw)
                        if isinstance(snapshot_antes_raw, dict)
                        else str(snapshot_antes_raw))
    else:
        # Fallback: tomar el estado actual (no es ideal, pero permite
        # funcionar sin el paso de preparar-anotacion).
        snapshot_json = json.dumps(_snapshot_actual(id_boda))

    precio_anterior = float(body.get("precio_anterior") or 0)

    # Boda actual (ya con los cambios aplicados)
    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Boda no encontrada")
    precio_nuevo = float(boda.get("precio_total") or 0)

    # Datos del autor
    autor = db.fetch_one("usp_usuario_obtener", (payload["id_usuario"],))
    nombre_autor = (autor.get("nombre") if autor else None) or "Coro Pacem Deus"

    try:
        result = db.fetch_one("usp_anotacion_crear", (
            id_boda, payload["id_usuario"], nombre_autor,
            texto_nota, snapshot_json, campos_str,
            precio_anterior, precio_nuevo
        ))
    except Exception as e:
        return responses.server_error(f"Error al guardar anotacion: {e}")

    db.execute("usp_boda_cambiar_estado", (id_boda, "RETURNED_WITH_NOTES", None))

    # Notificar a la novia con tono elegante. Best effort: si el push o la
    # insercion en `notificacion` fallan, el flujo principal sigue intacto
    # (la anotacion ya quedo persistida y la pareja la vera al refrescar).
    try:
        notifications.notify_couple(
            id_boda=id_boda,
            tipo="BODA_RETURNED_WITH_NOTES",
            titulo="Una nota del coro para tu boda",
            mensaje=(
                "El Coro Pacem Deus ha dejado anotaciones sobre los detalles "
                "de tu ceremonia para conversarlas contigo. Entra cuando "
                "puedas a revisarlas con calma."
            )
        )
    except Exception as e:
        print(f"devolver_anotaciones: notify_couple fallo (continuando): {e}")

    return responses.ok({
        "id_boda_anotacion": result.get("id_boda_anotacion"),
        "id_boda": id_boda,
        "estado_boda": "RETURNED_WITH_NOTES",
        "mensaje": "Boda devuelta con anotaciones. La pareja recibira la notificacion."
    })


def _snapshot_actual(id_boda):
    """Fallback: arma un snapshot del estado actual de la boda."""
    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return {}
    instrumentos = db.fetch_all("usp_boda_instrumentos_listar", (id_boda,))
    setlist = db.fetch_all("usp_setlist_listar", (id_boda,))
    return {
        "venue_name": boda.get("nombre_local"),
        "venue_address": boda.get("direccion_local"),
        "venue_lat": boda.get("latitud"),
        "venue_lng": boda.get("longitud"),
        "instrumentos": [i.get("slug") for i in (instrumentos or [])],
        "setlist": [{"id_momento": s.get("id_momento"),
                     "id_cancion": s.get("id_cancion"),
                     "orden": s.get("orden")}
                    for s in (setlist or [])],
        "precio_base": float(boda.get("precio_base") or 0),
        "precio_instrumentos": float(boda.get("precio_instrumentos") or 0),
        "precio_movilidad": float(boda.get("precio_movilidad") or 0),
        "precio_total": float(boda.get("precio_total") or 0),
        "fuera_de_lima": bool(boda.get("fuera_de_lima") or False),
    }
