"""
shared/distance.py
Calculo de distancia entre dos puntos geograficos.

Estrategia:
  1. Intenta Google Distance Matrix Advanced (distancia real por carretera + tiempo
     proyectado con trafico segun la fecha del evento).
  2. Si no hay GOOGLE_MAPS_API_KEY o Google falla, cae a Haversine (linea recta)
     con factor de correccion 1.3 para aproximar la distancia por carretera.

Variable de entorno opcional:
  GOOGLE_MAPS_API_KEY  -> habilita Distance Matrix
"""

import os
import math
import logging
import urllib.parse
import urllib.request
import json
from datetime import datetime, timedelta, timezone

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

# Calibrado: las carreteras suelen ser ~1.3x la linea recta en Lima
HAVERSINE_ROAD_FACTOR = 1.3


def haversine_km(lat1, lng1, lat2, lng2):
    """Distancia en linea recta entre dos coordenadas."""
    earth_radius = 6371.0
    d_lat = math.radians(lat2 - lat1)
    d_lng = math.radians(lng2 - lng1)
    a = (
        math.sin(d_lat / 2) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(d_lng / 2) ** 2
    )
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return earth_radius * c


def google_distance(origin_lat, origin_lng, dest_lat, dest_lng, departure_time=None):
    """Llama a Google Distance Matrix. Devuelve dict o None si falla."""
    api_key = os.environ.get("GOOGLE_MAPS_API_KEY")
    if not api_key:
        return None

    params = {
        "origins": f"{origin_lat},{origin_lng}",
        "destinations": f"{dest_lat},{dest_lng}",
        "mode": "driving",
        "language": "es",
        "key": api_key,
    }

    if departure_time:
        # Defensive: si nos llega naive (sin tzinfo), asumimos hora local
        # de Peru (UTC-5, sin DST). Esto previene crashes al comparar con
        # datetime.now(timezone.utc) que es aware. Los callers normalmente
        # construyen el departure con datetime.strptime() que es naive.
        if departure_time.tzinfo is None:
            peru_tz = timezone(timedelta(hours=-5))
            departure_time = departure_time.replace(tzinfo=peru_tz)

        departure = departure_time - timedelta(hours=2)
        if departure > datetime.now(timezone.utc):
            params["departure_time"] = str(int(departure.timestamp()))
            params["traffic_model"] = "best_guess"

    url = "https://maps.googleapis.com/maps/api/distancematrix/json?" + urllib.parse.urlencode(params)

    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            data = json.loads(response.read().decode("utf-8"))
    except Exception as e:
        logger.warning("Google Distance Matrix fallo: %s", e)
        return None

    if data.get("status") != "OK":
        return None

    rows = data.get("rows") or []
    if not rows:
        return None
    elements = rows[0].get("elements") or []
    if not elements or elements[0].get("status") != "OK":
        return None

    el = elements[0]
    return {
        "distance_km": round(el["distance"]["value"] / 1000, 1),
        "duration_minutes": round(el["duration"]["value"] / 60),
        "duration_in_traffic_minutes": round((el.get("duration_in_traffic") or {}).get("value", 0) / 60),
        "source": "google",
    }


def get_distance(base_lat, base_lng, dest_lat, dest_lng, departure_time=None):
    """Distancia entre dos puntos. Google primero, Haversine como fallback."""
    google_result = google_distance(base_lat, base_lng, dest_lat, dest_lng, departure_time)
    if google_result:
        return google_result

    straight_km = haversine_km(base_lat, base_lng, dest_lat, dest_lng)
    road_km = round(straight_km * HAVERSINE_ROAD_FACTOR, 1)
    estimated_duration = round(road_km * 2)

    return {
        "distance_km": road_km,
        "duration_minutes": estimated_duration,
        "duration_in_traffic_minutes": 0,
        "source": "haversine",
    }


def is_in_centro_historico(lat, lng, north_lat, south_lat, west_lng, east_lng):
    """Detecta si esta dentro del bounding box del Centro Historico."""
    return south_lat <= lat <= north_lat and west_lng <= lng <= east_lng
