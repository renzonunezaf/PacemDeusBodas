-- =============================================================================
-- 03_seed_pricing.sql
-- Configuracion inicial de precios y parametros del motor de movilidad.
-- Single-row: solo existe una fila en configuracion_precios.
-- TODOS los valores son configurables desde el panel admin.
-- =============================================================================

IF NOT EXISTS (SELECT 1 FROM configuracion_precios)
BEGIN
    INSERT INTO configuracion_precios(
        precio_base_lima,
        precio_base_fuera,
        precio_instrumento_lima,
        precio_instrumento_fuera,
        movilidad_minima,
        movilidad_maxima,
        latitud_base,
        longitud_base,
        radio_lima_km,
        movilidad_km_libres,
        movilidad_minutos_libres,
        movilidad_tarifa_km,
        movilidad_tarifa_minuto,
        movilidad_grupo_grande,
        movilidad_umbral_grupo,
        movilidad_centro_historico,
        centro_norte_lat,
        centro_sur_lat,
        centro_oeste_lng,
        centro_este_lng
    )
    VALUES (
        450,            -- precio_base_lima              S/.
        650,            -- precio_base_fuera             S/.
        150,            -- precio_instrumento_lima       S/.
        180,            -- precio_instrumento_fuera      S/.
        200,            -- movilidad_minima              S/.
        400,            -- movilidad_maxima              S/.
        -12.0541,       -- latitud_base   (Parroquia Sagrada Familia, Bellavista)
        -77.0933,       -- longitud_base
        30,             -- radio_lima_km
        15,             -- movilidad_km_libres           km incluidos en precio base
        25,             -- movilidad_minutos_libres      minutos incluidos
        6,              -- movilidad_tarifa_km           S/. por km excedente
        3.5,            -- movilidad_tarifa_minuto       S/. por minuto excedente
        150,            -- movilidad_grupo_grande        recargo si grupo > umbral
        5,              -- movilidad_umbral_grupo
        100,            -- movilidad_centro_historico
        -12.038,        -- centro_norte_lat   (Rio Rimac)
        -12.065,        -- centro_sur_lat     (Av. Grau)
        -77.050,        -- centro_oeste_lng   (Av. Alfonso Ugarte)
        -77.010         -- centro_este_lng    (Av. Abancay)
    )
END
GO
