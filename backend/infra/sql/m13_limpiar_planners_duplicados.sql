-- ============================================================================
-- m13_limpiar_planners_duplicados.sql (checkpoint v03)
--
-- Limpia los wedding planners duplicados que quedaron en BD por un bug de
-- m04 (en una version vieja insertaba con emails *.pe distintos a los
-- que ya tenian los planners originales con emails wedding[N]@correo.com).
--
-- Diagnostico (output del query del 2026-05-14):
--   id_planner | id_usuario | nombre         | email original (usuario)
--   1          | 4          | Carla Mendoza  | wedding1@correo.com    <- ORIGINAL, usado por 6 bodas
--   4          | 49         | Carla Mendoza  | carla.mendoza@bodasdoradas.pe  <- DUPLICADO, 0 bodas
--   2          | 5          | Lucia Salazar  | wedding2@correo.com    <- ORIGINAL, usado por 5 bodas
--   5          | 50         | Lucia Salazar  | lucia.salazar@sagradaceremonia.pe  <- DUPLICADO
--   3          | 6          | Diego Fuentes  | wedding3@correo.com    <- ORIGINAL, usado por 7 bodas
--   6          | 51         | Diego Fuentes  | diego.fuentes@eventosdelalma.pe  <- DUPLICADO
--
-- Estrategia: borrar los duplicados (los 3 con emails *.pe). Ningun
-- registro de boda los referencia, asi que es seguro.
--
-- Es IDEMPOTENTE: si los duplicados ya no estan, no hace nada.
-- ============================================================================

USE pacem_deus_bodas;
GO

PRINT '=================================================================';
PRINT 'Migracion v13 - Limpieza de wedding planners duplicados';
PRINT '=================================================================';

-- Emails de los duplicados a eliminar
DECLARE @emails_duplicados TABLE (email VARCHAR(120))
INSERT INTO @emails_duplicados VALUES
    ('carla.mendoza@bodasdoradas.pe'),
    ('lucia.salazar@sagradaceremonia.pe'),
    ('diego.fuentes@eventosdelalma.pe')

-- Seguridad: antes de borrar, verificar que NINGUNA boda referencia a
-- estos planners. Si alguna lo hace, abortar (significaria que necesitamos
-- reasignar primero, no podemos dejar bodas huerfanas).
DECLARE @bodas_afectadas INT = (
    SELECT COUNT(*)
    FROM   boda b
    INNER JOIN planner p ON p.id_planner = b.id_planner
    INNER JOIN usuario u ON u.id_usuario = p.id_usuario
    WHERE  u.email IN (SELECT email FROM @emails_duplicados)
)

IF @bodas_afectadas > 0
BEGIN
    PRINT 'ABORTANDO: hay bodas que referencian a los planners duplicados.';
    PRINT 'Reasignar primero antes de eliminar los duplicados.';
    RAISERROR(N'Bodas huerfanas detectadas, no se puede limpiar.', 16, 1);
    RETURN;
END

PRINT 'Verificacion: ninguna boda referencia los planners duplicados. OK para limpiar.';
PRINT '';

-- Paso 1: borrar de tabla planner los duplicados
DELETE FROM planner
WHERE  id_usuario IN (
    SELECT id_usuario FROM usuario
    WHERE  email IN (SELECT email FROM @emails_duplicados)
)
DECLARE @planners_borrados INT = @@ROWCOUNT
PRINT CONCAT('Planners borrados: ', @planners_borrados)

-- Paso 2: borrar los usuarios duplicados
DELETE FROM usuario
WHERE  email IN (SELECT email FROM @emails_duplicados)
DECLARE @usuarios_borrados INT = @@ROWCOUNT
PRINT CONCAT('Usuarios duplicados borrados: ', @usuarios_borrados)

PRINT '';
PRINT 'Estado final de planners:';
SELECT p.id_planner, p.nombre, p.empresa, u.email
FROM planner p
INNER JOIN usuario u ON u.id_usuario = p.id_usuario
ORDER BY p.id_planner

PRINT '';
PRINT '=================================================================';
PRINT 'Migracion v13 - COMPLETADA';
PRINT '=================================================================';
GO
