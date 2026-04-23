# ═══════════════════════════════════════════════════════════════
# Pacem Deus Bodas — Lambda: Catálogo Musical
# IS276 — Plataformas Móviles y Análisis Cloud — Grupo 2
# ═══════════════════════════════════════════════════════════════
# Endpoints:
#   GET /moments     → Momentos litúrgicos de la ceremonia
#   GET /songs       → Canciones (filtrar por momento con ?momentId=)
#   GET /instruments → Instrumentos y voces disponibles
# ═══════════════════════════════════════════════════════════════

from shared.db import query, get_query_param, success, error, require_auth


def handler(event, context):
    """Punto de entrada del Lambda. Enruta según método y recurso."""
    method = event.get("httpMethod", "")
    resource = event.get("resource", "")

    if method == "OPTIONS":
        return success({"message": "OK"})

    if resource == "/moments" and method == "GET":
        return get_moments(event)
    elif resource == "/songs" and method == "GET":
        return get_songs(event)
    elif resource == "/instruments" and method == "GET":
        return get_instruments(event)

    return error("Recurso no encontrado", 404)


# ─── MOMENTOS LITÚRGICOS ────────────────────────────────────

def get_moments(event):
    """
    Retorna los 14 momentos litúrgicos de la ceremonia,
    ordenados según el flujo de la misa.
    """
    try:
        require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    moments = query("""
        SELECT id, slug, name, description, icon, display_order, max_songs
        FROM liturgical_moments
        WHERE is_active = true
        ORDER BY display_order ASC
    """)

    return success(moments)


# ─── CANCIONES ───────────────────────────────────────────────

def get_songs(event):
    """
    Retorna canciones filtradas por momento litúrgico.
    Query param: ?momentId=xxx
    Si no se envía momentId, retorna todas las canciones activas.
    """
    try:
        require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    moment_id = get_query_param(event, "momentId")

    if moment_id:
        # Canciones disponibles para un momento específico
        songs = query("""
            SELECT s.id, s.title, s.author, s.language
            FROM songs s
            JOIN song_moments sm ON sm.song_id = s.id
            WHERE sm.moment_id = %s AND s.is_active = true
            ORDER BY s.title ASC
        """, (moment_id,))
    else:
        # Todas las canciones activas
        songs = query("""
            SELECT id, title, author, language
            FROM songs
            WHERE is_active = true
            ORDER BY title ASC
        """)

    return success(songs)


# ─── INSTRUMENTOS ────────────────────────────────────────────

def get_instruments(event):
    """Retorna la lista de instrumentos y voces disponibles."""
    try:
        require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    instruments = query("""
        SELECT id, slug, name, icon, price_lima, sort_order
        FROM instruments
        WHERE is_active = true
        ORDER BY sort_order ASC
    """)

    return success(instruments)
