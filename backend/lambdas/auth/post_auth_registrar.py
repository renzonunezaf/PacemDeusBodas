"""
post_auth_registrar.py
POST /v1/auth/registrar
Body para novios:
  {
    "rol": "COUPLE",
    "email": "...", "password": "...",
    "nombreNovio": "...", "nombreNovia": "...",
    "tipoDocNovio": "DNI|CE|PASAPORTE", "tipoDocNovia": "DNI|CE|PASAPORTE",
    "documentoNovio": "...", "documentoNovia": "...",
    "telefono": "...",
    "comoSeEntero": "REDES_SOCIALES|REFERIDO|BODA_PRESENCIADA|YOUTUBE|SAGRADA_FAMILIA|OTRO"
  }

Body para wedding planner:
  {
    "rol": "WEDDING_PLANNER",
    "email": "...", "password": "...",
    "nombre": "...", "empresa": "...", "telefono": "..."
  }
"""

from shared import db
from shared import auth
from shared import responses


VALID_ROLES = ("COUPLE", "WEDDING_PLANNER")
VALID_DOC_TYPES = ("DNI", "CE", "PASAPORTE")
VALID_HOW_FOUND = (
    "REDES_SOCIALES", "REFERIDO", "BODA_PRESENCIADA",
    "YOUTUBE", "SAGRADA_FAMILIA", "OTRO",
)


def handle_post_auth_registrar(event, context):
    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    rol = body.get("rol")
    email = (body.get("email") or "").strip().lower()
    password = body.get("password") or ""

    if rol not in VALID_ROLES:
        return responses.bad_request(f"rol debe ser uno de: {', '.join(VALID_ROLES)}")
    if not email or "@" not in email:
        return responses.bad_request("Email invalido")
    if not password or len(password) < 8:
        return responses.bad_request("Password debe tener al menos 8 caracteres")

    # Verifica que el email no este en uso
    existing = db.fetch_one("usp_usuario_obtener_por_email", (email,))
    if existing:
        return responses.conflict("El email ya esta registrado")

    password_hash = auth.hash_password(password)

    if rol == "COUPLE":
        return _registrar_novios(body, email, password_hash)
    return _registrar_planner(body, email, password_hash)


def _registrar_novios(body, email, password_hash):
    nombre_novio = (body.get("nombreNovio") or "").strip()
    nombre_novia = (body.get("nombreNovia") or "").strip()
    documento_novio = (body.get("documentoNovio") or "").strip()
    documento_novia = (body.get("documentoNovia") or "").strip()
    telefono = (body.get("telefono") or "").strip()
    tipo_doc_novio = body.get("tipoDocNovio", "DNI")
    tipo_doc_novia = body.get("tipoDocNovia", "DNI")
    como_se_entero = body.get("comoSeEntero", "OTRO")

    if not nombre_novio or not nombre_novia:
        return responses.bad_request("Nombres del novio y la novia son requeridos")
    if not documento_novio or not documento_novia:
        return responses.bad_request("Documentos de identidad son requeridos")
    if not telefono:
        return responses.bad_request("Telefono es requerido")
    if tipo_doc_novio not in VALID_DOC_TYPES or tipo_doc_novia not in VALID_DOC_TYPES:
        return responses.bad_request("Tipo de documento invalido")
    if como_se_entero not in VALID_HOW_FOUND:
        como_se_entero = "OTRO"

    # Crea usuario
    user_result = db.execute_returning_id(
        "usp_usuario_crear", (email, password_hash, "COUPLE")
    )
    id_usuario = user_result["id_usuario"]

    # Crea perfil de novios
    novios_result = db.execute_returning_id(
        "usp_novios_crear",
        (id_usuario, nombre_novio, nombre_novia,
         tipo_doc_novio, tipo_doc_novia,
         documento_novio, documento_novia,
         telefono, como_se_entero)
    )
    id_novios = novios_result["id_novios"]

    token = auth.sign_token(id_usuario, "COUPLE")
    return responses.created({
        "token": token,
        "usuario": {"id_usuario": id_usuario, "email": email, "rol": "COUPLE"},
        "perfil": {
            "id_novios": id_novios,
            "nombre_novio": nombre_novio,
            "nombre_novia": nombre_novia,
            "telefono": telefono,
        },
    })


def _registrar_planner(body, email, password_hash):
    nombre = (body.get("nombre") or "").strip()
    empresa = (body.get("empresa") or "").strip() or None
    telefono = (body.get("telefono") or "").strip()

    if not nombre or not telefono:
        return responses.bad_request("Nombre y telefono son requeridos")

    user_result = db.execute_returning_id(
        "usp_usuario_crear", (email, password_hash, "WEDDING_PLANNER")
    )
    id_usuario = user_result["id_usuario"]

    planner_result = db.execute_returning_id(
        "usp_planner_crear", (id_usuario, nombre, empresa, telefono)
    )
    id_planner = planner_result["id_planner"]

    token = auth.sign_token(id_usuario, "WEDDING_PLANNER")
    return responses.created({
        "token": token,
        "usuario": {"id_usuario": id_usuario, "email": email, "rol": "WEDDING_PLANNER"},
        "perfil": {
            "id_planner": id_planner,
            "nombre": nombre,
            "empresa": empresa,
            "telefono": telefono,
        },
    })
