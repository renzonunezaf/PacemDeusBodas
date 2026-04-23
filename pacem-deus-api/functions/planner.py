# ═══════════════════════════════════════════════════════════════
# Pacem Deus Bodas — Lambda: Dashboard del Wedding Planner
# IS276 — Plataformas Móviles y Análisis Cloud — Grupo 2
# ═══════════════════════════════════════════════════════════════
# Endpoints:
#   GET /planner/weddings → Eventos asignados al planner (solo lectura)
# ═══════════════════════════════════════════════════════════════

from shared.db import query, success, error, require_auth


def handler(event, context):
    """Punto de entrada del Lambda. Enruta según método y recurso."""
    method = event.get("httpMethod", "")
    resource = event.get("resource", "")

    if method == "OPTIONS":
        return success({"message": "OK"})

    if resource == "/planner/weddings" and method == "GET":
        return get_planner_weddings(event)

    return error("Recurso no encontrado", 404)


# ─── EVENTOS DEL WEDDING PLANNER ────────────────────────────

def get_planner_weddings(event):
    """
    Retorna todos los eventos asignados al wedding planner autenticado.
    Vista de solo lectura: el planner puede ver pero no modificar.
    Incluye todos los estados (desde DRAFT hasta COMPLETED).
    """
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    if auth["role"] != "WEDDING_PLANNER":
        return error("Acceso denegado: se requiere rol WEDDING_PLANNER", 403)

    # Obtener el perfil del planner
    planner = query(
        "SELECT id FROM wedding_planners WHERE user_id = %s",
        (auth["userId"],), fetch_one=True
    )
    if not planner:
        return error("Perfil de planner no encontrado", 404)

    # Traer todos los eventos asignados a este planner
    weddings = query("""
        SELECT w.id, w.wedding_date, w.wedding_time,
               w.venue_name, w.venue_address, w.venue_lat, w.venue_lng,
               w.venue_photo_url, w.status, w.total_price, w.created_at,
               c.groom_name, c.bride_name, c.phone,
               (SELECT COUNT(*) FROM wedding_instruments wi WHERE wi.wedding_id = w.id) AS instrument_count,
               (SELECT COUNT(*) FROM setlist_items si WHERE si.wedding_id = w.id) AS setlist_count,
               (SELECT COUNT(*) > 0 FROM contracts ct WHERE ct.wedding_id = w.id) AS has_contract
        FROM weddings w
        JOIN couples c ON c.id = w.couple_id
        WHERE w.planner_id = %s
        ORDER BY w.wedding_date ASC
    """, (planner["id"],))

    return success(weddings)
