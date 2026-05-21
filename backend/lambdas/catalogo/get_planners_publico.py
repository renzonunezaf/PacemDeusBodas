"""
get_planners_publico.py
GET /v1/planners
Header: Authorization: Bearer <token>

Devuelve la lista de wedding planners disponibles para que el COUPLE
(la novia) los pueda seleccionar al armar su evento. Versión publica
del endpoint /admin/planners pero accesible para cualquier rol
autenticado.

Respuesta 200:
  {
    "planners": [
      { "id_planner": "1", "nombre": "Carla Mendoza",
        "empresa": "Bodas Doradas", "telefono": "+51..." },
      ...
    ],
    "total": N
  }
"""

from shared import db
from shared import auth
from shared import responses


def handle_get_planners_publico(event, context):
    try:
        auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    planners = db.fetch_all("usp_planners_listar_publico")

    planners_payload = [
        {
            "id_planner": str(p["id_planner"]),
            "nombre": p["nombre"],
            "empresa": p.get("empresa"),
            "telefono": p["telefono"],
        }
        for p in planners
    ]

    return responses.ok({
        "planners": planners_payload,
        "total": len(planners_payload),
    })
