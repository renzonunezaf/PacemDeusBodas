"""
post_pago_crear.py
POST /v1/admin/bodas/{id_boda}/pagos
Header: Authorization: Bearer <token> (rol: ADMIN)

Body:
  {
    "monto": 500.00,
    "fechaPago": "2026-12-01",
    "banco": "SCOTIABANK | BCP | INTERBANK",
    "tipoPago": "ADVANCE | BALANCE",
    "notas": "..."
  }
"""

from shared import db
from shared import auth
from shared import responses


BANCOS_VALIDOS = ("SCOTIABANK", "BCP", "INTERBANK")
TIPOS_VALIDOS = ("ADVANCE", "BALANCE")


def handle_post_pago_crear(event, context):
    try:
        auth.authenticate(event, allowed_roles=["ADMIN"])
    except auth.AuthError as e:
        return responses.unauthorized(e.message) if e.status == 401 else responses.forbidden(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    monto = body.get("monto")
    fecha_pago = body.get("fechaPago")
    banco = body.get("banco")
    tipo_pago = body.get("tipoPago")
    notas = body.get("notas")

    # Validaciones
    try:
        monto_val = float(monto)
        if monto_val <= 0:
            raise ValueError()
    except (TypeError, ValueError):
        return responses.bad_request("monto debe ser un numero positivo")

    if not fecha_pago:
        return responses.bad_request("fechaPago es requerido (formato YYYY-MM-DD)")
    if banco not in BANCOS_VALIDOS:
        return responses.bad_request(f"banco debe ser uno de: {', '.join(BANCOS_VALIDOS)}")
    if tipo_pago not in TIPOS_VALIDOS:
        return responses.bad_request(f"tipoPago debe ser uno de: {', '.join(TIPOS_VALIDOS)}")

    # Verifica que la boda existe
    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    result = db.execute_returning_id(
        "usp_pago_crear",
        (id_boda, monto_val, fecha_pago, banco, tipo_pago, notas)
    )

    return responses.created({
        "id_pago": result["id_pago"],
        "id_boda": id_boda,
        "monto": monto_val,
        "fecha_pago": fecha_pago,
        "banco": banco,
        "tipo_pago": tipo_pago,
    })
