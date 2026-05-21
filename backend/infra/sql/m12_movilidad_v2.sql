-- ============================================================================
-- MIGRACION v12 - Modelo de movilidad v2 (checkpoint v02)
--
-- Cambia el modelo de movilidad de:
--   "tarifa por km excedente + tarifa por min excedente"
-- a:
--   "curva potencial por km entre 20 y 50, plateau despues +
--    recargo por trafico solo cuando km > 20 y trafico > umbral"
--
-- Cambios en configuracion_precios:
--   + Agrega 6 columnas nuevas para el modelo v2 (mov_*)
--   + Actualiza los valores a los confirmados por Renzo el 2026-05-14
--   - NO toca las columnas viejas (movilidad_km_libres,
--     movilidad_minutos_libres, movilidad_tarifa_km,
--     movilidad_tarifa_minuto, etc.) para no romper consultas legacy;
--     quedan como deuda tecnica para una limpieza posterior.
--
-- Es IDEMPOTENTE: se puede correr varias veces sin romper nada.
-- ============================================================================

USE pacem_deus_bodas;
GO

PRINT '=================================================================';
PRINT 'Migracion v12 - Modelo de movilidad v2';
PRINT '=================================================================';

-- ----------------------------------------------------------------------------
-- 1. Agregar columnas del nuevo modelo
-- ----------------------------------------------------------------------------
PRINT '';
PRINT '[1/3] Agregando columnas del modelo v2...';

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('configuracion_precios')
      AND name = 'mov_distancia_libre_km'
)
BEGIN
    ALTER TABLE configuracion_precios
    ADD mov_distancia_libre_km DECIMAL(5,2) NOT NULL DEFAULT 20.00;
    PRINT '      mov_distancia_libre_km agregada (default 20.00)';
END
ELSE PRINT '      mov_distancia_libre_km ya existe';
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('configuracion_precios')
      AND name = 'mov_arranque_movilidad'
)
BEGIN
    ALTER TABLE configuracion_precios
    ADD mov_arranque_movilidad DECIMAL(10,2) NOT NULL DEFAULT 180.00;
    PRINT '      mov_arranque_movilidad agregada (default 180.00)';
END
ELSE PRINT '      mov_arranque_movilidad ya existe';
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('configuracion_precios')
      AND name = 'mov_tope_movilidad'
)
BEGIN
    ALTER TABLE configuracion_precios
    ADD mov_tope_movilidad DECIMAL(10,2) NOT NULL DEFAULT 320.00;
    PRINT '      mov_tope_movilidad agregada (default 320.00)';
END
ELSE PRINT '      mov_tope_movilidad ya existe';
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('configuracion_precios')
      AND name = 'mov_curva_exponente'
)
BEGIN
    ALTER TABLE configuracion_precios
    ADD mov_curva_exponente DECIMAL(4,2) NOT NULL DEFAULT 1.50;
    PRINT '      mov_curva_exponente agregada (default 1.50)';
END
ELSE PRINT '      mov_curva_exponente ya existe';
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('configuracion_precios')
      AND name = 'mov_traffic_umbral_min'
)
BEGIN
    ALTER TABLE configuracion_precios
    ADD mov_traffic_umbral_min DECIMAL(5,2) NOT NULL DEFAULT 10.00;
    PRINT '      mov_traffic_umbral_min agregada (default 10.00)';
END
ELSE PRINT '      mov_traffic_umbral_min ya existe';
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('configuracion_precios')
      AND name = 'mov_traffic_tarifa_minuto'
)
BEGIN
    ALTER TABLE configuracion_precios
    ADD mov_traffic_tarifa_minuto DECIMAL(10,2) NOT NULL DEFAULT 3.15;
    PRINT '      mov_traffic_tarifa_minuto agregada (default 3.15)';
END
ELSE PRINT '      mov_traffic_tarifa_minuto ya existe';
GO

PRINT '[1/3] OK: 6 columnas verificadas';

-- ----------------------------------------------------------------------------
-- 2. Setear valores finales (confirmados por Renzo el 2026-05-14)
-- ----------------------------------------------------------------------------
PRINT '';
PRINT '[2/3] Estableciendo valores finales del modelo v2...';

UPDATE configuracion_precios
SET    mov_distancia_libre_km    = 20.00,
       mov_arranque_movilidad    = 180.00,
       mov_tope_movilidad        = 320.00,
       mov_curva_exponente       = 1.50,
       mov_traffic_umbral_min    = 10.00,
       mov_traffic_tarifa_minuto = 3.15;

PRINT '[2/3] OK';

-- ----------------------------------------------------------------------------
-- 3. Verificacion: imprimir configuracion final de movilidad
-- ----------------------------------------------------------------------------
PRINT '';
PRINT '[3/3] Verificacion - configuracion de movilidad final:';

SELECT mov_distancia_libre_km   AS km_libres,
       mov_arranque_movilidad   AS arranque_soles,
       mov_tope_movilidad       AS tope_soles,
       mov_curva_exponente      AS exponente,
       mov_traffic_umbral_min   AS umbral_min,
       mov_traffic_tarifa_minuto AS tarifa_min_soles,
       movilidad_xl_umbral_pasajeros AS xl_umbral_pax,
       movilidad_xl_factor      AS xl_factor,
       movilidad_centro_historico AS centro_historico_soles
FROM   configuracion_precios;

PRINT '';
PRINT '=================================================================';
PRINT 'Migracion v12 - COMPLETADA';
PRINT '=================================================================';
GO
