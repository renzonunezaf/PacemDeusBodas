"""
get_pagos_listar.py
GET /v1/admin/bodas/{id_boda}/pagos
Header: Authorization: Bearer <token> (rol: ADMIN)

Lista los pagos registrados para una boda con el total acumulado.
"""

from shared import db
from shared import auth
from shared import responses


def handle_get_pagos_listar(event, context):
    try:
        auth.authenticate(event, allowed_roles=["ADMIN"])
    except auth.AuthError as e:
        return responses.unauthorized(e.message) if e.status == 401 else responses.forbidden(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    pagos = db.fetch_all("usp_pagos_listar", (id_boda,))
    total = sum(float(p["monto"]) for p in pagos)

    return responses.ok({"pagos": pagos, "total_pagado": total})
