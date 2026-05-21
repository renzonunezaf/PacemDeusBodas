-- =============================================================================
-- m16_usp_usuario_obtener.sql
-- Pacem Deus Bodas - SP general para obtener datos de un usuario por su
-- id_usuario, incluyendo su nombre humano segun el rol.
--
-- Diseño:
--   - LEFT JOIN con novios y planner para resolver el nombre legible
--     (la tabla `usuario` no tiene columna `nombre`).
--   - Para usuarios ADMIN devuelve nombre = NULL (no hay nombre humano
--     persistido; el handler aplica fallback "Coro Pacem Deus").
--
-- Uso: post_boda_devolver_anotaciones consulta el nombre del autor
-- antes de insertar la anotacion. Se mantiene general para otros
-- handlers futuros que necesiten datos basicos de un usuario.
--
-- Idempotente (CREATE OR ALTER).
-- =============================================================================

CREATE OR ALTER PROC usp_usuario_obtener
(
    @id_usuario INT
)
AS
BEGIN
    SET NOCOUNT ON

    SELECT
        u.id_usuario,
        u.email,
        u.rol,
        u.activo,
        u.fecha_creacion,
        -- Nombre humano resuelto via LEFT JOIN segun el rol del usuario.
        -- ADMIN no tiene tabla de perfil, asi que su `nombre` queda NULL
        -- y el handler aplica fallback institucional.
        CASE
            WHEN u.rol = 'COUPLE'          THEN n.nombre_novio + ' y ' + n.nombre_novia
            WHEN u.rol = 'WEDDING_PLANNER' THEN p.nombre
            ELSE NULL
        END AS nombre
    FROM      usuario  u
    LEFT JOIN novios   n ON n.id_usuario = u.id_usuario
    LEFT JOIN planner  p ON p.id_usuario = u.id_usuario
    WHERE     u.id_usuario = @id_usuario
END
GO


PRINT 'SP creado: usp_usuario_obtener'
GO
