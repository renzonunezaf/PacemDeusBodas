"""
shared/seasons.py
Logica de tiempos liturgicos. Parsea las restricciones JSON y aplica los
4 modificadores de visibilidad a los momentos liturgicos.
"""

import json
from . import db


def get_season_for_date(fecha_boda):
    """Encuentra el tiempo liturgico vigente. Default: ordinario."""
    season = db.fetch_one("usp_temporada_obtener_por_fecha", (fecha_boda,))
    if season:
        restrictions = season.get("restricciones") or "{}"
        try:
            restrictions = json.loads(restrictions) if isinstance(restrictions, str) else restrictions
        except (json.JSONDecodeError, TypeError):
            restrictions = {}
        return {
            "slug": season["slug"],
            "nombre": season["nombre"],
            "restricciones": restrictions,
        }
    return {
        "slug": "ordinario",
        "nombre": "Tiempo Ordinario",
        "restricciones": {},
    }


def get_moments_with_status(fecha_boda):
    """
    Devuelve los momentos liturgicos con su estado para una fecha de boda.

    Reglas de visibilidad (orden de aplicacion):
      oculto_en          -> el momento no aparece
      mostrado_solo_en   -> aparece solo en estas temporadas
      deshabilitado_en   -> aparece grayed con razon
      momentos_deshabilitados (de la temporada) -> aplicar tambien
    """
    season = get_season_for_date(fecha_boda)
    season_slug = season["slug"]
    season_restrictions = season["restricciones"] or {}
    disabled_moments = season_restrictions.get("momentos_deshabilitados", [])

    all_moments = db.fetch_all("usp_momentos_listar")

    visible = []
    for m in all_moments:
        # Parsear restricciones del momento
        raw = m.get("restricciones_temporada")
        try:
            restr = json.loads(raw) if raw else {}
        except (json.JSONDecodeError, TypeError):
            restr = {}

        is_visible = True
        is_enabled = True
        disabled_reason = None

        # 1. Oculto en esta temporada
        if season_slug in (restr.get("oculto_en") or []):
            is_visible = False

        # 2. Solo se muestra en ciertas temporadas
        if "mostrado_solo_en" in restr and season_slug not in restr["mostrado_solo_en"]:
            is_visible = False

        if not is_visible:
            continue

        # 3. Temporada lo deshabilita por defecto
        if m["slug"] in disabled_moments:
            is_enabled = False
            disabled_reason = f"No se canta en {season['nombre']}"

        # 4. Restriccion del momento dice deshabilitado en esta temporada
        if season_slug in (restr.get("deshabilitado_en") or []):
            is_enabled = False
            disabled_reason = f"No se canta en {season['nombre']}"

        visible.append({
            "id_momento": m["id_momento"],
            "slug": m["slug"],
            "nombre": m["nombre"],
            "descripcion": m["descripcion"],
            "icono": m["icono"],
            "orden": m["orden"],
            "categoria": m["categoria"],
            "max_canciones": m["max_canciones"],
            "permite_repetidas": m["permite_repetidas"],
            "habilitado": is_enabled,
            "razon_deshabilitado": disabled_reason,
        })

    return {
        "temporada": {
            "slug": season["slug"],
            "nombre": season["nombre"],
        },
        "momentos": visible,
    }
