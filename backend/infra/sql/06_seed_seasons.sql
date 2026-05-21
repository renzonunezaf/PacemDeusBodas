-- =============================================================================
-- 06_seed_seasons.sql
-- Tiempos liturgicos catolicos.
-- Las restricciones JSON definen que momentos se desactivan en cada temporada.
-- =============================================================================

IF NOT EXISTS (SELECT 1 FROM temporada_liturgica)
BEGIN
    INSERT INTO temporada_liturgica(slug, nombre, descripcion, restricciones)
    VALUES
        ('ordinario', 'Tiempo Ordinario',
            'Periodo regular del calendario liturgico',
            N'{}'),

        ('cuaresma', 'Cuaresma',
            'Tiempo de preparacion para la Pascua - 40 dias de penitencia',
            N'{"momentos_deshabilitados":["gloria"]}'),

        ('pascual', 'Tiempo Pascual',
            'Desde el Domingo de Resurreccion hasta Pentecostes',
            N'{}'),

        ('adviento', 'Adviento',
            'Cuatro semanas de preparacion para la Navidad',
            N'{"momentos_deshabilitados":["gloria"]}'),

        ('navidad', 'Tiempo de Navidad',
            'Desde la Navidad hasta el Bautismo del Senor',
            N'{}')
END
GO
