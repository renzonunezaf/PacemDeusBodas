-- =============================================================================
-- 04_seed_instruments.sql
-- Catalogo de instrumentos y voces del Coro Pacem Deus.
-- Piano y Voz femenina principal son obligatorios en todo evento.
-- =============================================================================

IF NOT EXISTS (SELECT 1 FROM instrumento)
BEGIN
    INSERT INTO instrumento(slug, nombre, icono, precio_lima, precio_fuera, es_voz, canta_ingles, orden)
    VALUES
        ('piano',           'Pianista',                    'P',  150, 180, 0, 0, 1),
        ('voz_femenina',    'Voz femenina (principal)',    'F',  150, 180, 1, 1, 2),
        ('voz_femenina_2',  'Voz femenina II',             'F',  150, 180, 1, 1, 3),
        ('voz_femenina_3',  'Voz femenina III',            'F',  150, 180, 1, 1, 4),
        ('voz_masculina',   'Voz masculina (adicional)',   'M',  150, 180, 1, 1, 5),
        ('violin_1',        'Violin I',                    'V',  150, 180, 0, 0, 6),
        ('violin_2',        'Violin II',                   'V',  150, 180, 0, 0, 7),
        ('cello',           'Cello',                       'C',  150, 180, 0, 0, 8),
        ('saxo_soprano',    'Saxo soprano',                'S',  150, 180, 0, 0, 9)
END
GO
