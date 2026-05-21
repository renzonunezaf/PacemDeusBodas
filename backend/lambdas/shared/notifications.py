"""
shared/notifications.py
Helper para crear notificaciones desde cualquier handler.

Hace doble disparo:
  1. Insert en tabla `notificacion` (historico permanente, leido via
     polling como fallback)
  2. Push FCM via shared/push.py (entrega inmediata)

Si el push falla por cualquier motivo, la notificacion sigue persistida
en BD y la app la agarra en el siguiente poll. Si la BD insert falla,
el push no se intenta (no tiene sentido notificar sin tener historico).
"""

from shared import db
from shared import push


def notify_admins(tipo, titulo, mensaje, id_boda=None):
    """
    Notifica a TODOS los admins activos:
      - inserta una fila en `notificacion` para cada uno
      - manda push FCM al device de cada uno (best effort)
    """
    admins = db.fetch_all("usp_admin_listar_ids", ())
    for admin in admins:
        try:
            db.execute(
                "usp_notificacion_crear",
                (admin["id_usuario"], tipo, titulo, mensaje, id_boda)
            )
        except Exception as e:
            print(f"notify_admins: error insertando para {admin['id_usuario']}: {e}")
            continue

    # Push best effort. Si Firebase esta caido el polling agarra
    # las novedades en 10s.
    try:
        data = {"tipo": tipo}
        if id_boda:
            data["id_boda"] = str(id_boda)
        push.send_to_admins(titulo, mensaje, data)
    except Exception as e:
        print(f"notify_admins: push fallo (continuando): {e}")


def notify_user(id_usuario, tipo, titulo, mensaje, id_boda=None):
    """Notifica a un usuario especifico (BD + FCM)."""
    db.execute(
        "usp_notificacion_crear",
        (id_usuario, tipo, titulo, mensaje, id_boda)
    )
    try:
        data = {"tipo": tipo}
        if id_boda:
            data["id_boda"] = str(id_boda)
        push.send_to_user(id_usuario, titulo, mensaje, data)
    except Exception as e:
        print(f"notify_user: push fallo (continuando): {e}")


def notify_couple(id_boda, tipo, titulo, mensaje):
    """
    Atajo: encuentra el id_usuario de la novia duena de la boda y le
    crea la notificacion. Si la boda no existe o no tiene novios,
    no hace nada (no rompe el flujo principal).
    """
    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda or not boda.get("id_novios"):
        return
    novios = db.fetch_one("usp_novios_obtener_por_id", (boda["id_novios"],))
    if not novios or not novios.get("id_usuario"):
        return
    notify_user(novios["id_usuario"], tipo, titulo, mensaje, id_boda)


def couple_label(id_novios):
    """
    Devuelve 'Novio y Novia' o 'Una pareja' si falta info.
    Util para componer mensajes que mencionan a la pareja al admin
    sin que cada handler tenga que armar el label localmente.
    """
    if not id_novios:
        return "Una pareja"
    n = db.fetch_one("usp_novios_obtener_por_id", (id_novios,))
    if not n:
        return "Una pareja"
    novio = (n.get("nombre_novio") or "").strip()
    novia = (n.get("nombre_novia") or "").strip()
    if novio and novia:
        return f"{novio} y {novia}"
    return novio or novia or "Una pareja"
