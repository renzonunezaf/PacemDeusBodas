"""
post_boda_anotacion_responder.py
POST /v1/bodas/{id_boda}/anotaciones/responder
Solo COUPLE (la pareja duena de la boda).

Body: { "aceptar": true } o { "aceptar": false }

ACEPTAR:
  - La anotacion pendiente queda marcada como ACEPTADA.
  - La boda vuelve a estado SUBMITTED (regresa al coro para
    finalizar el flujo de aprobacion).

RECHAZAR:
  - La anotacion queda marcada como RECHAZADA.
  - La boda vuelve a DRAFT.
  - REVIERTE setlist, instrumentos, venue y precios al snapshot
    guardado en la anotacion. La novia puede editar libremente.
"""

import json
from shared import db
from shared import auth
from shared import responses
from shared import notifications


def handle_post_boda_anotacion_responder(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    if payload["rol"] != "COUPLE":
        return responses.forbidden("Solo la pareja puede responder anotaciones")

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    aceptar = bool(body.get("aceptar", False))

    # Verificar pertenencia
    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Boda no encontrada")
    couple = db.fetch_one("usp_novios_obtener_por_usuario", (payload["id_usuario"],))
    if not couple or couple["id_novios"] != boda["id_novios"]:
        return responses.forbidden("No tienes acceso a esta boda")

    if boda["estado"] != "RETURNED_WITH_NOTES":
        return responses.bad_request(
            "La boda no esta en estado RETURNED_WITH_NOTES"
        )

    # Anotacion pendiente
    anotacion = db.fetch_one("usp_anotacion_obtener_pendiente", (id_boda,))
    if not anotacion:
        return responses.not_found("No hay anotacion pendiente para esta boda")

    if aceptar:
        db.execute("usp_anotacion_marcar",
                   (anotacion["id_boda_anotacion"], "ACEPTADA"))
        db.execute("usp_boda_cambiar_estado", (id_boda, "SUBMITTED", None))

        # Avisar al coro: la pareja acepto los cambios y la boda vuelve
        # a su panel de "Por aprobar" para cerrar el ciclo.
        try:
            couple_label = notifications.couple_label(boda["id_novios"])
            notifications.notify_admins(
                tipo="BODA_NOTES_ACCEPTED",
                titulo="Una pareja confirmo tus anotaciones",
                mensaje=f"{couple_label} acepta los ajustes propuestos. Su evento regresa a revision.",
                id_boda=id_boda
            )
        except Exception:
            pass

        return responses.ok({
            "id_boda": id_boda,
            "estado_boda": "SUBMITTED",
            "mensaje": "Cambios aceptados. El coro recibira la notificacion."
        })
    else:
        # Revertir al snapshot
        try:
            snapshot = json.loads(anotacion.get("snapshot_antes") or "{}")
        except (ValueError, TypeError):
            snapshot = {}

        if snapshot:
            try:
                _revertir_boda(id_boda, snapshot)
            except Exception as e:
                return responses.server_error(f"Error al revertir cambios: {e}")

        db.execute("usp_anotacion_marcar",
                   (anotacion["id_boda_anotacion"], "RECHAZADA"))
        db.execute("usp_boda_cambiar_estado", (id_boda, "DRAFT", None))

        # Avisar al coro: la pareja prefirio conservar la version anterior.
        try:
            couple_label = notifications.couple_label(boda["id_novios"])
            notifications.notify_admins(
                tipo="BODA_NOTES_REJECTED",
                titulo="Una pareja prefiere su version original",
                mensaje=f"{couple_label} rechazo los ajustes propuestos. La boda volvio a borrador.",
                id_boda=id_boda
            )
        except Exception:
            pass

        return responses.ok({
            "id_boda": id_boda,
            "estado_boda": "DRAFT",
            "mensaje": "Cambios rechazados. La boda volvio a borrador con tu version original."
        })


def _revertir_boda(id_boda, snap):
    """
    Aplica el snapshot guardado: venue, instrumentos, setlist, precios.
    Usa SPs existentes (editar boda, reemplazar instrumentos, agregar
    setlist).
    """
    # Venue + precios via update directo a tabla
    conn = db.get_connection()
    cur = conn.cursor()
    cur.execute("""
        UPDATE boda
        SET    nombre_local = ?, direccion_local = ?,
               latitud = ?, longitud = ?,
               precio_base = ?, precio_instrumentos = ?,
               precio_movilidad = ?, precio_total = ?,
               fuera_de_lima = ?
        WHERE  id_boda = ?
    """, (
        snap.get("venue_name"), snap.get("venue_address"),
        snap.get("venue_lat"), snap.get("venue_lng"),
        snap.get("precio_base", 0), snap.get("precio_instrumentos", 0),
        snap.get("precio_movilidad", 0), snap.get("precio_total", 0),
        1 if snap.get("fuera_de_lima") else 0,
        id_boda
    ))

    # Instrumentos: borrar todos y reinsertar segun snapshot
    cur.execute("DELETE FROM boda_instrumento WHERE id_boda = ?", (id_boda,))
    slugs = snap.get("instrumentos") or []
    if slugs:
        placeholders = ",".join(["?"] * len(slugs))
        cur.execute(
            f"INSERT INTO boda_instrumento (id_boda, id_instrumento) "
            f"SELECT ?, id_instrumento FROM instrumento "
            f"WHERE slug IN ({placeholders}) AND activo = 1",
            tuple([id_boda] + slugs)
        )

    # Setlist: borrar y reinsertar
    cur.execute("DELETE FROM setlist WHERE id_boda = ?", (id_boda,))
    for item in (snap.get("setlist") or []):
        cur.execute("""
            INSERT INTO setlist (id_boda, id_momento, id_cancion, orden)
            VALUES (?, ?, ?, ?)
        """, (id_boda, item.get("id_momento"), item.get("id_cancion"),
              item.get("orden", 0)))

    conn.commit()
    cur.close()
