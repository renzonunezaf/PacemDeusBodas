-- =============================================================================
-- m18_disponibilidad_v2.sql
-- Pacem Deus Bodas - usp_disponibilidad_mes v2
--
-- PROBLEMAS QUE ARREGLA:
--
-- (1) Novia mode antes filtraba solo CONTRACTED/COMPLETED. Eso significaba
--     que un dia con 1 APPROVED + 1 CONTRACTED aparecia como "partial"
--     (1 contracted) cuando en realidad ya estaba "full" — APPROVED
--     tambien bloquea el slot porque el admin se compromete con esa fecha.
--
-- (2) Admin mode excluia CANCELLATION_REQUESTED. El coro necesita ver
--     las cancelaciones pendientes en su dashboard porque debe aceptarlas
--     o negarlas. Ahora se incluyen.
--
-- (3) El calculo de partial/full en Python solo chequeaba gap contra UNA
--     boda existente (la primera CONTRACTED). Con 3 bodas en el dia
--     reportaba "partial" aunque las 3 horas combinadas no dejaran un
--     gap valido. El fix de Python (_calcular_estado_color) chequea
--     gap contra TODAS las bodas bloqueantes.
--
-- ESTADOS BLOQUEANTES = TODO MENOS DRAFT
--   El DRAFT no compromete fecha (la novia esta armando). El resto si.
--   CANCELLATION_REQUESTED se incluye conservadoramente porque si el
--   admin niega la cancelacion el slot queda igual reservado.
--
-- Idempotente (CREATE OR ALTER).
-- =============================================================================

CREATE OR ALTER PROC usp_disponibilidad_mes
(
    @anio            INT,
    @mes             INT,
    @id_boda_excluir INT          = NULL,
    @modo            VARCHAR(20)  = 'novia'
)
AS
BEGIN
    SET NOCOUNT ON

    DECLARE @estados_filtro TABLE (estado VARCHAR(30))

    IF @modo = 'admin'
        -- Admin ve TODOS los estados, para tener vision completa del mes.
        INSERT INTO @estados_filtro VALUES
            ('DRAFT'),
            ('SUBMITTED'),
            ('APPROVED'),
            ('CONTRACTED'),
            ('COMPLETED'),
            ('RETURNED_WITH_NOTES'),
            ('CANCELLATION_REQUESTED')
    ELSE
        -- Novia ve todos los estados bloqueantes (excluye solo DRAFT,
        -- porque los borradores de otras parejas no la bloquean a ella).
        INSERT INTO @estados_filtro VALUES
            ('SUBMITTED'),
            ('APPROVED'),
            ('CONTRACTED'),
            ('COMPLETED'),
            ('RETURNED_WITH_NOTES'),
            ('CANCELLATION_REQUESTED')

    SELECT  b.id_boda,
            CONVERT(VARCHAR(10), b.fecha_boda, 23)  AS fecha,
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


PRINT 'OK: usp_disponibilidad_mes v2 - bloqueantes = todo menos DRAFT'
GO
