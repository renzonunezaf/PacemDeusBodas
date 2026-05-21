-- =============================================================================
-- 05_seed_moments.sql
-- 14 momentos liturgicos de una boda catolica en orden estricto.
-- Las restricciones por temporada se almacenan en JSON valido (validado por CHECK ISJSON).
-- =============================================================================

IF NOT EXISTS (SELECT 1 FROM momento_liturgico)
BEGIN
    INSERT INTO momento_liturgico
        (slug, nombre, descripcion, icono, orden, categoria, max_canciones, permite_repetidas, restricciones_temporada)
    VALUES
        ('entrada_novio', 'Entrada del novio',
            'Procesion de ingreso del novio al altar', 'E', 1, 'NON_LITURGICAL', 1, 0, NULL),

        ('entrada_novia', 'Entrada de la novia',
            'Procesion de ingreso de la novia', 'N', 2, 'NON_LITURGICAL', 1, 0, NULL),

        ('piedad', 'Piedad (Kyrie)',
            'Acto penitencial - Senor, ten piedad', 'P', 3, 'LITURGICAL', 1, 0, NULL),

        ('gloria', 'Gloria',
            'Himno de alabanza - Gloria a Dios en el cielo', 'G', 4, 'LITURGICAL', 1, 0,
            N'{"deshabilitado_en":["cuaresma","adviento"]}'),

        ('salmo_responsorial', 'Salmo / Canto entre lecturas',
            'Canto meditativo entre primera y segunda lectura', 'S', 5, 'LITURGICAL', 1, 0, NULL),

        ('aleluya', 'Aleluya',
            'Aclamacion antes del Evangelio', 'A', 6, 'LITURGICAL', 1, 0,
            N'{"oculto_en":["cuaresma"]}'),

        ('aclamacion', 'Aclamacion del Evangelio',
            'Reemplaza al Aleluya durante la Cuaresma', 'A', 7, 'LITURGICAL', 1, 0,
            N'{"mostrado_solo_en":["cuaresma"]}'),

        ('ofertorio', 'Ofertorio',
            'Presentacion de las ofrendas de pan y vino', 'O', 8, 'LITURGICAL', 1, 0, NULL),

        ('santo', 'Santo (Sanctus)',
            'Santo, Santo, Santo es el Senor', 'S', 9, 'LITURGICAL', 1, 0, NULL),

        ('cordero', 'Cordero de Dios (Agnus Dei)',
            'Cordero de Dios que quitas el pecado del mundo', 'C', 10, 'LITURGICAL', 1, 0, NULL),

        ('comunion', 'Comunion',
            'Canto durante la distribucion de la Eucaristia', 'X', 11, 'LITURGICAL', 1, 0, NULL),

        ('firma_pliego', 'Firma del pliego matrimonial',
            'Momento solemne de la firma del acta de matrimonio', 'F', 12, 'NON_LITURGICAL', 1, 0, NULL),

        ('fotografias', 'Momento de fotografias',
            'Registro fotografico despues de la ceremonia', 'P', 13, 'NON_LITURGICAL', 4, 1, NULL),

        ('salida', 'Salida de los esposos',
            'Procesion de salida de los recien casados', 'X', 14, 'NON_LITURGICAL', 1, 1, NULL)
END
GO
