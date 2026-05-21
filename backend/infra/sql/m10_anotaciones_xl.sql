-- =============================================================================
-- migracion_v10_anotaciones_xl.sql
-- Pacem Deus Bodas - migracion grande que introduce:
--   1. Nuevo estado de boda: RETURNED_WITH_NOTES
--   2. Tabla boda_anotacion para guardar devoluciones del coordinador
--   3. Reajuste de pricing:
--       - Baja 10% en tarifas de movilidad
--       - Nueva regla XL: si pasajeros > 4 -> movilidad x 1.20
--       - Elimina cargo "grupo grande" (lo reemplaza XL)
--   4. SP actualizado de validacion de conflicto con gap variable
--      segun distancia (4h si ambas bodas <=20km, 6h si alguna >20km)
--
-- Idempotente.
-- =============================================================================


-- =============================================================================
-- 1. NUEVO ESTADO RETURNED_WITH_NOTES
-- =============================================================================

-- Drop del CHECK viejo (encontramos su nombre dinamicamente)
DECLARE @check_name SYSNAME
SELECT  @check_name = name
FROM    sys.check_constraints
WHERE   parent_object_id = OBJECT_ID('boda')
  AND   definition LIKE '%estado%'

IF @check_name IS NOT NULL
BEGIN
    EXEC('ALTER TABLE boda DROP CONSTRAINT ' + @check_name)
END

ALTER TABLE boda ADD CONSTRAINT ck_boda_estado CHECK (estado IN (
    'DRAFT', 'SUBMITTED', 'APPROVED', 'CONTRACTED',
    'CANCELLATION_REQUESTED', 'COMPLETED', 'RETURNED_WITH_NOTES'
))
GO

PRINT 'OK: nuevo estado RETURNED_WITH_NOTES habilitado'


-- =============================================================================
-- 2. TABLA boda_anotacion
-- Cada vez que el coordinador devuelve una boda con cambios, guardamos
-- una fila con: nota libre, snapshot del estado antes de los cambios,
-- precio antes/despues. Sirve para que la novia vea el comparativo y
-- para revertir si rechaza.
-- =============================================================================
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'boda_anotacion')
BEGIN
    CREATE TABLE boda_anotacion
    (
        id_boda_anotacion   INT             PRIMARY KEY IDENTITY(1,1),
        id_boda             INT             NOT NULL,
        id_usuario_autor    INT             NOT NULL,
        nombre_autor        VARCHAR(256)    NOT NULL,
        texto_nota          VARCHAR(2000)   NOT NULL,
        -- Snapshot JSON del estado antes de los cambios. Incluye:
        --   { venue_name, venue_address, venue_lat, venue_lng,
        --     instrumentos: [slugs], setlist: [{slug_momento,id_cancion,orden}],
        --     precio_base, precio_instrumentos, precio_movilidad, precio_total,
        --     fuera_de_lima }
        snapshot_antes      NVARCHAR(MAX)   NOT NULL,
        -- Resumen de campos modificados, ej. ["venue","setlist","precio"]
        campos_modificados  VARCHAR(500)    NOT NULL DEFAULT '',
        precio_anterior     DECIMAL(10,2)   NOT NULL,
        precio_nuevo        DECIMAL(10,2)   NOT NULL,
        -- Estado de la revision por parte de la novia
        estado_revision     VARCHAR(20)     NOT NULL DEFAULT 'PENDIENTE',
        fecha_creacion      DATETIMEOFFSET  NOT NULL DEFAULT SYSDATETIMEOFFSET(),
        fecha_revision      DATETIMEOFFSET  NULL,

        FOREIGN KEY (id_boda) REFERENCES boda(id_boda) ON DELETE CASCADE,
        FOREIGN KEY (id_usuario_autor) REFERENCES usuario(id_usuario),
        CHECK (estado_revision IN ('PENDIENTE', 'ACEPTADA', 'RECHAZADA'))
    )

    CREATE INDEX ix_boda_anotacion_boda_estado
        ON boda_anotacion(id_boda, estado_revision)
END
GO

PRINT 'OK: tabla boda_anotacion creada'


-- =============================================================================
-- 3. RECONFIGURAR PRICING (10% menos en tarifas, eliminar cargo grupo grande,
--    agregar columnas XL)
-- =============================================================================

-- Agregar columnas XL si no existen
IF NOT EXISTS (SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('configuracion_precios')
      AND name = 'movilidad_xl_umbral_pasajeros')
BEGIN
    ALTER TABLE configuracion_precios
    ADD movilidad_xl_umbral_pasajeros INT NOT NULL DEFAULT 4
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('configuracion_precios')
      AND name = 'movilidad_xl_factor')
BEGIN
    ALTER TABLE configuracion_precios
    ADD movilidad_xl_factor DECIMAL(5,3) NOT NULL DEFAULT 1.200
END
GO

-- Bajar tarifas 10% y desactivar cargo grupo grande
-- Multiplicar por 0.90 (sin asumir valores fijos: respetamos cualquier
-- ajuste previo que hayas hecho en la BD)
UPDATE configuracion_precios
SET    movilidad_tarifa_km     = ROUND(movilidad_tarifa_km * 0.90, 2),
       movilidad_tarifa_minuto = ROUND(movilidad_tarifa_minuto * 0.90, 2),
       movilidad_grupo_grande  = 0,    -- queda inerte, lo reemplaza XL
       movilidad_umbral_grupo  = 9999  -- umbral imposible
GO

PRINT 'OK: tarifas reducidas 10%, cargo grupo grande eliminado, XL configurado'


-- =============================================================================
-- 4. SP DE VALIDACION DE CONFLICTO con gap variable segun distancia
--
-- Reglas:
--   - Si ambas bodas (existente y nueva) estan a <= 20km de la
--     parroquia base -> gap minimo 4 horas.
--   - Si alguna de las dos esta a > 20km -> gap minimo 6 horas.
--
-- La distancia se mide Haversine desde la parroquia base
-- (lat/lng_base de configuracion_precios).
-- =============================================================================
GO

CREATE OR ALTER PROC usp_boda_validar_conflicto
(
    @fecha          DATE,
    @hora           TIME(0),
    @lat_nueva      FLOAT = NULL,
    @lng_nueva      FLOAT = NULL,
    @id_boda_excluir INT = NULL
)
AS
BEGIN
    SET NOCOUNT ON

    DECLARE @hora_min TIME = '12:00:00'
    DECLARE @hora_max TIME = '20:00:00'

    -- Fuera de rango horario
    IF @hora < @hora_min OR @hora > @hora_max
    BEGIN
        SELECT  CAST(1 AS BIT) AS conflicto,
                N'La hora debe estar entre las 12:00 y las 20:00.' AS razon,
                N'' AS horas_disponibles
        RETURN
    END

    -- Punto base (parroquia)
    DECLARE @lat_base FLOAT, @lng_base FLOAT
    SELECT TOP 1 @lat_base = latitud_base, @lng_base = longitud_base
    FROM   configuracion_precios

    -- Distancia Haversine de la boda propuesta
    DECLARE @dist_nueva FLOAT = 0
    IF @lat_nueva IS NOT NULL AND @lng_nueva IS NOT NULL
        AND @lat_base IS NOT NULL
    BEGIN
        SET @dist_nueva = dbo.fn_haversine_km(@lat_base, @lng_base, @lat_nueva, @lng_nueva)
    END

    -- Contar bodas CONTRACTED/COMPLETED ese dia
    DECLARE @bodas_dia INT
    SELECT  @bodas_dia = COUNT(*)
    FROM    boda
    WHERE   fecha_boda = @fecha
      AND   estado IN ('CONTRACTED', 'COMPLETED')
      AND   (@id_boda_excluir IS NULL OR id_boda <> @id_boda_excluir)

    IF @bodas_dia >= 2
    BEGIN
        SELECT  CAST(1 AS BIT) AS conflicto,
                N'Ya hay dos bodas contratadas ese dia. Por favor elige otra fecha.' AS razon,
                N'' AS horas_disponibles
        RETURN
    END

    IF @bodas_dia = 1
    BEGIN
        DECLARE @hora_existente TIME(0)
        DECLARE @lat_existente FLOAT, @lng_existente FLOAT
        SELECT  @hora_existente = hora_boda,
                @lat_existente = latitud,
                @lng_existente = longitud
        FROM    boda
        WHERE   fecha_boda = @fecha
          AND   estado IN ('CONTRACTED', 'COMPLETED')
          AND   (@id_boda_excluir IS NULL OR id_boda <> @id_boda_excluir)

        -- Distancia de la boda existente
        DECLARE @dist_existente FLOAT = 0
        IF @lat_existente IS NOT NULL AND @lng_existente IS NOT NULL
        BEGIN
            SET @dist_existente = dbo.fn_haversine_km(@lat_base, @lng_base,
                                                     @lat_existente, @lng_existente)
        END

        -- Gap requerido: 6h si alguna boda > 20km, 4h si ambas <= 20km
        DECLARE @gap_horas INT = 4
        IF @dist_nueva > 20 OR @dist_existente > 20
            SET @gap_horas = 6

        DECLARE @diff_horas FLOAT = ABS(DATEDIFF(MINUTE, @hora_existente, @hora)) / 60.0

        IF @diff_horas < @gap_horas
        BEGIN
            -- Construir CSV de horas disponibles
            DECLARE @horas_libres VARCHAR(200) = ''
            DECLARE @h INT = DATEPART(HOUR, @hora_min)
            DECLARE @h_max INT = DATEPART(HOUR, @hora_max)
            DECLARE @h_existente INT = DATEPART(HOUR, @hora_existente)
            WHILE @h <= @h_max
            BEGIN
                IF ABS(@h - @h_existente) >= @gap_horas
                BEGIN
                    SET @horas_libres = @horas_libres +
                        CASE WHEN LEN(@horas_libres) > 0 THEN ', ' ELSE '' END +
                        RIGHT('0' + CAST(@h AS VARCHAR), 2) + ':00'
                END
                SET @h = @h + 1
            END

            DECLARE @hora_str VARCHAR(10) =
                RIGHT('0' + CAST(DATEPART(HOUR, @hora_existente) AS VARCHAR), 2)
                + ':' +
                RIGHT('0' + CAST(DATEPART(MINUTE, @hora_existente) AS VARCHAR), 2)

            SELECT  CAST(1 AS BIT) AS conflicto,
                    N'Ya existe una boda contratada ese dia a las ' + @hora_str +
                    N'. Necesitamos al menos ' + CAST(@gap_horas AS VARCHAR) +
                    N' horas entre bodas (segun distancia de los venues).' AS razon,
                    ISNULL(@horas_libres, '') AS horas_disponibles
            RETURN
        END
    END

    SELECT  CAST(0 AS BIT) AS conflicto,
            N'' AS razon,
            N'' AS horas_disponibles
END
GO

-- Funcion helper Haversine (necesaria para el SP de arriba)
CREATE OR ALTER FUNCTION dbo.fn_haversine_km
(
    @lat1 FLOAT, @lng1 FLOAT, @lat2 FLOAT, @lng2 FLOAT
)
RETURNS FLOAT
AS
BEGIN
    DECLARE @R FLOAT = 6371.0
    DECLARE @dlat FLOAT = RADIANS(@lat2 - @lat1)
    DECLARE @dlng FLOAT = RADIANS(@lng2 - @lng1)
    DECLARE @a FLOAT =
        SIN(@dlat/2) * SIN(@dlat/2) +
        COS(RADIANS(@lat1)) * COS(RADIANS(@lat2)) *
        SIN(@dlng/2) * SIN(@dlng/2)
    DECLARE @c FLOAT = 2 * ATN2(SQRT(@a), SQRT(1-@a))
    RETURN @R * @c
END
GO

PRINT 'OK: SP usp_boda_validar_conflicto actualizado con gap variable'


-- =============================================================================
-- 5. SP usp_disponibilidad_mes con modo admin (muestra todos los estados)
-- =============================================================================
GO

CREATE OR ALTER PROC usp_disponibilidad_mes
(
    @anio           INT,
    @mes            INT,
    @id_boda_excluir INT = NULL,
    -- 'admin' devuelve TODAS las bodas (cualquier estado).
    -- 'novia' (default) devuelve solo CONTRACTED/COMPLETED, que son
    -- las que realmente bloquean al picker.
    @modo           VARCHAR(20) = 'novia'
)
AS
BEGIN
    SET NOCOUNT ON

    DECLARE @estados_filtro TABLE (estado VARCHAR(30))
    IF @modo = 'admin'
        INSERT INTO @estados_filtro VALUES
            ('DRAFT'),('SUBMITTED'),('APPROVED'),('CONTRACTED'),
            ('COMPLETED'),('RETURNED_WITH_NOTES')
    ELSE
        INSERT INTO @estados_filtro VALUES ('CONTRACTED'),('COMPLETED')

    SELECT  b.id_boda,
            CONVERT(VARCHAR(10), b.fecha_boda, 23) AS fecha,
            b.hora_boda,
            n.nombre_novio,
            n.nombre_novia,
            b.estado
    FROM    boda b
    INNER JOIN novios n ON n.id_novios = b.id_novios
    WHERE   YEAR(b.fecha_boda)  = @anio
      AND   MONTH(b.fecha_boda) = @mes
      AND   b.estado IN (SELECT estado FROM @estados_filtro)
      AND   (@id_boda_excluir IS NULL OR b.id_boda <> @id_boda_excluir)
    ORDER BY b.fecha_boda, b.hora_boda
END
GO

PRINT 'OK: SP usp_disponibilidad_mes actualizado con parametro modo'


-- =============================================================================
-- 6. SPs nuevos para devolver / aceptar / rechazar anotaciones
-- =============================================================================

GO

CREATE OR ALTER PROC usp_anotacion_crear
(
    @id_boda            INT,
    @id_usuario_autor   INT,
    @nombre_autor       VARCHAR(256),
    @texto_nota         VARCHAR(2000),
    @snapshot_antes     NVARCHAR(MAX),
    @campos_modificados VARCHAR(500),
    @precio_anterior    DECIMAL(10,2),
    @precio_nuevo       DECIMAL(10,2)
)
AS
BEGIN
    SET NOCOUNT ON

    INSERT INTO boda_anotacion
        (id_boda, id_usuario_autor, nombre_autor, texto_nota,
         snapshot_antes, campos_modificados,
         precio_anterior, precio_nuevo)
    VALUES
        (@id_boda, @id_usuario_autor, @nombre_autor, @texto_nota,
         @snapshot_antes, @campos_modificados,
         @precio_anterior, @precio_nuevo)

    SELECT SCOPE_IDENTITY() AS id_boda_anotacion
END
GO


CREATE OR ALTER PROC usp_anotacion_obtener_pendiente
(
    @id_boda INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT TOP 1
        id_boda_anotacion, id_boda, nombre_autor, texto_nota,
        snapshot_antes, campos_modificados,
        precio_anterior, precio_nuevo, estado_revision,
        fecha_creacion
    FROM   boda_anotacion
    WHERE  id_boda = @id_boda
      AND  estado_revision = 'PENDIENTE'
    ORDER BY fecha_creacion DESC
END
GO


CREATE OR ALTER PROC usp_anotacion_marcar
(
    @id_boda_anotacion INT,
    @nuevo_estado      VARCHAR(20)
)
AS
BEGIN
    SET NOCOUNT ON
    UPDATE boda_anotacion
    SET    estado_revision = @nuevo_estado,
           fecha_revision  = SYSDATETIMEOFFSET()
    WHERE  id_boda_anotacion = @id_boda_anotacion
END
GO

PRINT 'OK: SPs de anotaciones creados'


-- =============================================================================
-- 7. SP para mapa filtrado por mes
-- =============================================================================
GO

CREATE OR ALTER PROC usp_mapa_bodas_mes
(
    @anio INT,
    @mes  INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  b.id_boda,
            CONVERT(VARCHAR(10), b.fecha_boda, 23) AS fecha,
            b.hora_boda,
            b.nombre_local,
            b.direccion_local,
            b.latitud,
            b.longitud,
            b.precio_movilidad,
            b.estado,
            n.nombre_novio,
            n.nombre_novia
    FROM    boda b
    INNER JOIN novios n ON n.id_novios = b.id_novios
    WHERE   YEAR(b.fecha_boda)  = @anio
      AND   MONTH(b.fecha_boda) = @mes
      AND   b.estado IN ('SUBMITTED', 'APPROVED', 'CONTRACTED',
                         'COMPLETED', 'RETURNED_WITH_NOTES')
      AND   b.latitud IS NOT NULL
      AND   b.longitud IS NOT NULL
    ORDER BY b.fecha_boda, b.hora_boda
END
GO

PRINT 'OK: SP usp_mapa_bodas_mes creado'

PRINT '============================================================'
PRINT 'Migracion v10 completada. Recuerda recotizar las 20 bodas:'
PRINT '   python recotizar_bodas.py'
PRINT '============================================================'
GO
