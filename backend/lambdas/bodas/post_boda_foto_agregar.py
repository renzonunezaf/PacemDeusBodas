"""
post_boda_foto_agregar.py
POST /v1/bodas/{id_boda}/fotos
Header: Authorization: Bearer <token>

Body: {
  "imagenBase64": "...",
  "tipoContenido": "image/jpeg",
  "caption": "Detalle de la entrada principal"   # OPCIONAL
}

Sube la foto a S3 y agrega una fila a boda_foto. Limite: 5 fotos por evento.
Registra al usuario autenticado como autor (creado_por_id_usuario) y el
caption opcional.

Variables de entorno requeridas:
  S3_BUCKET  -> nombre del bucket (pacem-deus-fotos)
  AWS_REGION -> region (us-east-1) -- viene poblada por el runtime
"""

import os
import base64
import uuid
import boto3
from shared import db
from shared import auth
from shared import responses


s3 = boto3.client("s3")

MAX_FOTOS_POR_BODA = 5
MAX_CAPTION_LEN = 500

TIPOS_VALIDOS = {
    "image/jpeg": ".jpg",
    "image/jpg":  ".jpg",
    "image/png":  ".png",
    "image/webp": ".webp",
}


def handle_post_boda_foto_agregar(event, context):
    # ─── Autenticacion ──────────────────────────────────────
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    id_usuario_autor = payload["id_usuario"]

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    # ─── Body ───────────────────────────────────────────────
    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    imagen_b64 = body.get("imagenBase64")
    tipo = (body.get("tipoContenido") or "image/jpeg").lower()
    caption = body.get("caption")

    if not imagen_b64:
        return responses.bad_request("imagenBase64 es requerido")
    if tipo not in TIPOS_VALIDOS:
        return responses.bad_request(
            f"tipoContenido invalido. Soportados: {', '.join(TIPOS_VALIDOS.keys())}"
        )

    # Sanitizar caption (no permitir vacio ni que pase del limite)
    if caption is not None:
        caption = str(caption).strip()
        if not caption:
            caption = None
        elif len(caption) > MAX_CAPTION_LEN:
            return responses.bad_request(
                f"El comentario no puede superar {MAX_CAPTION_LEN} caracteres"
            )

    # ─── Permisos: novia, planner asignado o admin ─────────
    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")
    if not _tiene_acceso(payload, boda):
        return responses.forbidden("No tienes acceso a esta boda")

    # ─── Validar limite de 5 fotos ─────────────────────────
    count_row = db.fetch_one("usp_boda_foto_contar", (id_boda,))
    total_actual = count_row["total"] if count_row else 0
    if total_actual >= MAX_FOTOS_POR_BODA:
        return responses.bad_request(
            f"Maximo {MAX_FOTOS_POR_BODA} fotos por evento. "
            "Elimina una antes de agregar otra."
        )

    # ─── Decodificar imagen y validar tamano ───────────────
    try:
        bytes_imagen = base64.b64decode(imagen_b64)
    except Exception:
        return responses.bad_request("imagenBase64 no es base64 valido")

    if len(bytes_imagen) > 10 * 1024 * 1024:
        return responses.bad_request("La imagen no puede superar 10 MB")

    # ─── Subir a S3 ────────────────────────────────────────
    extension = TIPOS_VALIDOS[tipo]
    s3_key = f"bodas/{id_boda}/local_{uuid.uuid4().hex}{extension}"

    bucket = os.environ.get("S3_BUCKET")
    if not bucket:
        return responses.server_error("S3_BUCKET no configurado")

    try:
        s3.put_object(
            Bucket=bucket,
            Key=s3_key,
            Body=bytes_imagen,
            ContentType=tipo,
        )
    except Exception as e:
        return responses.server_error(f"Error al subir a S3: {e}")

    region = os.environ.get("AWS_REGION", "us-east-1")
    foto_url = f"https://{bucket}.s3.{region}.amazonaws.com/{s3_key}"

    # ─── Persistir en BD ──────────────────────────────────
    try:
        nueva_foto = db.fetch_one(
            "usp_boda_foto_agregar",
            (id_boda, foto_url, s3_key, caption, id_usuario_autor)
        )
    except Exception as e:
        # Si falla el insert, eliminamos la foto que ya subimos a S3
        try:
            s3.delete_object(Bucket=bucket, Key=s3_key)
        except Exception:
            pass
        return responses.server_error(f"Error al guardar en BD: {e}")

    # Resolver el nombre del autor para devolverlo en la respuesta
    autor_nombre = _resolver_autor_nombre(payload, boda, id_usuario_autor)

    return responses.created({
        "id_foto": nueva_foto["id_foto"],
        "id_boda": id_boda,
        "url": nueva_foto["url"],
        "orden": nueva_foto["orden"],
        "caption": nueva_foto["caption"],
        "autor_nombre": autor_nombre,
        "autor_rol": payload["rol"],
        "tamanio_bytes": len(bytes_imagen),
        "total_fotos": total_actual + 1,
        "mensaje": "Foto agregada al evento",
    })


def _resolver_autor_nombre(payload, boda, id_usuario):
    """Resuelve el nombre legible del autor segun su rol."""
    rol = payload["rol"]
    if rol == "ADMIN":
        return "Coro Pacem Deus"
    if rol == "COUPLE":
        couple = db.fetch_one("usp_novios_obtener_por_usuario", (id_usuario,))
        if couple:
            groom = (couple.get("nombre_novio") or "").strip()
            bride = (couple.get("nombre_novia") or "").strip()
            if groom and bride:
                return f"{groom} y {bride}"
            return groom or bride or None
    if rol == "WEDDING_PLANNER":
        planner = db.fetch_one("usp_planner_obtener_por_usuario", (id_usuario,))
        if planner:
            return planner.get("nombre")
    return None


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
