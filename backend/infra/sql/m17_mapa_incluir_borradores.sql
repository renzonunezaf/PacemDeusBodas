-- =============================================================================
-- m17_mapa_incluir_borradores.sql
-- Pacem Deus Bodas - Ajuste de usp_mapa_bodas_mes para que el panel
-- del admin vea TODAS las bodas con coordenadas excepto las que
-- estan en CANCELLATION_REQUESTED.
--
-- Antes (m10): el SP filtraba a SUBMITTED/APPROVED/CONTRACTED/
-- COMPLETED/RETURNED_WITH_NOTES (excluyendo DRAFT y CANCELLATION).
-- Ahora: incluye DRAFT (gris) para que el coro vea las bodas que
-- los novios todavia estan armando. Solo excluye CANCELLATION,
-- que no requiere accion en el mapa.
--
-- Idempotente (CREATE OR ALTER).
-- =============================================================================

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
      AND   b.estado IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'CONTRACTED',
                         'COMPLETED', 'RETURNED_WITH_NOTES')
      AND   b.latitud IS NOT NULL
      AND   b.longitud IS NOT NULL
    ORDER BY b.fecha_boda, b.hora_boda
END
GO


PRINT 'OK: usp_mapa_bodas_mes ahora incluye DRAFT (excluye solo CANCELLATION_REQUESTED)'
GO
