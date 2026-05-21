"""
delete_boda_foto.py
DELETE /v1/bodas/{id_boda}/fotos/{id_foto}
Header: Authorization: Bearer <token>

Elimina una foto del evento:
  1. Borra la fila de boda_foto via SP (que devuelve el s3_key)
  2. Borra el objeto de S3 usando ese s3_key

Si la foto no pertenece a la boda, devuelve 404. Si el delete de S3 falla
pero el de BD si funciono, lo logueamos pero respondemos 200 igual: la
foto ya no es visible al usuario y un proceso de limpieza de huerfanos
puede correr aparte.
"""

import os
import boto3
from shared import db
from shared import auth
from shared import responses


s3 = boto3.client("s3")


def handle_delete_boda_foto(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    id_foto = responses.parse_int_param(responses.get_path_param(event, "id_foto"))
    if not id_boda or not id_foto:
        return responses.bad_request("id_boda e id_foto son requeridos")

    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    if not _tiene_acceso(payload, boda):
        return responses.forbidden("No tienes acceso a esta boda")

    # Borrar de BD (el SP devuelve s3_key o NULL si la foto no existia)
    result = db.fetch_one("usp_boda_foto_eliminar", (id_boda, id_foto))
    s3_key = result.get("s3_key") if result else None

    if s3_key is None:
        return responses.not_found("Foto no encontrada para este evento")

    # Borrar de S3 (best effort)
    bucket = os.environ.get("S3_BUCKET")
    if bucket:
        try:
            s3.delete_object(Bucket=bucket, Key=s3_key)
        except Exception as e:
            # Lo logueamos pero no abortamos: la foto ya no aparece al usuario.
            print(f"WARN: no se pudo borrar de S3 ({s3_key}): {e}")

    return responses.ok({
        "id_boda": id_boda,
        "id_foto": id_foto,
        "mensaje": "Foto eliminada",
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
