"""
shared/db.py
Conexion a SQL Server via pyodbc para AWS Lambda.

Sigue el patron del laboratorio del profesor (pyodbc + ODBC Driver 18) pero
LEE las credenciales de variables de entorno en lugar de hardcodear el connection
string en cada Lambda. Las env vars se configuran en la consola de Lambda
y son sustancialmente mas seguras y mantenibles.

Variables de entorno requeridas:
  DB_SERVER    -> endpoint RDS, ej: pacem-db.xxx.us-east-1.rds.amazonaws.com
  DB_DATABASE  -> nombre de la BD, ej: pacem_deus_bodas
  DB_UID       -> usuario, ej: admin
  DB_PWD       -> password (idealmente desde Secrets Manager, no env var)

La conexion se cachea entre invocaciones del mismo Lambda container (warm starts).
"""

import os
import struct
from datetime import datetime, timedelta, timezone

import pyodbc

# Conexion global para warm starts. Si se cierra, se vuelve a abrir.
_connection = None

# Tipo SQL Server DATETIMEOFFSET. pyodbc no lo soporta nativamente; tenemos
# que registrar un converter manualmente. Si no lo hacemos, cualquier SELECT
# que devuelva una columna de este tipo (ej. fecha_creacion, fecha_actualizacion)
# truena con: 'ODBC SQL type -155 is not yet supported'.
_SQL_TYPE_DATETIMEOFFSET = -155


def _decode_datetimeoffset(raw_bytes):
    """
    Convierte el binario de DATETIMEOFFSET de SQL Server (20 bytes) a un
    datetime de Python con tzinfo.

    Layout binario little-endian:
      year(2) month(2) day(2) hour(2) minute(2) second(2)
      nanoseconds(4) tz_hour_offset(2) tz_minute_offset(2)
    """
    parts = struct.unpack("<6hI2h", raw_bytes)
    year, month, day, hour, minute, second = parts[0:6]
    nanoseconds = parts[6]
    tz_hours, tz_minutes = parts[7], parts[8]
    return datetime(
        year, month, day, hour, minute, second,
        nanoseconds // 1000,  # micro: nano / 1000
        timezone(timedelta(hours=tz_hours, minutes=tz_minutes)),
    )


def _build_connection_string():
    """Construye el connection string desde variables de entorno."""
    server = os.environ["DB_SERVER"]
    database = os.environ["DB_DATABASE"]
    uid = os.environ["DB_UID"]
    pwd = os.environ["DB_PWD"]
    return (
        "Driver={ODBC Driver 18 for SQL Server};"
        f"Server={server};"
        f"Database={database};"
        f"UID={uid};"
        f"PWD={pwd};"
        "Encrypt=yes;"
        "TrustServerCertificate=yes;"
        "Connection Timeout=10;"
    )


def _open_connection():
    """
    Abre una conexion nueva y registra el output converter para
    DATETIMEOFFSET. Hay que registrarlo en CADA conexion nueva (no es
    una config global de pyodbc).

    autocommit=True: cada EXEC commitea al terminar. Sin esto, los SPs
    que hacen INSERT/UPDATE/DELETE + SELECT (como usp_boda_foto_agregar)
    dejaban la transaccion abierta porque fetch_one/fetch_all consumen
    el resultset sin llamar conn.commit(). Cuando llegaba un segundo
    INSERT a la misma tabla en otra invocacion warm, se bloqueaba
    esperando el lock de la transaccion huerfana hasta morir por timeout
    de Lambda a los 30s.
    """
    conn = pyodbc.connect(_build_connection_string(), autocommit=True)
    conn.add_output_converter(_SQL_TYPE_DATETIMEOFFSET, _decode_datetimeoffset)
    return conn


def get_connection():
    """Devuelve una conexion abierta a SQL Server. Reutiliza entre warm starts."""
    global _connection
    if _connection is None:
        _connection = _open_connection()
    else:
        # Verifica si la conexion sigue viva
        try:
            _connection.cursor().execute("SELECT 1")
        except pyodbc.Error:
            _connection = _open_connection()
    return _connection


def fetch_one(sp_name, params=()):
    """
    Ejecuta un stored procedure y devuelve la primera fila como dict.
    Las claves son los nombres de las columnas devueltas por el SP.
    """
    conn = get_connection()
    cursor = conn.cursor()
    placeholders = ",".join(["?"] * len(params)) if params else ""
    sql = f"EXEC {sp_name} {placeholders}".strip()
    cursor.execute(sql, params)
    row = cursor.fetchone()
    if not row:
        cursor.close()
        return None
    columns = [c[0] for c in cursor.description]
    result = dict(zip(columns, row))
    cursor.close()
    return result


def fetch_all(sp_name, params=()):
    """Ejecuta un SP y devuelve todas las filas como lista de dicts."""
    conn = get_connection()
    cursor = conn.cursor()
    placeholders = ",".join(["?"] * len(params)) if params else ""
    sql = f"EXEC {sp_name} {placeholders}".strip()
    cursor.execute(sql, params)
    rows = cursor.fetchall()
    columns = [c[0] for c in cursor.description] if cursor.description else []
    result = [dict(zip(columns, r)) for r in rows]
    cursor.close()
    return result


def execute(sp_name, params=()):
    """
    Ejecuta un SP que no devuelve resultset (UPDATE/DELETE puro).
    Con autocommit=True el commit es implicito al terminar EXEC; no
    llamamos conn.commit() (no-op) ni conn.rollback() (lanza excepcion
    porque no hay transaccion abierta y enmascara el error original).
    """
    conn = get_connection()
    cursor = conn.cursor()
    placeholders = ",".join(["?"] * len(params)) if params else ""
    sql = f"EXEC {sp_name} {placeholders}".strip()
    try:
        cursor.execute(sql, params)
    finally:
        cursor.close()


def execute_returning_id(sp_name, params=()):
    """
    Ejecuta un SP que hace INSERT y devuelve SCOPE_IDENTITY() como dict.
    Con autocommit=True el commit es implicito.
    """
    conn = get_connection()
    cursor = conn.cursor()
    placeholders = ",".join(["?"] * len(params)) if params else ""
    sql = f"EXEC {sp_name} {placeholders}".strip()
    try:
        cursor.execute(sql, params)
        row = cursor.fetchone()
        columns = [c[0] for c in cursor.description] if cursor.description else []
        result = dict(zip(columns, row)) if row else {}
    finally:
        cursor.close()
    return result
