-- =============================================================================
-- 07_seed_season_dates.sql
-- Fechas exactas de cada tiempo liturgico por anio (2025-2027).
-- =============================================================================

IF NOT EXISTS (SELECT 1 FROM temporada_fechas)
BEGIN
    INSERT INTO temporada_fechas(id_temporada, anio, fecha_inicio, fecha_fin)
    SELECT t.id_temporada, d.anio, d.fecha_inicio, d.fecha_fin
    FROM (
        VALUES
            -- 2025
            ('ordinario',  2025, '2025-01-13', '2025-03-04'),
            ('cuaresma',   2025, '2025-03-05', '2025-04-19'),
            ('pascual',    2025, '2025-04-20', '2025-06-08'),
            ('adviento',   2025, '2025-11-30', '2025-12-24'),
            ('navidad',    2025, '2025-12-25', '2026-01-11'),
            -- 2026
            ('ordinario',  2026, '2026-01-12', '2026-02-17'),
            ('cuaresma',   2026, '2026-02-18', '2026-04-04'),
            ('pascual',    2026, '2026-04-05', '2026-05-24'),
            ('adviento',   2026, '2026-11-29', '2026-12-24'),
            ('navidad',    2026, '2026-12-25', '2027-01-10'),
            -- 2027
            ('ordinario',  2027, '2027-01-11', '2027-02-09'),
            ('cuaresma',   2027, '2027-02-10', '2027-03-27'),
            ('pascual',    2027, '2027-03-28', '2027-05-16'),
            ('adviento',   2027, '2027-11-28', '2027-12-24'),
            ('navidad',    2027, '2027-12-25', '2028-01-09')
    ) AS d(slug, anio, fecha_inicio, fecha_fin)
    INNER JOIN temporada_liturgica t ON t.slug = d.slug
END
GO
