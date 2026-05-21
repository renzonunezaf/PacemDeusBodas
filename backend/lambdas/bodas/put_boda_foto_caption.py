"""
put_boda_foto_caption.py
PUT /v1/bodas/{id_boda}/fotos/{id_foto}/caption
Header: Authorization: Bearer <token>

Body: { "caption": "Nuevo comentario" }   // o null/"" para limpiar

Solo el autor original de la foto puede editar su caption. Admin con
permisos de admin no puede editar captions ajenos (seria edicion de
opiniones de otros usuarios). Si Renzo necesita esto despues, se cambia.

Respuesta 200:
  { "id_foto": 7, "caption": "...", "mensaje": "..." }
Errores:
  - 403 si no eres el autor de la foto
  - 404 si la foto no existe
"""

from shared import db
from shared import auth
from shared import responses


MAX_CAPTION_LEN = 500


def handle_put_boda_foto_caption(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    id_foto = responses.parse_int_param(responses.get_path_param(event, "id_foto"))
    if not id_boda or not id_foto:
        return responses.bad_request("id_boda e id_foto son requeridos")

    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    caption_raw = body.get("caption")

    # Normalizar: None / vacio / blanco -> NULL en BD
    if caption_raw is None:
        caption = None
    else:
        caption = str(caption_raw).strip()
        if not caption:
            caption = None
        elif len(caption) > MAX_CAPTION_LEN:
            return responses.bad_request(
                f"El comentario no puede superar {MAX_CAPTION_LEN} caracteres"
            )

    # Verificar que la foto pertenezca a la boda y traer el autor original
    fotos = db.fetch_all("usp_boda_foto_listar", (id_boda,))
    foto = next((f for f in fotos if f["id_foto"] == id_foto), None)
    if not foto:
        return responses.not_found("Foto no encontrada para este evento")

    # Solo el autor puede editar su propio caption
    autor_original = foto.get("creado_por_id_usuario")
    if autor_original is None:
        return responses.forbidden(
            "Esta foto no tiene autor registrado, no se puede editar el comentario."
        )
    if int(autor_original) != int(payload["id_usuario"]):
        return responses.forbidden(
            "Solo el autor original del comentario puede editarlo."
        )

    # Actualizar
    result = db.fetch_one("usp_boda_foto_editar_caption", (id_boda, id_foto, caption))
    filas = result["filas_afectadas"] if result else 0
    if filas == 0:
        return responses.not_found("No se pudo actualizar la foto")

    return responses.ok({
        "id_foto": id_foto,
        "caption": caption,
        "mensaje": "Comentario actualizado",
    })
