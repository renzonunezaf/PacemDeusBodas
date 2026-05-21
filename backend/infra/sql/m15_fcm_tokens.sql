-- ============================================================================
-- m15_fcm_tokens.sql (checkpoint v07)
--
-- Soporte para push notifications via Firebase Cloud Messaging.
--
-- Cada dispositivo Android obtiene un token FCM unico al instalar la app.
-- La app llama PUT /v1/auth/fcm-token con su token y el backend lo guarda
-- en usuario.fcm_token. Cuando un evento dispara una notificacion, el
-- backend usa ese token para mandar el push via Firebase API V1.
--
-- Limitacion conocida: solo guardamos UN token por usuario. Si el mismo
-- usuario instala la app en 2 devices, el segundo pisa al primero. Para
-- prod-real querriamos tabla device_token separada con FK a usuario,
-- pero para PC2/TB2 con 1 dispositivo por rol es suficiente.
-- ============================================================================

USE pacem_deus_bodas;
GO

PRINT '=================================================================';
PRINT 'Migracion v15 - FCM tokens';
PRINT '=================================================================';

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE Name = 'fcm_token' AND Object_ID = OBJECT_ID('usuario')
)
BEGIN
    ALTER TABLE usuario ADD fcm_token NVARCHAR(512) NULL;
    PRINT 'Columna usuario.fcm_token agregada.';
END
ELSE
BEGIN
    PRINT 'Columna usuario.fcm_token ya existe, se omite.';
END
GO


-- ─── SP usp_usuario_guardar_fcm_token ─────────────────────
-- Upsert simple: actualiza el token del usuario indicado.
CREATE OR ALTER PROC usp_usuario_guardar_fcm_token
(
    @id_usuario INT,
    @fcm_token  NVARCHAR(512)
)
AS
BEGIN
    SET NOCOUNT ON
    UPDATE usuario
    SET    fcm_token = @fcm_token
    WHERE  id_usuario = @id_usuario
END
GO


-- ─── SP usp_usuario_obtener_fcm_token ─────────────────────
-- Lee el token de un usuario para mandarle un push. El backend lo
-- llama desde shared/push.py al disparar una notificacion.
CREATE OR ALTER PROC usp_usuario_obtener_fcm_token
(
    @id_usuario INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT fcm_token FROM usuario WHERE id_usuario = @id_usuario
END
GO


-- ─── SP usp_admin_listar_fcm_tokens ───────────────────────
-- Devuelve los tokens FCM de todos los admins activos. Usado por el
-- helper notify_admins para mandar push a todos los administradores
-- del sistema en una sola operacion.
CREATE OR ALTER PROC usp_admin_listar_fcm_tokens
AS
BEGIN
    SET NOCOUNT ON
    SELECT id_usuario, fcm_token
    FROM   usuario
    WHERE  rol = 'ADMIN'
      AND  activo = 1
      AND  fcm_token IS NOT NULL
END
GO


PRINT '=================================================================';
PRINT 'Migracion v15 - COMPLETADA';
PRINT '=================================================================';
GO
