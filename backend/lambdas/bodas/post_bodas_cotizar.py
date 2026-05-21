"""
post_bodas_cotizar.py
POST /v1/bodas/cotizar
Header: Authorization: Bearer <token>

Body:
  {
    "latitud":     -12.05,
    "longitud":    -77.03,
    "fechaBoda":   "2026-08-15",      // formato YYYY-MM-DD
    "horaBoda":    "16:00",            // formato HH:MM (24h)
    "instrumentos": ["piano","voz_femenina","violin_1"]
  }

Devuelve el desglose completo de precio (base + instrumentos + movilidad +
total) SIN requerir una boda creada. Usado por la app movil en la pantalla
"Crear evento" para mostrar el costo en vivo antes de comprometerse.

La logica de calculo es identica a GET /bodas/{id}/precio: se delega al
modulo shared/pricing.py para evitar duplicacion. La diferencia es que
este endpoint recibe los parametros directamente en el body, sin tocar BD.
"""

from datetime import datetime
from shared import auth
from shared import responses
from shared import pricing


def handle_post_bodas_cotizar(event, context):
    try:
        auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    try:
        body = responses.parse_body(event)
    except ValueError as e:
        return responses.bad_request(str(e))

    # Parsear y validar coordenadas
    lat = body.get("latitud")
    lng = body.get("longitud")
    if lat is None or lng is None:
        return responses.bad_request("latitud y longitud son requeridos")
    try:
        lat = float(lat)
        lng = float(lng)
    except (TypeError, ValueError):
        return responses.bad_request("latitud y longitud deben ser numericos")

    # Parsear fecha + hora para construir departure_time. El tiempo
    # predictivo de trafico depende del momento de la boda, por eso lo
    # necesitamos. Si fecha/hora no vienen, departure_time = None y el
    # backend cae a Haversine sin trafico.
    fecha_str = body.get("fechaBoda")
    hora_str = body.get("horaBoda")
    departure_time = None
    if fecha_str and hora_str:
        try:
            departure_time = datetime.strptime(
                f"{fecha_str} {hora_str}", "%Y-%m-%d %H:%M"
            )
        except ValueError:
            return responses.bad_request(
                "fechaBoda debe ser YYYY-MM-DD y horaBoda debe ser HH:MM"
            )

    # Lista de instrumentos. Aceptamos vacia: el novio puede cotizar
    # antes de elegir instrumentos para ver el costo base + movilidad.
    instrumentos = body.get("instrumentos") or []
    if not isinstance(instrumentos, list):
        return responses.bad_request("instrumentos debe ser una lista de slugs")

    # Delegacion al motor de pricing centralizado
    try:
        breakdown = pricing.calculate_price_breakdown(
            venue_lat=lat,
            venue_lng=lng,
            instrument_slugs=instrumentos,
            departure_time=departure_time,
        )
    except Exception as e:
        return responses.server_error(f"Error al calcular precio: {e}")

    return responses.ok(breakdown)
