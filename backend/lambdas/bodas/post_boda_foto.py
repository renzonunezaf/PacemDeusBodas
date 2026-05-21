"""
post_boda_foto.py
POST /v1/bodas/{id_boda}/foto
Header: Authorization: Bearer <token>

Body: { "imagenBase64": "...", "tipoContenido": "image/jpeg" }

Sube la foto a S3 y guarda la URL en boda.foto_local_url.

Variable de entorno requerida:
  S3_BUCKET  -> nombre del bucket
"""

import os
import base64
import uuid
import boto3
from shared import db
from shared import auth
from shared import responses


s3 = boto3.client("s3")


TIPOS_VALIDOS = {
    "image/jpeg": ".jpg",
    "image/jpg":  ".jpg",
    "image/png":  ".png",
    "image/webp": ".webp",
}


def handle_post_boda_foto(event, context):
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

    imagen_b64 = body.get("imagenBase64")
    tipo = (body.get("tipoContenido") or "image/jpeg").lower()

    if not imagen_b64:
        return responses.bad_request("imagenBase64 es requerido")
    if tipo not in TIPOS_VALIDOS:
        return responses.bad_request(
            f"tipoContenido invalido. Soportados: {', '.join(TIPOS_VALIDOS.keys())}"
        )

    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    if not _tiene_acceso(payload, boda):
        return responses.forbidden("No tienes acceso a esta boda")

    try:
        bytes_imagen = base64.b64decode(imagen_b64)
    except Exception:
        return responses.bad_request("imagenBase64 no es base64 valido")

    if len(bytes_imagen) > 10 * 1024 * 1024:
        return responses.bad_request("La imagen no puede superar 10 MB")

    extension = TIPOS_VALIDOS[tipo]
    s3_key = f"bodas/{id_boda}/local_{uuid.uuid4().hex}{extension}"

    bucket = os.environ.get("S3_BUCKET")
    if not bucket:
        return responses.server_error("S3_BUCKET no configurado")

    s3.put_object(
        Bucket=bucket,
        Key=s3_key,
        Body=bytes_imagen,
        ContentType=tipo,
    )

    # Construir la URL HTTPS publica. La region viene siempre en AWS_REGION
    # cuando la Lambda esta corriendo en runtime. Default us-east-1 si por
    # alguna razon no esta (no deberia pasar).
    region = os.environ.get("AWS_REGION", "us-east-1")
    foto_url = f"https://{bucket}.s3.{region}.amazonaws.com/{s3_key}"
    db.execute("usp_boda_actualizar_foto", (id_boda, foto_url))

    return responses.ok({
        "id_boda": id_boda,
        "foto_local_url": foto_url,
        "tamanio_bytes": len(bytes_imagen),
        "mensaje": "Foto del local actualizada",
    })


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
