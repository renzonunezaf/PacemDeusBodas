-- =============================================================================
-- migracion_v7_sps_aux_obtener.sql
-- Pacem Deus Bodas - SPs auxiliares para traer datos completos de
-- novios y planner por su ID primario.
--
-- El handler get_boda_contrato_pdf necesita estos datos para armar el
-- contrato. Los SPs son simples (SELECT por PK) pero los mantenemos
-- consistentes con el patron del proyecto (toda query va via SP).
--
-- Idempotente (CREATE OR ALTER).
-- =============================================================================


CREATE OR ALTER PROC usp_novios_obtener
(
    @id_novios INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  id_novios,
            id_usuario,
            nombre_novio,
            nombre_novia,
            tipo_doc_novio,
            documento_novio,
            tipo_doc_novia,
            documento_novia,
            telefono,
            fecha_creacion
    FROM    novios
    WHERE   id_novios = @id_novios
END
GO


CREATE OR ALTER PROC usp_planner_obtener
(
    @id_planner INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  id_planner,
            id_usuario,
            nombre,
            empresa,
            telefono,
            fecha_creacion
    FROM    planner
    WHERE   id_planner = @id_planner
END
GO


PRINT 'SPs auxiliares creados: usp_novios_obtener, usp_planner_obtener'
GO
