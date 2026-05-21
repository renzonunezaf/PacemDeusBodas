-- =============================================================================
-- migracion_v8_disponibilidad.sql
-- Pacem Deus Bodas - SPs para validar conflictos de fecha/hora y para
-- devolver el calendario de disponibilidad mes por mes.
--
-- Reglas del negocio:
--   - Maximo 2 bodas en estado CONTRACTED por dia.
--   - Entre dos bodas CONTRACTED del mismo dia debe haber >=5 horas
--     de separacion.
--   - Estados que NO bloquean: DRAFT, SUBMITTED, APPROVED, CANCELADA, etc.
--     Solo CONTRACTED y COMPLETED bloquean.
--   - Horario permitido: 12:00 a 20:00.
--
-- Idempotente.
-- =============================================================================


-- =============================================================================
-- usp_disponibilidad_mes
-- Devuelve, para cada dia del mes pedido, el estado de disponibilidad y
-- la lista de bodas CONTRACTED (hora + nombre de la pareja).
--
-- Estado por dia:
--   'free'      -> sin bodas CONTRACTED
--   'partial'   -> 1 boda CONTRACTED, queda ventana de >=5h libre 12-20
--   'full'      -> 1 boda CONTRACTED sin ventana valida, o 2 CONTRACTED
--
-- @id_boda_excluir: si la novia esta editando su propia boda, le pasamos
-- su id para no contarla como conflicto con ella misma.
-- =============================================================================
CREATE OR ALTER PROC usp_disponibilidad_mes
(
    @anio           INT,
    @mes            INT,
    @id_boda_excluir INT = NULL
)
AS
BEGIN
    SET NOCOUNT ON

    -- Bodas CONTRACTED + COMPLETED del mes, excluyendo la propia si aplica
    ;WITH bodas_mes AS (
        SELECT  b.id_boda,
                b.fecha_boda,
                b.hora_boda,
                n.nombre_novio,
                n.nombre_novia
        FROM    boda b
        INNER JOIN novios n ON n.id_novios = b.id_novios
        WHERE   YEAR(b.fecha_boda)  = @anio
          AND   MONTH(b.fecha_boda) = @mes
          AND   b.estado IN ('CONTRACTED', 'COMPLETED')
          AND   (@id_boda_excluir IS NULL OR b.id_boda <> @id_boda_excluir)
    )
    SELECT  b.id_boda,
            CONVERT(VARCHAR(10), b.fecha_boda, 23) AS fecha,
            b.hora_boda,
            b.nombre_novio,
            b.nombre_novia
    FROM    bodas_mes b
    ORDER BY b.fecha_boda, b.hora_boda
END
GO


-- =============================================================================
-- usp_boda_validar_conflicto
-- Recibe una fecha + hora propuesta y devuelve si pasaria validacion.
--
-- Reglas:
--   1. Si ya hay 2 bodas CONTRACTED/COMPLETED ese dia -> conflicto (full).
--   2. Si hay 1 boda y la propuesta esta a menos de 5 horas -> conflicto.
--   3. Hora propuesta fuera de 12:00-20:00 -> conflicto.
--   4. Si no hay conflictos -> OK.
--
-- Devuelve una unica fila con:
--   conflicto BIT, razon VARCHAR, horas_disponibles VARCHAR
--     (csv de horas libres ej: "17:00,18:00,19:00,20:00")
-- =============================================================================
CREATE OR ALTER PROC usp_boda_validar_conflicto
(
    @fecha          DATE,
    @hora           TIME(0),
    @id_boda_excluir INT = NULL
)
AS
BEGIN
    SET NOCOUNT ON

    DECLARE @hora_min TIME = '12:00:00'
    DECLARE @hora_max TIME = '20:00:00'
    DECLARE @gap_horas INT = 5

    -- Horario fuera de rango
    IF @hora < @hora_min OR @hora > @hora_max
    BEGIN
        SELECT  CAST(1 AS BIT) AS conflicto,
                N'La hora debe estar entre las 12:00 y las 20:00.' AS razon,
                N'' AS horas_disponibles
        RETURN
    END

    -- Contar bodas CONTRACTED/COMPLETED ese dia
    DECLARE @bodas_dia INT
    SELECT @bodas_dia = COUNT(*)
    FROM   boda
    WHERE  fecha_boda = @fecha
      AND  estado IN ('CONTRACTED', 'COMPLETED')
      AND  (@id_boda_excluir IS NULL OR id_boda <> @id_boda_excluir)

    -- 2 bodas ya = full
    IF @bodas_dia >= 2
    BEGIN
        SELECT  CAST(1 AS BIT) AS conflicto,
                N'Ya hay dos bodas contratadas ese dia. Por favor elige otra fecha.' AS razon,
                N'' AS horas_disponibles
        RETURN
    END

    -- Si hay 1 boda, calcular distancia
    IF @bodas_dia = 1
    BEGIN
        DECLARE @hora_existente TIME(0)
        SELECT @hora_existente = hora_boda
        FROM   boda
        WHERE  fecha_boda = @fecha
          AND  estado IN ('CONTRACTED', 'COMPLETED')
          AND  (@id_boda_excluir IS NULL OR id_boda <> @id_boda_excluir)

        -- Distancia en horas
        DECLARE @diff_horas FLOAT
        SET @diff_horas = ABS(DATEDIFF(MINUTE, @hora_existente, @hora)) / 60.0

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
                    N'. Necesitamos al menos 5 horas entre bodas.' AS razon,
                    ISNULL(@horas_libres, '') AS horas_disponibles
            RETURN
        END
    END

    -- Sin conflictos
    SELECT  CAST(0 AS BIT) AS conflicto,
            N'' AS razon,
            N'' AS horas_disponibles
END
GO


-- =============================================================================
-- VERIFICACION
-- =============================================================================
PRINT 'SPs creados:'
SELECT name FROM sys.procedures
WHERE name IN ('usp_disponibilidad_mes', 'usp_boda_validar_conflicto')
ORDER BY name
GO
