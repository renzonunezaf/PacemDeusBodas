"""
get_disponibilidad_mes.py
GET /v1/disponibilidad/{anio}/{mes}
Header: Authorization: Bearer <token>
Query param opcional: ?excluir_boda=NN  (para no contar la propia al editar)

Devuelve por dia del mes:
  - lista de bodas bloqueantes (todo excepto DRAFT)
  - estado del dia ('free' | 'partial' | 'full') para colorear el calendario

Los dias con bodas se pintan ambar (partial) si todavia hay al menos
una hora candidata en 12-20 con gap valido respecto a TODAS las bodas
existentes; o rojo (full) si ninguna hora cumple gap.

Aplica a ambos modos (novia y admin): los dos modos ven el mismo
semaforo de color. La diferencia es que admin tambien ve los DRAFT
en la lista (la novia no), pero los DRAFT no afectan el color
porque no comprometen la fecha.
"""

from shared import db
from shared import auth
from shared import responses


HORA_MIN = 12
HORA_MAX = 20
# Gap defensivo minimo (4h). El gap real lo valida el SP al confirmar
# fecha+hora — puede ser 4h o 6h segun la distancia al venue. Aqui usamos
# el minimo defensivo para que el picker no se sobre-bloquee. Si la novia
# elige una hora que el SP termina rechazando por distancia, el flujo
# de submit le devuelve el error y vuelve a elegir.
GAP_HORAS = 4


def handle_get_disponibilidad_mes(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    anio = responses.parse_int_param(responses.get_path_param(event, "anio"))
    mes  = responses.parse_int_param(responses.get_path_param(event, "mes"))
    if not anio or not mes or mes < 1 or mes > 12:
        return responses.bad_request("anio y mes son requeridos (mes 1-12)")

    # excluir_boda permite que la novia editando su propia boda no
    # se vea bloqueada por ella misma.
    qs = event.get("queryStringParameters") or {}
    excluir = None
    if qs.get("excluir_boda"):
        try:
            excluir = int(qs["excluir_boda"])
        except (TypeError, ValueError):
            excluir = None

    # modo determina que bodas devuelve el SP:
    #   'novia' (default): bodas bloqueantes (todo excepto DRAFT).
    #   'admin': TODO incluyendo DRAFT y CANCELLATION_REQUESTED.
    # Si el rol es ADMIN/WEDDING_PLANNER, asumimos 'admin' salvo override.
    if payload.get("rol") in ("ADMIN", "WEDDING_PLANNER"):
        modo = qs.get("modo", "admin")
    else:
        modo = "novia"

    bodas = db.fetch_all("usp_disponibilidad_mes", (anio, mes, excluir, modo))

    # Agrupar por dia
    por_dia = {}
    for b in bodas:
        fecha = b["fecha"]
        if fecha not in por_dia:
            por_dia[fecha] = []
        hora_str = _format_hora(b["hora_boda"])
        groom = (b.get("nombre_novio") or "").strip()
        bride = (b.get("nombre_novia") or "").strip()
        pareja = (
            f"{groom} y {bride}" if groom and bride else (groom or bride or "—")
        )
        estado_boda = (b.get("estado") or "").strip()
        por_dia[fecha].append({
            "id_boda": b["id_boda"],
            "hora":    hora_str,
            "pareja":  pareja,
            "estado":  estado_boda,
        })

    # Color del dia: mismo calculo para ambos modos.
    dias_payload = []
    for fecha, lista in sorted(por_dia.items()):
        dias_payload.append({
            "fecha":  fecha,
            "estado": _calcular_estado_color(lista),
            "bodas":  lista,
        })

    return responses.ok({
        "anio": anio,
        "mes":  mes,
        "dias_con_bodas": dias_payload,
    })


def _format_hora(value):
    """Acepta timedelta (pyodbc) o str. Devuelve 'HH:MM'."""
    if value is None:
        return ""
    s = str(value)
    if len(s) >= 5:
        return s[:5]  # '14:00:00' -> '14:00'
    return s


def _calcular_estado_color(bodas_del_dia):
    """
    Color del dia para el calendario:
      'free'    -> sin bodas bloqueantes en el dia.
      'partial' -> hay bloqueantes pero queda al menos una hora candidata
                   en [HORA_MIN, HORA_MAX] con gap >= GAP_HORAS de TODAS
                   las existentes.
      'full'    -> hay bloqueantes y ninguna hora candidata cumple el gap.

    BLOQUEANTES = todos los estados excepto DRAFT.
    Se incluye CANCELLATION_REQUESTED conservadoramente: si el admin niega
    la cancelacion, el slot queda reservado igual.

    Ejemplo: 3 bodas en un dia a las 12:00, 13:00 y 19:00.
      - Para que h sea candidata en [12, 20] necesita
        |h-12| >= 4 AND |h-13| >= 4 AND |h-19| >= 4.
      - h <= 8  cumple gap con 12 y 13, pero esta fuera del rango.
      - h = 20  cumple |20-12|=8, |20-13|=7, |20-19|=1 -> falla con 19.
      - Ninguna hora cumple -> 'full' (rojo).
    """
    bloqueantes = [
        b for b in bodas_del_dia
        if (b.get("estado") or "").strip() != "DRAFT"
    ]

    if not bloqueantes:
        return "free"

    # Parsear las horas de las bodas bloqueantes. Si alguna no parsea
    # (caso defensivo), asumimos 'full' para no permitir bookings sobre
    # un dia con datos sospechosos.
    horas_existentes = []
    for b in bloqueantes:
        try:
            h = int(b["hora"].split(":")[0])
            horas_existentes.append(h)
        except (ValueError, IndexError, KeyError, AttributeError, TypeError):
            return "full"

    # Buscar al menos una hora candidata con gap valido contra TODAS
    for h_cand in range(HORA_MIN, HORA_MAX + 1):
        if all(abs(h_cand - h_ex) >= GAP_HORAS for h_ex in horas_existentes):
            return "partial"
    return "full"
