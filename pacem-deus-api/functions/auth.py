# ═══════════════════════════════════════════════════════════════
# Pacem Deus Bodas — Lambda: Autenticación
# IS276 — Plataformas Móviles y Análisis Cloud — Grupo 2
# ═══════════════════════════════════════════════════════════════
# Endpoints:
#   POST /auth/login    → Iniciar sesión
#   POST /auth/register → Registro de novio/a o wedding planner
#   GET  /auth/me       → Perfil del usuario autenticado
# ═══════════════════════════════════════════════════════════════

from shared.db import (
    query, execute, get_body, success, error,
    hash_password, verify_password, generate_token, require_auth
)


def handler(event, context):
    """Punto de entrada del Lambda. Enruta según método y recurso."""
    method = event.get("httpMethod", "")
    resource = event.get("resource", "")

    # Preflight CORS
    if method == "OPTIONS":
        return success({"message": "OK"})

    if resource == "/auth/login" and method == "POST":
        return login(event)
    elif resource == "/auth/register" and method == "POST":
        return register(event)
    elif resource == "/auth/me" and method == "GET":
        return me(event)

    return error("Recurso no encontrado", 404)


# ─── LOGIN ──────────────────────────────────────────────────

def login(event):
    """Autentica al usuario con email y contraseña. Retorna JWT."""
    body = get_body(event)
    email = body.get("email", "").strip().lower()
    password = body.get("password", "")

    if not email or not password:
        return error("Email y contraseña son requeridos")

    # Buscar usuario por email
    user = query(
        "SELECT id, email, password_hash, role FROM users WHERE email = %s AND is_active = true",
        (email,), fetch_one=True
    )
    if not user:
        return error("Credenciales inválidas", 401)

    # Verificar contraseña
    if not verify_password(password, user["password_hash"]):
        return error("Credenciales inválidas", 401)

    # Generar token JWT
    token = generate_token(user["id"], user["email"], user["role"])

    # Obtener datos del perfil según el rol
    profile = _build_profile(user)

    return success({
        "token": token,
        "user": profile,
    })


# ─── REGISTRO ───────────────────────────────────────────────

def register(event):
    """Registra un nuevo usuario (novio/a o wedding planner)."""
    body = get_body(event)
    register_as = body.get("registerAs", "COUPLE")
    email = body.get("email", "").strip().lower()
    password = body.get("password", "")

    if not email or not password:
        return error("Email y contraseña son requeridos")

    if len(password) < 6:
        return error("La contraseña debe tener al menos 6 caracteres")

    # Verificar email único
    existing = query("SELECT id FROM users WHERE email = %s", (email,), fetch_one=True)
    if existing:
        return error("Ya existe una cuenta con este correo electrónico", 409)

    hashed = hash_password(password)

    if register_as == "WEDDING_PLANNER":
        return _register_planner(email, hashed, body)
    else:
        return _register_couple(email, hashed, body)


def _register_couple(email, hashed, body):
    """Registra un usuario con rol COUPLE y crea su perfil de pareja."""
    groom_name = body.get("groomName", "").strip()
    bride_name = body.get("brideName", "").strip()
    groom_dni = body.get("groomDni", "").strip()
    bride_dni = body.get("brideDni", "").strip()
    phone = body.get("phone", "").strip()

    if not all([groom_name, bride_name, groom_dni, bride_dni, phone]):
        return error("Todos los campos del novio y la novia son requeridos")

    # Crear usuario
    user = execute(
        "INSERT INTO users (email, password_hash, role) VALUES (%s, %s, 'COUPLE') RETURNING id, email, role",
        (email, hashed), returning=True
    )

    # Crear perfil de pareja
    execute(
        "INSERT INTO couples (user_id, groom_name, bride_name, groom_dni, bride_dni, phone) VALUES (%s, %s, %s, %s, %s, %s)",
        (user["id"], groom_name, bride_name, groom_dni, bride_dni, phone)
    )

    token = generate_token(user["id"], user["email"], user["role"])
    profile = _build_profile(user)

    return success({"token": token, "user": profile}, 201)


def _register_planner(email, hashed, body):
    """Registra un usuario con rol WEDDING_PLANNER y crea su perfil."""
    name = body.get("name", "").strip()
    company = body.get("company", "").strip() or None
    phone = body.get("phone", "").strip()

    if not name or not phone:
        return error("Nombre y teléfono son requeridos")

    # Crear usuario
    user = execute(
        "INSERT INTO users (email, password_hash, role) VALUES (%s, %s, 'WEDDING_PLANNER') RETURNING id, email, role",
        (email, hashed), returning=True
    )

    # Crear perfil de planner
    execute(
        "INSERT INTO wedding_planners (user_id, name, company, phone) VALUES (%s, %s, %s, %s)",
        (user["id"], name, company, phone)
    )

    token = generate_token(user["id"], user["email"], user["role"])
    profile = _build_profile(user)

    return success({"token": token, "user": profile}, 201)


# ─── PERFIL ─────────────────────────────────────────────────

def me(event):
    """Retorna el perfil completo del usuario autenticado."""
    try:
        auth = require_auth(event)
    except PermissionError:
        return error("No autorizado", 401)

    user = query(
        "SELECT id, email, role FROM users WHERE id = %s",
        (auth["userId"],), fetch_one=True
    )
    if not user:
        return error("Usuario no encontrado", 404)

    profile = _build_profile(user)
    return success(profile)


# ─── UTILIDADES ─────────────────────────────────────────────

def _build_profile(user):
    """Construye el perfil completo del usuario según su rol."""
    profile = {
        "id": user["id"],
        "email": user["email"],
        "role": user["role"],
    }

    if user["role"] == "COUPLE":
        couple = query(
            "SELECT id, groom_name, bride_name, phone FROM couples WHERE user_id = %s",
            (user["id"],), fetch_one=True
        )
        profile["couple"] = couple

        # Incluir la última boda
        if couple:
            wedding = query(
                "SELECT id, status, wedding_date FROM weddings WHERE couple_id = %s ORDER BY wedding_date DESC LIMIT 1",
                (couple["id"],), fetch_one=True
            )
            if wedding:
                profile["couple"]["weddings"] = [wedding]

    elif user["role"] == "WEDDING_PLANNER":
        planner = query(
            "SELECT id, name, company, phone FROM wedding_planners WHERE user_id = %s",
            (user["id"],), fetch_one=True
        )
        profile["weddingPlanner"] = planner

    return profile
