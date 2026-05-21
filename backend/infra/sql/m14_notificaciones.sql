-- ============================================================================
-- m14_notificaciones.sql (checkpoint v07)
--
-- Tabla `notificacion` + SPs para sistema simple de notificaciones por
-- polling (sin Firebase). Cada vez que ocurre un evento importante
-- (boda enviada para aprobacion, aprobada, devuelta, etc.), el handler
-- correspondiente inserta una fila en esta tabla. La app cliente hace
-- polling cada 10 segundos al endpoint GET /v1/notifications/poll para
-- traer las novedades.
--
-- Tipos posibles:
--   BODA_SUBMITTED          - novia envio su boda para aprobacion
--   BODA_APPROVED           - admin aprobo la boda
--   BODA_RETURNED           - admin devolvio con anotaciones
--   BODA_CANCELLATION_REQ   - novia solicito cancelacion
-- ============================================================================

USE pacem_deus_bodas;
GO

PRINT '=================================================================';
PRINT 'Migracion v14 - Tabla notificacion + SPs';
PRINT '=================================================================';

IF OBJECT_ID('notificacion', 'U') IS NULL
BEGIN
    CREATE TABLE notificacion (
        id_notificacion     INT IDENTITY(1,1) PRIMARY KEY,
        id_usuario_destino  INT NOT NULL,
        tipo                VARCHAR(40) NOT NULL,
        titulo              NVARCHAR(120) NOT NULL,
        mensaje             NVARCHAR(500) NOT NULL,
        id_boda             INT NULL,
        creado_en           DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
        leido_en            DATETIMEOFFSET NULL,
        CONSTRAINT fk_notif_usuario FOREIGN KEY (id_usuario_destino)
            REFERENCES usuario(id_usuario),
        CONSTRAINT fk_notif_boda FOREIGN KEY (id_boda)
            REFERENCES boda(id_boda)
    );
    CREATE INDEX ix_notif_destino_creado
        ON notificacion(id_usuario_destino, creado_en DESC);
    PRINT 'Tabla notificacion creada.';
END
ELSE
BEGIN
    PRINT 'Tabla notificacion ya existe, se omite creacion.';
END
GO


-- ─── SP usp_notificacion_crear ─────────────────────────────
-- Insertar una notificacion. Devuelve la fila insertada para que el
-- caller pueda log o devolverla en la respuesta del endpoint origen.
CREATE OR ALTER PROC usp_notificacion_crear
(
    @id_usuario_destino INT,
    @tipo               VARCHAR(40),
    @titulo             NVARCHAR(120),
    @mensaje            NVARCHAR(500),
    @id_boda            INT = NULL
)
AS
BEGIN
    SET NOCOUNT ON
    INSERT INTO notificacion (id_usuario_destino, tipo, titulo, mensaje, id_boda)
    VALUES (@id_usuario_destino, @tipo, @titulo, @mensaje, @id_boda)

    SELECT  id_notificacion, id_usuario_destino, tipo, titulo, mensaje,
            id_boda, creado_en
    FROM    notificacion
    WHERE   id_notificacion = SCOPE_IDENTITY()
END
GO


-- ─── SP usp_notificacion_poll ──────────────────────────────
-- Devuelve notificaciones para un usuario, filtrando por timestamp.
-- @since es ISO 8601 con offset (ej: "2026-05-15T10:30:00+00:00") o
-- NULL para devolver todas las no leidas.
CREATE OR ALTER PROC usp_notificacion_poll
(
    @id_usuario_destino INT,
    @since              DATETIMEOFFSET = NULL,
    @solo_no_leidas     BIT = 1
)
AS
BEGIN
    SET NOCOUNT ON

    SELECT  id_notificacion, tipo, titulo, mensaje, id_boda,
            creado_en, leido_en
    FROM    notificacion
    WHERE   id_usuario_destino = @id_usuario_destino
      AND   (@since IS NULL OR creado_en > @since)
      AND   (@solo_no_leidas = 0 OR leido_en IS NULL)
    ORDER BY creado_en DESC
END
GO


-- ─── SP usp_notificacion_marcar_leida ──────────────────────
-- Marca una notificacion como leida. La novia o el admin la llama
-- cuando ven el alert (o cuando navegan al detalle).
CREATE OR ALTER PROC usp_notificacion_marcar_leida
(
    @id_notificacion    INT,
    @id_usuario_destino INT
)
AS
BEGIN
    SET NOCOUNT ON
    UPDATE notificacion
    SET    leido_en = SYSDATETIMEOFFSET()
    WHERE  id_notificacion = @id_notificacion
      AND  id_usuario_destino = @id_usuario_destino
      AND  leido_en IS NULL
END
GO


-- ─── SP auxiliar: obtener id_usuario de los admins ──────────
-- Cuando un evento de boda dispara una notificacion a "todos los admins",
-- el handler usa este SP para resolver a quien notificar.
CREATE OR ALTER PROC usp_admin_listar_ids
AS
BEGIN
    SET NOCOUNT ON
    SELECT id_usuario FROM usuario WHERE rol = 'ADMIN' AND activo = 1
END
GO


-- ─── SP auxiliar: novios por id_novios ──────────────────────
-- Para resolver el id_usuario de la novia desde un id_novios cuando
-- queremos notificarle un cambio de estado en su boda.
CREATE OR ALTER PROC usp_novios_obtener_por_id
(
    @id_novios INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  id_novios, id_usuario, nombre_novio, nombre_novia
    FROM    novios
    WHERE   id_novios = @id_novios
END
GO


PRINT '=================================================================';
PRINT 'Migracion v14 - COMPLETADA';
PRINT '=================================================================';
GO
