-- =============================================================================
-- m04_planners_seed.sql
-- Pacem Deus Bodas - seed de wedding planners predefinidos.
--
-- Inserta los 3 planners predefinidos en el sistema con sus respectivos
-- usuarios. Renzo aclaro que los planners NO se registran via UI: se
-- insertan manualmente en BD. La novia los selecciona desde una lista.
--
-- IMPORTANTE: emails consistentes con DIR-H02 de directivas
-- (wedding1/2/3@correo.com). Una version vieja de este archivo usaba
-- emails *.pe distintos, lo que llevo a tener planners duplicados en BD
-- (mismo nombre, distinto email). m13_limpiar_planners_duplicados.sql
-- limpia el remanente.
--
-- Tambien crea el SP usp_planners_listar_publico para que la novia
-- (rol COUPLE) pueda consultar la lista sin requerir permisos de admin.
--
-- Password de los 3 planners: Welcome.10 (hash bcrypt generado offline)
--
-- Idempotente: si los usuarios ya existen, no los duplica.
-- =============================================================================


-- =============================================================================
-- PASO 1: insertar usuarios con rol WEDDING_PLANNER + entradas en planner
-- =============================================================================
-- bcrypt hash de "Welcome.10" (cost 12). Mismo hash que usa el admin@.

DECLARE @password_hash VARCHAR(256) = '$2b$12$Vo12ng4aV7gNG7FhHCRuUumDirIRjjP6gJLivpumyUFiYpR2g2QsC'

-- Planner 1: Carla Mendoza (Bodas Doradas)
IF NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'wedding1@correo.com')
BEGIN
    INSERT INTO usuario (email, password_hash, rol)
    VALUES ('wedding1@correo.com', @password_hash, 'WEDDING_PLANNER')

    DECLARE @id_u1 INT = SCOPE_IDENTITY()
    INSERT INTO planner (id_usuario, nombre, empresa, telefono)
    VALUES (@id_u1, 'Carla Mendoza', 'Bodas Doradas', '+51987654001')
END

-- Planner 2: Lucia Salazar (Sagrada Ceremonia)
IF NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'wedding2@correo.com')
BEGIN
    INSERT INTO usuario (email, password_hash, rol)
    VALUES ('wedding2@correo.com', @password_hash, 'WEDDING_PLANNER')

    DECLARE @id_u2 INT = SCOPE_IDENTITY()
    INSERT INTO planner (id_usuario, nombre, empresa, telefono)
    VALUES (@id_u2, 'Lucia Salazar', 'Sagrada Ceremonia', '+51987654002')
END

-- Planner 3: Diego Fuentes (Eventos del Alma)
IF NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'wedding3@correo.com')
BEGIN
    INSERT INTO usuario (email, password_hash, rol)
    VALUES ('wedding3@correo.com', @password_hash, 'WEDDING_PLANNER')

    DECLARE @id_u3 INT = SCOPE_IDENTITY()
    INSERT INTO planner (id_usuario, nombre, empresa, telefono)
    VALUES (@id_u3, 'Diego Fuentes', 'Eventos del Alma', '+51987654003')
END
GO


-- =============================================================================
-- PASO 2: SP usp_planners_listar_publico
-- =============================================================================
-- Permite a cualquier rol autenticado consultar la lista de planners. La
-- novia lo usa al seleccionar planner para su boda; el admin lo usa al
-- reasignar. Devuelve campos publicos (sin email ni id_usuario).

CREATE OR ALTER PROC usp_planners_listar_publico
AS
BEGIN
    SET NOCOUNT ON
    SELECT  p.id_planner,
            p.nombre,
            p.empresa,
            p.telefono
    FROM    planner p
    INNER JOIN usuario u ON u.id_usuario = p.id_usuario
    WHERE   u.activo = 1
    ORDER BY p.nombre ASC
END
GO


-- =============================================================================
-- PASO 3: SP usp_boda_asignar_planner_couple
-- =============================================================================
-- La novia asigna un planner a SU boda. Validamos en el codigo Python que
-- la boda pertenezca al couple. El SP solo persiste.

CREATE OR ALTER PROC usp_boda_asignar_planner_couple
(
    @id_boda    INT,
    @id_planner INT
)
AS
BEGIN
    SET NOCOUNT ON

    IF NOT EXISTS (SELECT 1 FROM planner WHERE id_planner = @id_planner)
    BEGIN
        RAISERROR(N'El planner no existe', 16, 1)
        RETURN
    END

    UPDATE boda
    SET    id_planner = @id_planner
    WHERE  id_boda = @id_boda
END
GO


-- =============================================================================
-- VERIFICACION
-- =============================================================================
PRINT '==============================================================='
PRINT 'Migracion v4 completada. Verificacion:'
PRINT '==============================================================='

SELECT 'Planners activos' AS tabla, p.id_planner, p.nombre, p.empresa, p.telefono
FROM planner p
INNER JOIN usuario u ON u.id_usuario = p.id_usuario
WHERE u.activo = 1
ORDER BY p.nombre

SELECT 'SPs nuevos' AS tabla, name FROM sys.procedures
WHERE name IN ('usp_planners_listar_publico', 'usp_boda_asignar_planner_couple')
GO
