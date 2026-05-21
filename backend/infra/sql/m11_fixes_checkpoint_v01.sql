-- ============================================================================
-- MIGRACION v11 - Fixes consolidados (checkpoint v01)
--
-- Aplica los fixes confirmados por Renzo el 2026-05-14:
--   1. SP `usp_instrumentos_listar` ahora devuelve `incluido_en_paquete_base`
--      (fix piano/voz desbloqueados en Android).
--   2. Columnas `surge_fuera_lima_rango_km` y `surge_fuera_lima_factor_max`
--      agregadas a `configuracion_precios` para el acelerador suave.
--   3. Valor de `precio_instrumento_adicional` corregido a S/.150
--      (estaba en S/.180; ahora con surge factor se multiplica fuera de Lima).
--
-- Es IDEMPOTENTE: se puede correr varias veces sin romper nada.
-- ============================================================================

USE pacem_deus_bodas;
GO

PRINT '=================================================================';
PRINT 'Migracion v11 - inicio';
PRINT '=================================================================';

-- ----------------------------------------------------------------------------
-- 1. SP usp_instrumentos_listar: ahora incluye incluido_en_paquete_base
-- ----------------------------------------------------------------------------
PRINT '';
PRINT '[1/4] Actualizando usp_instrumentos_listar...';
GO

CREATE OR ALTER PROC usp_instrumentos_listar
AS
BEGIN
    SET NOCOUNT ON
    SELECT  id_instrumento,
            slug,
            nombre,
            icono,
            precio_lima,
            precio_fuera,
            es_voz,
            canta_ingles,
            orden,
            incluido_en_paquete_base
    FROM    instrumento
    WHERE   activo = 1
    ORDER BY orden ASC
END
GO

PRINT '[1/4] OK: usp_instrumentos_listar ahora devuelve incluido_en_paquete_base';

-- ----------------------------------------------------------------------------
-- 2. Columnas para el acelerador suave fuera de Lima
-- ----------------------------------------------------------------------------
PRINT '';
PRINT '[2/4] Agregando columnas de surge factor...';
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('configuracion_precios')
      AND name = 'surge_fuera_lima_rango_km'
)
BEGIN
    ALTER TABLE configuracion_precios
    ADD surge_fuera_lima_rango_km DECIMAL(5,2) NOT NULL DEFAULT 50.00
    PRINT '      surge_fuera_lima_rango_km agregada con default 50.00';
END
ELSE
BEGIN
    PRINT '      surge_fuera_lima_rango_km ya existe (sin cambios)';
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('configuracion_precios')
      AND name = 'surge_fuera_lima_factor_max'
)
BEGIN
    ALTER TABLE configuracion_precios
    ADD surge_fuera_lima_factor_max DECIMAL(5,3) NOT NULL DEFAULT 0.200
    PRINT '      surge_fuera_lima_factor_max agregada con default 0.200';
END
ELSE
BEGIN
    PRINT '      surge_fuera_lima_factor_max ya existe (sin cambios)';
END
GO

PRINT '[2/4] OK';

-- ----------------------------------------------------------------------------
-- 3. Corregir valor de precio_instrumento_adicional a 150 (era 180)
--    El surge factor se encarga del recargo fuera de Lima.
-- ----------------------------------------------------------------------------
PRINT '';
PRINT '[3/4] Actualizando valores en configuracion_precios...';

DECLARE @precio_inst_actual DECIMAL(10,2);
SELECT @precio_inst_actual = precio_instrumento_adicional FROM configuracion_precios;
PRINT '      precio_instrumento_adicional actual = ' + CAST(@precio_inst_actual AS VARCHAR);

UPDATE configuracion_precios
SET    precio_instrumento_adicional = 150.00,
       precio_paquete_base          = 650.00
WHERE  precio_instrumento_adicional <> 150.00
   OR  precio_paquete_base          <> 650.00;

PRINT '[3/4] OK: precio_paquete_base = 650.00, precio_instrumento_adicional = 150.00';

-- ----------------------------------------------------------------------------
-- 4. Verificacion final
-- ----------------------------------------------------------------------------
PRINT '';
PRINT '[4/4] Verificacion final...';

PRINT '';
PRINT '      Configuracion de precios resultante:';
SELECT precio_paquete_base,
       precio_instrumento_adicional,
       radio_lima_km,
       surge_fuera_lima_rango_km,
       surge_fuera_lima_factor_max,
       movilidad_tarifa_km,
       movilidad_tarifa_minuto,
       movilidad_xl_umbral_pasajeros,
       movilidad_xl_factor
FROM   configuracion_precios;

PRINT '';
PRINT '      SP usp_instrumentos_listar devuelve:';
EXEC usp_instrumentos_listar;

PRINT '';
PRINT '=================================================================';
PRINT 'Migracion v11 - COMPLETADA';
PRINT '=================================================================';
GO
