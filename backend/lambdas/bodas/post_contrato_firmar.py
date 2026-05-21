"""
post_contrato_firmar.py
POST /v1/bodas/{id_boda}/contrato/firmar
Header: Authorization: Bearer <token>

Body: { "nombreFirmante": "Renzo Nunez Berdejo" }

Firma electronica de doble via:
  - Si firma COUPLE: guarda nombre, fecha e IP del cliente.
  - Si firma ADMIN: guarda nombre y fecha.
  - Si AMBAS partes ya firmaron: VALIDAR CONFLICTO y, si pasa,
    la boda automaticamente pasa a CONTRACTED.

Validacion de conflicto al pasar a CONTRACTED:
  Max 2 bodas CONTRACTED por dia, separadas >=5h. Si la boda actual
  rompe la regla, NO se cambia el estado y se devuelve 409.
  La firma del admin SI se mantiene registrada (para auditoria), pero
  la boda no progresa. Hay que reagendar primero.

Notifs:
  - Primera firma (couple) -> notify_admins ("falta tu firma")
  - Primera firma (admin)  -> notify_couple ("falta tu firma")
  - Segunda firma -> avisar a la otra parte que el contrato esta cerrado
"""

from shared import db
from shared import auth
from shared import responses
from shared import notifications


def handle_post_contrato_firmar(event, context):
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

    nombre_firmante = (body.get("nombreFirmante") or "").strip()
    if not nombre_firmante:
        return responses.bad_request("nombreFirmante es requerido")

    rol = payload["rol"]
    if rol not in ("ADMIN", "COUPLE"):
        return responses.forbidden("Solo novios o admin pueden firmar el contrato")

    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    contrato = db.fetch_one("usp_contrato_obtener", (id_boda,))
    if not contrato:
        return responses.not_found("Contrato no encontrado")

    if rol == "COUPLE":
        couple = db.fetch_one("usp_novios_obtener_por_usuario", (payload["id_usuario"],))
        if not couple or couple["id_novios"] != boda["id_novios"]:
            return responses.forbidden("No tienes acceso a este contrato")

    headers = event.get("headers") or {}
    ip_cliente = (
        headers.get("X-Forwarded-For", "").split(",")[0].strip()
        or headers.get("x-forwarded-for", "").split(",")[0].strip()
        or "unknown"
    )

    if rol == "ADMIN":
        db.execute("usp_contrato_firmar_admin", (id_boda, nombre_firmante))
    else:
        db.execute("usp_contrato_firmar_novios", (id_boda, nombre_firmante, ip_cliente))

    estado_firmas = db.fetch_one("usp_contrato_estado_firmas", (id_boda,))

    if estado_firmas["firmado_novios"] and estado_firmas["firmado_admin"]:
        # Validacion de conflicto antes de pasar a CONTRACTED.
        # Excluimos la propia boda (puede tener fecha ya asignada).
        hora_boda = boda.get("hora_boda")
        # hora_boda puede venir como timedelta de pyodbc
        hora_str = _format_hora_for_sp(hora_boda)

        conflicto = db.fetch_one(
            "usp_boda_validar_conflicto",
            (boda["fecha_boda"], hora_str,
             boda.get("latitud"), boda.get("longitud"),
             id_boda)
        )

        if conflicto and conflicto["conflicto"]:
            # No revertimos la firma (queda en auditoria) pero NO
            # progresamos el estado. La pareja/coro deben reagendar.
            razon = conflicto.get("razon") or "Conflicto de fecha/hora con otra boda contratada."
            return responses.conflict(
                f"{razon} Reagende y vuelva a firmar."
            )

        # Sin conflicto: avanzar a CONTRACTED.
        db.execute("usp_boda_cambiar_estado", (id_boda, "CONTRACTED", None))
        nuevo_estado = "CONTRACTED"
        mensaje = "Contrato firmado por ambas partes. La boda esta CONTRATADA."

        # Notificar a la OTRA parte que el contrato esta cerrado. La parte
        # que firmo en este request recibe la respuesta sincrona del 200
        # con "CONTRACTED" -- no necesita push.
        try:
            if rol == "ADMIN":
                # Admin sello la 2da firma; avisar a la pareja.
                notifications.notify_couple(
                    id_boda=id_boda,
                    tipo="BODA_CONTRACTED",
                    titulo="Tu evento esta contratado",
                    mensaje="Ambas firmas estan registradas. Nos vemos el dia de tu boda."
                )
            else:
                # Pareja sello la 2da firma; avisar al coro.
                couple_label_str = notifications.couple_label(boda["id_novios"])
                notifications.notify_admins(
                    tipo="BODA_CONTRACTED",
                    titulo="Boda contratada",
                    mensaje=f"{couple_label_str} firmo el contrato. El evento esta contratado.",
                    id_boda=id_boda
                )
        except Exception:
            pass
    else:
        nuevo_estado = boda["estado"]
        mensaje = "Tu firma fue registrada. Esperando firma de la otra parte."

        # Primera firma: notificar a la parte que falta firmar.
        try:
            if rol == "COUPLE":
                # La pareja firmo primero. Avisar al coro que falta su firma.
                couple_label_str = notifications.couple_label(boda["id_novios"])
                notifications.notify_admins(
                    tipo="CONTRATO_FIRMADO_NOVIOS",
                    titulo="Firma pendiente del coro",
                    mensaje=f"{couple_label_str} firmo el contrato. Falta tu firma para sellar el evento.",
                    id_boda=id_boda
                )
            else:
                # El admin firmo primero. Avisar a la pareja que falta su firma.
                notifications.notify_couple(
                    id_boda=id_boda,
                    tipo="CONTRATO_FIRMADO_ADMIN",
                    titulo="El coro firmo tu contrato",
                    mensaje="Falta solo tu firma para que tu evento quede contratado."
                )
        except Exception:
            pass

    return responses.ok({
        "id_boda": id_boda,
        "firmado_novios": estado_firmas["firmado_novios"],
        "firmado_admin": estado_firmas["firmado_admin"],
        "estado_boda": nuevo_estado,
        "mensaje": mensaje,
    })


def _format_hora_for_sp(value):
    """Acepta timedelta de pyodbc o str. Devuelve 'HH:MM:SS' o vacio."""
    if value is None:
        return None
    s = str(value)
    if len(s) >= 8:
        return s[:8]
    if len(s) == 5:
        return s + ":00"
    return s
