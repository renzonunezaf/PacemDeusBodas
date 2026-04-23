# ═══════════════════════════════════════════════════════════════
# Pacem Deus Bodas — Servidor de Desarrollo Local
# IS276 — Plataformas Móviles y Análisis Cloud — Grupo 2
# ═══════════════════════════════════════════════════════════════
# Simula API Gateway + Lambda localmente usando Flask.
# Permite probar todos los endpoints sin desplegar a AWS.
#
# Uso:
#   1. Instalar dependencias:    pip install -r requirements.txt
#   2. Copiar .env.example a .env y completar credenciales locales
#   3. Crear la BD en PostgreSQL, ejecutar schema.sql y seed.py (ver README)
#   4. python server.py
#   5. API disponible en http://localhost:5000
#
# Desde el emulador Android, usar: http://10.0.2.2:5000
# ═══════════════════════════════════════════════════════════════

import json
import os
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv

# ─── CARGA DE VARIABLES DE ENTORNO ─────────────────────────────
# Busca un archivo .env en pacem-deus-api/ y lo inyecta en os.environ.
# Si la variable ya está definida en el sistema, no se sobrescribe.
# En producción (AWS Lambda) las variables se inyectan vía Environment Variables.
load_dotenv()

# Importar handlers de las funciones Lambda (deben ir después de load_dotenv)
from functions.auth import handler as auth_handler
from functions.weddings import handler as weddings_handler
from functions.catalog import handler as catalog_handler
from functions.setlist import handler as setlist_handler
from functions.planner import handler as planner_handler

app = Flask(__name__)
CORS(app)


def build_event(resource, method="GET", path_params=None, body=None):
    """
    Construye un evento simulando lo que API Gateway envía a Lambda.
    Traduce la request de Flask al formato que esperan nuestras funciones.
    """
    return {
        "httpMethod": method,
        "resource": resource,
        "headers": dict(request.headers),
        "pathParameters": path_params,
        "queryStringParameters": dict(request.args) if request.args else None,
        "body": json.dumps(body) if body else request.get_data(as_text=True) or None,
    }


def lambda_response(result):
    """Convierte la respuesta Lambda al formato Flask."""
    body = json.loads(result.get("body", "{}"))
    status = result.get("statusCode", 200)
    return jsonify(body), status


# ═══════════════════════════════════════════════════════════════
# RUTAS — Replican exactamente los recursos de API Gateway
# ═══════════════════════════════════════════════════════════════

# ─── AUTH ────────────────────────────────────────────────────

@app.route("/auth/login", methods=["POST", "OPTIONS"])
def auth_login():
    event = build_event("/auth/login", "POST", body=request.get_json(silent=True))
    return lambda_response(auth_handler(event, None))


@app.route("/auth/register", methods=["POST", "OPTIONS"])
def auth_register():
    event = build_event("/auth/register", "POST", body=request.get_json(silent=True))
    return lambda_response(auth_handler(event, None))


@app.route("/auth/me", methods=["GET", "OPTIONS"])
def auth_me():
    event = build_event("/auth/me", "GET")
    return lambda_response(auth_handler(event, None))


# ─── WEDDINGS ────────────────────────────────────────────────

@app.route("/weddings", methods=["GET", "POST", "OPTIONS"])
def weddings_list_or_create():
    """GET → lista eventos. POST → el couple crea su evento."""
    if request.method == "POST":
        event = build_event("/weddings", "POST", body=request.get_json(silent=True))
    else:
        event = build_event("/weddings", "GET")
    return lambda_response(weddings_handler(event, None))


@app.route("/weddings/<id>", methods=["GET", "PATCH", "OPTIONS"])
def weddings_detail(id):
    """GET → detalle. PATCH → editar datos (couple en DRAFT)."""
    if request.method == "PATCH":
        event = build_event("/weddings/{id}", "PATCH", {"id": id}, request.get_json(silent=True))
    else:
        event = build_event("/weddings/{id}", "GET", {"id": id})
    return lambda_response(weddings_handler(event, None))


@app.route("/weddings/<id>/submit", methods=["POST", "OPTIONS"])
def weddings_submit(id):
    """El couple envía su ensamble al coro (DRAFT → SUBMITTED)."""
    event = build_event("/weddings/{id}/submit", "POST", {"id": id}, request.get_json(silent=True))
    return lambda_response(weddings_handler(event, None))


@app.route("/weddings/<id>/cancel", methods=["POST", "OPTIONS"])
def weddings_cancel(id):
    """El couple o admin solicita cancelación del evento."""
    event = build_event("/weddings/{id}/cancel", "POST", {"id": id}, request.get_json(silent=True))
    return lambda_response(weddings_handler(event, None))


@app.route("/weddings/<id>/approve", methods=["POST", "OPTIONS"])
def weddings_approve(id):
    event = build_event("/weddings/{id}/approve", "POST", {"id": id}, request.get_json(silent=True))
    return lambda_response(weddings_handler(event, None))


@app.route("/weddings/<id>/photo", methods=["POST", "OPTIONS"])
def weddings_photo(id):
    event = build_event("/weddings/{id}/photo", "POST", {"id": id}, request.get_json(silent=True))
    return lambda_response(weddings_handler(event, None))


@app.route("/weddings/<id>/planner", methods=["PUT", "OPTIONS"])
def weddings_planner(id):
    event = build_event("/weddings/{id}/planner", "PUT", {"id": id}, request.get_json(silent=True))
    return lambda_response(weddings_handler(event, None))


@app.route("/weddings/<id>/instruments", methods=["POST", "OPTIONS"])
def weddings_instruments(id):
    """Actualizar los instrumentos contratados para el evento."""
    event = build_event("/weddings/{id}/instruments", "POST", {"id": id}, request.get_json(silent=True))
    return lambda_response(weddings_handler(event, None))


@app.route("/weddings/<id>/contract", methods=["GET", "OPTIONS"])
def weddings_contract(id):
    """Preview de datos del contrato (para pantalla Android)."""
    event = build_event("/weddings/{id}/contract", "GET", {"id": id})
    return lambda_response(weddings_handler(event, None))


@app.route("/wedding-planners", methods=["GET", "OPTIONS"])
def wedding_planners_list():
    event = build_event("/wedding-planners", "GET")
    return lambda_response(weddings_handler(event, None))


# ─── CATALOG ─────────────────────────────────────────────────

@app.route("/moments", methods=["GET", "OPTIONS"])
def moments():
    event = build_event("/moments", "GET")
    return lambda_response(catalog_handler(event, None))


@app.route("/songs", methods=["GET", "OPTIONS"])
def songs():
    event = build_event("/songs", "GET")
    return lambda_response(catalog_handler(event, None))


@app.route("/instruments", methods=["GET", "OPTIONS"])
def instruments():
    event = build_event("/instruments", "GET")
    return lambda_response(catalog_handler(event, None))


# ─── SETLIST ─────────────────────────────────────────────────

@app.route("/weddings/<id>/setlist", methods=["GET", "OPTIONS"])
def setlist_get(id):
    event = build_event("/weddings/{id}/setlist", "GET", {"id": id})
    return lambda_response(setlist_handler(event, None))


@app.route("/weddings/<id>/setlist", methods=["POST"])
def setlist_add(id):
    event = build_event("/weddings/{id}/setlist", "POST", {"id": id}, request.get_json(silent=True))
    return lambda_response(setlist_handler(event, None))


@app.route("/weddings/<id>/setlist/<itemId>", methods=["DELETE", "OPTIONS"])
def setlist_remove(id, itemId):
    event = build_event("/weddings/{id}/setlist/{itemId}", "DELETE", {"id": id, "itemId": itemId})
    return lambda_response(setlist_handler(event, None))


# ─── PLANNER ─────────────────────────────────────────────────

@app.route("/planner/weddings", methods=["GET", "OPTIONS"])
def planner_weddings():
    event = build_event("/planner/weddings", "GET")
    return lambda_response(planner_handler(event, None))


# ═══════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("═══════════════════════════════════════════════════════")
    print("  Pacem Deus Bodas — API REST (desarrollo local)")
    print("  http://localhost:5000")
    print("  Desde emulador Android: http://10.0.2.2:5000")
    print("═══════════════════════════════════════════════════════")
    print()
    print("  Endpoints disponibles:")
    print("  POST   /auth/login")
    print("  POST   /auth/register")
    print("  GET    /auth/me")
    print("  GET    /weddings")
    print("  POST   /weddings                       (couple crea evento)")
    print("  GET    /weddings/{id}")
    print("  PATCH  /weddings/{id}                  (editar)")
    print("  POST   /weddings/{id}/submit           (enviar al coro)")
    print("  POST   /weddings/{id}/cancel           (cancelar)")
    print("  POST   /weddings/{id}/approve          (admin)")
    print("  POST   /weddings/{id}/photo")
    print("  PUT    /weddings/{id}/planner")
    print("  POST   /weddings/{id}/instruments      (elegir instrumentos)")
    print("  GET    /weddings/{id}/contract         (datos del contrato)")
    print("  GET    /wedding-planners")
    print("  GET    /moments")
    print("  GET    /songs?momentId=xxx")
    print("  GET    /instruments")
    print("  GET    /weddings/{id}/setlist")
    print("  POST   /weddings/{id}/setlist")
    print("  DELETE /weddings/{id}/setlist/{itemId}")
    print("  GET    /planner/weddings")
    print()
    print("  Contraseña de prueba: PacemDeus2026!")
    print("═══════════════════════════════════════════════════════")

    app.run(host="0.0.0.0", port=5000, debug=True)
