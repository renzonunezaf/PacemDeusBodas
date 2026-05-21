"""
post_auth_login.py
POST /v1/auth/login
Body: { "email": "...", "password": "..." }

Devuelve JWT + datos del usuario y su perfil (novios o planner).
Patron: SP usp_usuario_obtener_por_email + verificacion bcrypt en Python.
"""

from shared import db
from shared import auth
from shared import responses


def handle_post_auth_login(event, context):
    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    email = (body.get("email") or "").strip().lower()
    password = body.get("password") or ""

    if not email or not password:
        return responses.bad_request("Email y password son requeridos")

    # Busca usuario via stored procedure
    user = db.fetch_one("usp_usuario_obtener_por_email", (email,))

    if not user or not user["activo"]:
        return responses.unauthorized("Credenciales invalidas")

    if not auth.verify_password(password, user["password_hash"]):
        return responses.unauthorized("Credenciales invalidas")

    token = auth.sign_token(user["id_usuario"], user["rol"])

    # Carga perfil segun rol
    profile = None
    if user["rol"] == "COUPLE":
        profile = db.fetch_one("usp_novios_obtener_por_usuario", (user["id_usuario"],))
    elif user["rol"] == "WEDDING_PLANNER":
        profile = db.fetch_one("usp_planner_obtener_por_usuario", (user["id_usuario"],))

    return responses.ok({
        "token": token,
        "usuario": {
            "id_usuario": user["id_usuario"],
            "email": user["email"],
            "rol": user["rol"],
        },
        "perfil": profile,
    })
