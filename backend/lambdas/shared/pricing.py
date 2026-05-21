"""
shared/pricing.py
Motor de calculo de precios. Llama al SP usp_pricing_obtener para leer la
configuracion y hace los calculos en Python.

REGLA: NADA esta hardcodeado. Cambiar precios -> editar tabla
configuracion_precios via PUT /v1/admin/pricing.

MODELO DE PRECIOS (checkpoint v02, confirmado por Renzo 2026-05-14):

  Precio base del paquete: S/.650 fijo (Lima).
  Precio por instrumento adicional: S/.150 fijo (Lima).
  Fuera de Lima: ambos precios aceleran linealmente con la distancia
  via surge factor (entre radio_lima_km y radio_lima_km + rango, hasta
  un tope plateau).

  Movilidad (cambio mayor respecto a v01):
    - 0 a mov_distancia_libre_km (20km): movilidad = S/.0
      (cada musico va por su lado, no se alquila vehiculo).
    - Entre 20 y radio_lima_km (50km): curva potencial t^exp
      arrancando en mov_arranque_movilidad (180) y llegando a
      mov_tope_movilidad (320) a los 50km.
    - >= 50km: plateau en mov_tope_movilidad (320).
    - Recargo por trafico: SOLO si km > 20. Se compara duracion
      con trafico vs duracion normal (de Google). Si la diferencia
      supera mov_traffic_umbral_min (10), los minutos excedentes
      pagan mov_traffic_tarifa_minuto (3.15) c/u.
    - Recargo XL: si pasajeros > 4, movilidad * 1.20.
    - Recargo Centro Historico: +S/.100 SIEMPRE que la boda este
      dentro del bbox del centro, independiente de la distancia
      (es prima de inconveniencia, no de movilidad).
"""

import math
from . import db
from . import distance


def get_pricing_config():
    """Carga la configuracion via stored procedure."""
    cfg = db.fetch_one("usp_pricing_obtener")
    if not cfg:
        raise RuntimeError(
            "configuracion_precios no inicializada. Ejecuta 03_seed_pricing.sql."
        )
    return cfg


def calculate_surge_factor(distance_km, config):
    """
    Acelerador del precio base e instrumentos fuera de Lima.
    Lineal entre radio_lima_km y radio_lima_km + rango.
    """
    radio = float(config["radio_lima_km"])
    rango = float(config["surge_fuera_lima_rango_km"])
    factor_max = float(config["surge_fuera_lima_factor_max"])

    if distance_km <= radio:
        return 1.0
    over = distance_km - radio
    if over >= rango:
        return 1.0 + factor_max
    return 1.0 + factor_max * (over / rango)


def calculate_mobility_distance(distance_km, config):
    """
    Componente de movilidad por distancia.

    Curva potencial entre mov_distancia_libre y radio_lima, con tope
    plateau:
      d <= libre      -> 0     (no se alquila movilidad, cada uno va por su lado)
      libre < d < radio -> arranque + (tope - arranque) * t^exp
      d >= radio      -> tope  (plateau)

    Con valores actuales (libre=20, radio=50, arranque=180, tope=320,
    exp=1.5):
      20km -> 180
      35km -> 230
      45km -> 290
      50km -> 320
      cualquier > 50 -> 320
    """
    libre = float(config["mov_distancia_libre_km"])
    radio = float(config["radio_lima_km"])
    arranque = float(config["mov_arranque_movilidad"])
    tope = float(config["mov_tope_movilidad"])
    exp = float(config["mov_curva_exponente"])

    if distance_km <= libre:
        return 0.0
    if distance_km >= radio:
        return tope
    rango = radio - libre
    t = (distance_km - libre) / rango
    return arranque + (tope - arranque) * (t ** exp)


def calculate_traffic_surcharge(distance_km, duration_normal, duration_traffic, config):
    """
    Recargo por trafico. Solo aplica cuando hay movilidad alquilada
    (km > mov_distancia_libre) Y la diferencia entre tiempo con
    trafico y tiempo normal supera el umbral de tolerancia.

    El recargo se cobra solo sobre los minutos QUE EXCEDEN el umbral:
      diff = duration_traffic - duration_normal
      if diff > umbral: (diff - umbral) * tarifa
    """
    libre = float(config["mov_distancia_libre_km"])

    # Sin movilidad alquilada (distancias cortas), no hay recargo de trafico
    if distance_km <= libre:
        return 0.0

    # Sin dato de trafico de Google (boda en el pasado, ruta sin datos),
    # no cobramos recargo
    if duration_traffic <= 0:
        return 0.0

    umbral = float(config["mov_traffic_umbral_min"])
    tarifa = float(config["mov_traffic_tarifa_minuto"])

    diff = duration_traffic - duration_normal
    if diff <= umbral:
        return 0.0
    excess = diff - umbral
    return excess * tarifa


def calculate_price_breakdown(venue_lat, venue_lng, instrument_slugs, departure_time=None):
    """
    Calcula el desglose completo de precios.

    Args:
        venue_lat, venue_lng: coordenadas (pueden ser None)
        instrument_slugs: lista de slugs de instrumentos elegidos
        departure_time: datetime de la boda (para que Google calcule trafico)
    """
    config = get_pricing_config()

    # 1. Distancia y duraciones
    distance_km = 0.0
    duration_minutes = 0
    duration_traffic = 0
    outside_lima = False

    if venue_lat is not None and venue_lng is not None:
        result = distance.get_distance(
            float(config["latitud_base"]),
            float(config["longitud_base"]),
            float(venue_lat),
            float(venue_lng),
            departure_time,
        )
        distance_km = result["distance_km"]
        duration_minutes = result["duration_minutes"]
        duration_traffic = result["duration_in_traffic_minutes"]
        outside_lima = distance_km > float(config["radio_lima_km"])

    # 2. Surge factor y precios base/instrumento
    surge = calculate_surge_factor(distance_km, config)
    base_unit = float(config["precio_paquete_base"])
    instrument_unit = float(config["precio_instrumento_adicional"])

    base_price = round(base_unit * surge, 2)
    price_per_instrument = round(instrument_unit * surge, 2)

    # 3. Instrumentos: catalogo y discriminacion de incluidos vs facturables
    instruments_detail = []
    if instrument_slugs:
        placeholders = ",".join(["?"] * len(instrument_slugs))
        conn = db.get_connection()
        cursor = conn.cursor()
        cursor.execute(
            f"SELECT slug, nombre, incluido_en_paquete_base "
            f"FROM instrumento "
            f"WHERE slug IN ({placeholders}) AND activo = 1 "
            f"ORDER BY orden ASC",
            tuple(instrument_slugs)
        )
        for row in cursor.fetchall():
            slug = row[0]
            nombre = row[1]
            incluido = bool(row[2])
            instruments_detail.append({
                "slug": slug,
                "nombre": nombre,
                "precio": 0.0 if incluido else price_per_instrument,
                "incluido_en_base": incluido,
            })
        cursor.close()

    facturables = [i for i in instruments_detail if not i["incluido_en_base"]]
    instruments_price = len(facturables) * price_per_instrument

    # 4. Movilidad (nuevo modelo v02):
    #    componente por distancia + recargo por trafico (si aplica)
    mov_distance = calculate_mobility_distance(distance_km, config)
    mov_traffic = calculate_traffic_surcharge(
        distance_km, duration_minutes, duration_traffic, config
    )
    mobility_price = mov_distance + mov_traffic

    # 5. Recargo XL: aplica si el grupo es mas grande de lo que cabe en
    #    una movilidad estandar. xl_umbral_pasajeros = 4 musicos
    #    (movilidad standard 4 pax + chofer). 5to musico en adelante
    #    obliga a XL.
    #
    #    NO se suma director adicional: el director suele ser uno de los
    #    instrumentistas (ej. el pianista o la directora del coro
    #    canta a la vez), no persona aparte. Si en el futuro se separa
    #    el rol y siempre viaja, agregar +1 aqui.
    #
    #    Ademas: el surge XL solo aplica si hay movilidad efectiva. Si
    #    la boda esta dentro del rango sin movilidad (km <= libre), el
    #    grupo XL no afecta el costo y por lo tanto NO se reporta como
    #    grupo_xl en la respuesta (evita badge "XL +20%" sin contexto).
    xl_threshold = int(config["movilidad_xl_umbral_pasajeros"])
    xl_factor = float(config["movilidad_xl_factor"])
    musicos = len(instruments_detail)
    grupo_es_xl = musicos > xl_threshold
    grupo_xl_aplica = grupo_es_xl and mobility_price > 0
    if grupo_xl_aplica:
        mobility_price *= xl_factor

    # 6. Recargo Centro Historico: cargo fijo, SIEMPRE que la boda este
    #    dentro del bbox, independiente de la distancia (es prima de
    #    inconveniencia por trabajar en el centro).
    is_centro = False
    if venue_lat is not None and venue_lng is not None:
        is_centro = distance.is_in_centro_historico(
            float(venue_lat),
            float(venue_lng),
            float(config["centro_norte_lat"]),
            float(config["centro_sur_lat"]),
            float(config["centro_oeste_lng"]),
            float(config["centro_este_lng"]),
        )
        if is_centro:
            mobility_price += float(config["movilidad_centro_historico"])

    # 7. Redondeo final de la movilidad a multiplo de S/.10
    mobility_price = math.ceil(mobility_price / 10) * 10

    total_price = base_price + instruments_price + mobility_price

    return {
        "precio_base": base_price,
        "precio_instrumentos": instruments_price,
        "instrumentos_detalle": instruments_detail,
        "precio_movilidad": mobility_price,
        "precio_total": total_price,
        "fuera_de_lima": outside_lima,
        "centro_historico": is_centro,
        "grupo_xl": grupo_xl_aplica,
        "pasajeros": musicos,
        "surge_factor": surge,
        "distancia_km": distance_km,
        "duracion_minutos": duration_minutes,
        "duracion_con_trafico": duration_traffic,
        "movilidad_distancia": round(mov_distance, 2),
        "movilidad_trafico": round(mov_traffic, 2),
    }
