-- =============================================================================
-- migracion_v9_marker_seed.sql
-- Pacem Deus Bodas - agrega columna marker_seed a usuario y boda para
-- poder identificar (y eventualmente borrar) registros creados por
-- seeds de demo. Es NULL para datos reales del usuario.
--
-- Idempotente.
-- =============================================================================


IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('usuario') AND name = 'marker_seed'
)
BEGIN
    ALTER TABLE usuario ADD marker_seed VARCHAR(50) NULL
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('boda') AND name = 'marker_seed'
)
BEGIN
    ALTER TABLE boda ADD marker_seed VARCHAR(50) NULL
END
GO

PRINT 'Columnas marker_seed agregadas (NULL por defecto, sin afectar datos existentes).'
GO
