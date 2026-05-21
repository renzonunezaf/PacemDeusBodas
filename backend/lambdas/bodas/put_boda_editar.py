"""
put_boda_editar.py
PUT /v1/bodas/{id_boda}
Header: Authorization: Bearer <token>

Permite que la novia (rol COUPLE, duena de la boda) o el ADMIN actualicen los
datos de un evento en estado DRAFT. Si la boda ya fue enviada/aprobada, no se
permite edicion (devuelve 409 Conflict).

Al editar la ubicacion/fecha/hora, recalculamos automaticamente toda la
cotizacion para que precio_base, precio_instrumentos y precio_movilidad
queden coherentes con los nuevos datos.

Body (todos los campos requeridos):
  {
    "fechaBoda":       "2026-12-15",
    "horaBoda":        "16:00",
    "nombreLocal":     "Parroquia Sagrada Familia",
    "direccionLocal":  "Av. Comandante Espinar 800, Bellavista",
    "latitud":         -12.0541,
    "longitud":        -77.0933
  }

Respuesta 200:
  {
    "id_boda": 12,
    "fecha_boda": "2026-12-15",
    ...
    "desglose_precio": { precio_base, precio_instrumentos, ... }
  }
"""

from datetime import datetime
from shared import auth
from shared import db
from shared import pricing
from shared import responses


def handle_put_boda_editar(event, context):
    # ─── Autenticacion ─────────────────────────────────────────────
    try:
        payload = auth.authenticate(event, allowed_roles=["COUPLE", "ADMIN"])
    except auth.AuthError as e:
        if e.status == 401:
            return responses.unauthorized(e.message)
        return responses.forbidden(e.message)

    # ─── Path param ────────────────────────────────────────────────
    path_params = event.get("pathParameters") or {}
    id_boda_str = path_params.get("id_boda")
    if not id_boda_str:
        return responses.bad_request("id_boda es requerido")
    try:
        id_boda = int(id_boda_str)
    except (TypeError, ValueError):
        return responses.bad_request("id_boda debe ser numerico")

    # ─── Body ──────────────────────────────────────────────────────
    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    fecha_str = body.get("fechaBoda")
    hora_str = body.get("horaBoda")
    nombre_local = (body.get("nombreLocal") or "").strip()
    direccion = (body.get("direccionLocal") or "").strip()
    latitud = body.get("latitud")
    longitud = body.get("longitud")

    if not fecha_str or not hora_str:
        return responses.bad_request("Fecha y hora son requeridos")
    if not nombre_local or not direccion:
        return responses.bad_request("Nombre y direccion del local son requeridos")
    if latitud is None or longitud is None:
        return responses.bad_request("Latitud y longitud son requeridos")

    try:
        latitud = float(latitud)
        longitud = float(longitud)
    except (TypeError, ValueError):
        return responses.bad_request("Latitud y longitud deben ser numericos")

    # ─── Parsear fecha y hora ──────────────────────────────────────
    try:
        fecha_part = datetime.strptime(fecha_str, "%Y-%m-%d").date()
        h, mn = hora_str.split(":")
        departure_dt = datetime(
            fecha_part.year, fecha_part.month, fecha_part.day,
            int(h), int(mn)
        )
    except (ValueError, IndexError):
        return responses.bad_request(
            "Formato invalido. Fecha YYYY-MM-DD y hora HH:MM"
        )

    # ─── Validacion: fecha debe ser futura ────────────────────────
    if departure_dt.date() < datetime.now().date():
        return responses.bad_request(
            "La fecha del evento debe ser igual o posterior a hoy"
        )

    # ─── Validar ownership y estado DRAFT ─────────────────────────
    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Boda no encontrada")
    if boda["estado"] != "DRAFT":
        return responses.conflict(
            "Solo se pueden editar bodas en estado borrador"
        )

    # Si es COUPLE, debe ser la duena de la boda
    if payload["rol"] == "COUPLE":
        couple = db.fetch_one(
            "usp_novios_obtener_por_usuario", (payload["id_usuario"],)
        )
        if not couple or couple["id_novios"] != boda["id_novios"]:
            return responses.forbidden(
                "No tienes permiso para editar esta boda"
            )

    # ─── Recalcular cotizacion con los nuevos datos ───────────────
    # Cargamos los instrumentos actuales de la boda para mantener la lista
    # y recalcular el precio con el factor de distancia que corresponda.
    instrumentos_rows = db.fetch_all(
        "usp_boda_instrumentos_listar", (id_boda,)
    )
    instrument_slugs = [r["slug"] for r in instrumentos_rows]

    breakdown = pricing.calculate_price_breakdown(
        latitud, longitud, instrument_slugs, departure_dt
    )

    # ─── Persistir la actualizacion ───────────────────────────────
    try:
        db.execute(
            "usp_boda_actualizar",
            (
                id_boda,
                fecha_part,
                hora_str,
                nombre_local,
                direccion,
                latitud,
                longitud,
                1 if breakdown["fuera_de_lima"] else 0,
                breakdown["precio_base"],
                breakdown["precio_instrumentos"],
                breakdown["precio_movilidad"],
                breakdown["precio_total"],
            )
        )
    except Exception as e:
        return responses.server_error(f"Error al actualizar boda: {e}")

    return responses.ok({
        "id_boda": id_boda,
        "fecha_boda": fecha_str,
        "hora_boda": hora_str,
        "nombre_local": nombre_local,
        "direccion_local": direccion,
        "latitud": latitud,
        "longitud": longitud,
        "estado": "DRAFT",
        "desglose_precio": breakdown,
    })
