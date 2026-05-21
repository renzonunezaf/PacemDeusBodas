"""
get_canciones_listar.py
GET /v1/canciones?id_momento=N&criterio=ave&idioma=ES&instrumentos=piano,voz_femenina

Devuelve el catalogo de cantos filtrado.
Si se pasan instrumentos, marca cada cancion con compatible=true|false
segun si los instrumentos seleccionados cubren los MINIMUM del canto.
"""

from shared import db
from shared import responses


def handle_get_canciones_listar(event, context):
    id_momento = responses.parse_int_param(responses.get_query_param(event, "id_momento"))
    criterio = responses.get_query_param(event, "criterio")
    idioma = responses.get_query_param(event, "idioma")
    instrumentos_csv = responses.get_query_param(event, "instrumentos")

    canciones = db.fetch_all(
        "usp_canciones_listar",
        (id_momento, criterio, idioma)
    )

    # Si pasaron instrumentos, calcular compatibilidad
    if instrumentos_csv and canciones:
        selected_slugs = set(s.strip() for s in instrumentos_csv.split(",") if s.strip())

        # Cargar requerimientos MINIMUM de las canciones devueltas
        for cancion in canciones:
            req_rows = db.fetch_all(
                "usp_cancion_requerimientos_minimos",
                (cancion["id_cancion"],)
            )
            min_required = set(r["slug"] for r in req_rows)
            cancion["compatible"] = min_required.issubset(selected_slugs)
            cancion["instrumentos_minimos"] = list(min_required)

    return responses.ok({"canciones": canciones, "total": len(canciones)})
