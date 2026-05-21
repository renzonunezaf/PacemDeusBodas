-- =============================================================================
-- seed_20_bodas.sql
-- Pacem Deus Bodas - Seed de demo con 20 bodas en sabados, iglesias
-- y locales reales de Lima Metropolitana / Pachacamac / Cieneguilla.
--
-- ESTRATEGIA DE PRECIOS:
--   Las bodas se crean con precio_base=0, precio_instrumentos=0,
--   precio_movilidad=0, precio_total=0. Despues se corre el script
--   Python cotizar_seed.py que calcula los precios reales con la
--   misma logica que usa el backend en produccion (paquete + numero
--   de instrumentos + distancia Haversine/Google a la parroquia base)
--   y los guarda via UPDATE.
--
-- ROLLBACK:
--   Para borrar TODO lo creado por este seed, ejecuta:
--     rollback_seed.sql
--
-- DISTRIBUCION:
--   20 bodas total
--   - 6 CONTRACTED (incluye los conflictos forzados):
--       * 2 el 2026-05-30 (12:00 y 19:00) -> dia rojo
--       * 1 el 2026-06-06 a las 12:00      -> dia ambar
--       * 3 dispersas en otros sabados
--   - 5 APPROVED
--   - 5 SUBMITTED
--   - 4 DRAFT
--
--   Wedding planner: 65% (13 de 20). Las 6 CONTRACTED siempre con planner.
--   Distribuidas entre los 3 planners (wedding1/2/3).
--
-- IDEMPOTENCIA:
--   Si vuelves a correrlo, vas a duplicar todo. Si quieres re-correr,
--   ejecuta primero rollback_seed.sql.
-- =============================================================================


DECLARE @password_hash VARCHAR(256) = '$2b$12$Vo12ng4aV7gNG7FhHCRuUumDirIRjjP6gJLivpumyUFiYpR2g2QsC'
DECLARE @marker VARCHAR(50) = 'SEED_DEMO_20'

-- ─────────────────────────────────────────────────────────────────────
-- IDs de planners (mantener fijos para insert de bodas)
-- ─────────────────────────────────────────────────────────────────────
DECLARE @planner1_id INT, @planner2_id INT, @planner3_id INT
SELECT @planner1_id = id_planner FROM planner WHERE nombre = 'Carla Mendoza'
SELECT @planner2_id = id_planner FROM planner WHERE nombre = 'Lucia Salazar'
SELECT @planner3_id = id_planner FROM planner WHERE nombre = 'Diego Fuentes'

IF @planner1_id IS NULL OR @planner2_id IS NULL OR @planner3_id IS NULL
BEGIN
    RAISERROR('Faltan wedding planners. Ejecuta primero migracion_v4_planners_seed.sql', 16, 1)
    RETURN
END


-- =============================================================================
-- TABLA TEMPORAL: definicion de las 20 bodas
-- =============================================================================
DECLARE @bodas TABLE (
    n               INT,
    email           VARCHAR(256),
    nombre_novio    VARCHAR(256),
    nombre_novia    VARCHAR(256),
    doc_novio       VARCHAR(20),
    doc_novia       VARCHAR(20),
    telefono        VARCHAR(30),
    fecha           DATE,
    hora            TIME,
    nombre_local    VARCHAR(256),
    direccion_local VARCHAR(500),
    lat             FLOAT,
    lng             FLOAT,
    estado          VARCHAR(30),
    id_planner      INT,
    instrumentos    VARCHAR(500)   -- CSV de slugs adicionales sobre la base
)

INSERT INTO @bodas VALUES
-- Sabado 30/05/2026 - 2 CONTRACTED (dia rojo)
(1, 'novios.boda01@correo.com', 'Andres Carrillo', 'Sofia Tovar',
 '40123456', '40123457', '+51987100001',
 '2026-05-30', '12:00', 'Parroquia Virgen de Fatima',
 'Av. Salaverry 2010, Jesus Maria, Lima', -12.0907, -77.0489,
 'CONTRACTED', @planner1_id, 'voz_femenina_2,violin_1'),

(2, 'novios.boda02@correo.com', 'Mateo Rivas', 'Camila Bustamante',
 '41123456', '41123457', '+51987100002',
 '2026-05-30', '19:00', 'Parroquia San Pedro',
 'Jr. Azangaro 451, Cercado de Lima', -12.0494, -77.0317,
 'CONTRACTED', @planner2_id, 'voz_femenina_2,voz_masculina'),

-- Sabado 06/06/2026 - 1 CONTRACTED a las 12 (dia ambar)
(3, 'novios.boda03@correo.com', 'Joaquin Salinas', 'Valeria Manrique',
 '42123456', '42123457', '+51987100003',
 '2026-06-06', '12:00', 'Hacienda Tres Canas',
 'Antigua Panamericana Sur Km 31.5, Pachacamac', -12.2294, -76.8589,
 'CONTRACTED', @planner3_id, 'voz_femenina_2,voz_femenina_3,violin_1'),

-- Sabado 13/06/2026
(4, 'novios.boda04@correo.com', 'Fernando Quispe', 'Adriana Vega',
 '43123456', '43123457', '+51987100004',
 '2026-06-13', '16:00', 'Parroquia Nuestra Senora del Pilar',
 'Av. Salaverry 2280, San Isidro', -12.0930, -77.0498,
 'APPROVED', @planner1_id, 'voz_femenina_2,violin_1'),

(5, 'novios.boda05@correo.com', 'Bruno Castillo', 'Daniela Ramos',
 '44123456', '44123457', '+51987100005',
 '2026-06-13', '18:00', 'Mesa de Piedra',
 'Camino al Cerro Manchay, Cieneguilla', -12.1145, -76.7956,
 'SUBMITTED', NULL, 'voz_femenina_2,voz_masculina'),

-- Sabado 20/06/2026
(6, 'novios.boda06@correo.com', 'Diego Espinoza', 'Lucia Mendez',
 '45123456', '45123457', '+51987100006',
 '2026-06-20', '17:00', 'Parroquia Santa Maria Reina',
 'Av. Pardo y Aliaga 555, San Isidro', -12.1010, -77.0381,
 'CONTRACTED', @planner1_id, 'voz_femenina_2,violin_1'),

-- Sabado 27/06/2026
(7, 'novios.boda07@correo.com', 'Sergio Aguirre', 'Carolina Salazar',
 '46123456', '46123457', '+51987100007',
 '2026-06-27', '15:00', 'El Jardin de Cieneguilla',
 'Antigua Carretera Central Km 28, Cieneguilla', -12.1230, -76.7889,
 'APPROVED', @planner2_id, 'voz_femenina_2,voz_femenina_3'),

-- Sabado 04/07/2026
(8, 'novios.boda08@correo.com', 'Renato Pacheco', 'Isabella Cordova',
 '47123456', '47123457', '+51987100008',
 '2026-07-04', '14:00', 'Hacienda Darenas',
 'Antigua Panamericana Sur Km 30, Pachacamac', -12.2261, -76.8616,
 'CONTRACTED', @planner2_id, 'voz_femenina_2,voz_masculina,violin_1'),

-- Sabado 11/07/2026
(9, 'novios.boda09@correo.com', 'Alvaro Bocanegra', 'Mariana Delgado',
 '48123456', '48123457', '+51987100009',
 '2026-07-11', '13:00', 'Parroquia Sagrada Familia',
 'Av. Brasil 2790, Magdalena del Mar', -12.0921, -77.0641,
 'SUBMITTED', @planner3_id, 'voz_femenina_2'),

(10, 'novios.boda10@correo.com', 'Ignacio Ramirez', 'Paula Velez',
 '49123456', '49123457', '+51987100010',
 '2026-07-11', '19:00', 'Caballeriza Mamacona',
 'Antigua Panamericana Sur Km 30, Lurin', -12.2645, -76.8722,
 'DRAFT', NULL, 'voz_femenina_2,violin_1'),

-- Sabado 18/07/2026
(11, 'novios.boda11@correo.com', 'Gonzalo Llerena', 'Romina Castro',
 '50123456', '50123457', '+51987100011',
 '2026-07-18', '16:00', 'Parroquia San Felipe',
 'Av. Gregorio Escobedo 360, Jesus Maria', -12.0825, -77.0468,
 'APPROVED', @planner1_id, 'voz_femenina_2,voz_femenina_3'),

-- Sabado 01/08/2026
(12, 'novios.boda12@correo.com', 'Esteban Solano', 'Fernanda Iturrizaga',
 '51123456', '51123457', '+51987100012',
 '2026-08-01', '17:00', 'Casa Hacienda Lurin',
 'Antigua Panamericana Sur Km 33, Lurin', -12.2792, -76.8688,
 'CONTRACTED', @planner3_id, 'voz_femenina_2,violin_1'),

-- Sabado 15/08/2026
(13, 'novios.boda13@correo.com', 'Maximiliano Pinto', 'Ariana Vasquez',
 '52123456', '52123457', '+51987100013',
 '2026-08-15', '15:00', 'Parroquia Santa Rosa de Lima',
 'Av. Tacna cuadra 1, Cercado de Lima', -12.0468, -77.0341,
 'SUBMITTED', @planner2_id, 'voz_femenina_2'),

(14, 'novios.boda14@correo.com', 'Nicolas Talledo', 'Andrea Salcedo',
 '53123456', '53123457', '+51987100014',
 '2026-08-22', '14:00', 'Fundo Mamacona',
 'Antigua Panamericana Sur Km 30, Lurin', -12.2675, -76.8745,
 'APPROVED', @planner3_id, 'voz_femenina_2,voz_femenina_3,voz_masculina'),

-- Sabado 05/09/2026
(15, 'novios.boda15@correo.com', 'Rafael Yepez', 'Patricia Olaechea',
 '54123456', '54123457', '+51987100015',
 '2026-09-05', '18:00', 'El Pedregal Cieneguilla',
 'Av. Las Palmeras 1234, Cieneguilla', -12.1158, -76.7912,
 'SUBMITTED', NULL, 'voz_femenina_2,violin_1'),

-- Sabado 19/09/2026
(16, 'novios.boda16@correo.com', 'Cesar Bermudez', 'Alessandra Ponce',
 '55123456', '55123457', '+51987100016',
 '2026-09-19', '16:00', 'Parroquia Santo Toribio',
 'Av. Rivera Navarrete 698, San Isidro', -12.0962, -77.0349,
 'CONTRACTED', @planner1_id, 'voz_femenina_2,voz_masculina'),

-- Sabado 10/10/2026
(17, 'novios.boda17@correo.com', 'Sebastian Ugarte', 'Gabriela Higa',
 '56123456', '56123457', '+51987100017',
 '2026-10-10', '13:00', 'Hacienda Casa Blanca',
 'Antigua Panamericana Sur Km 31, Pachacamac', -12.2278, -76.8631,
 'APPROVED', @planner2_id, 'voz_femenina_2,voz_femenina_3,violin_1'),

-- Sabado 24/10/2026
(18, 'novios.boda18@correo.com', 'Felipe Cisneros', 'Maria Pia Vera',
 '57123456', '57123457', '+51987100018',
 '2026-10-24', '17:00', 'El Mirador de Cieneguilla',
 'Av. Nueva Toledo 980, Cieneguilla', -12.1185, -76.7889,
 'DRAFT', NULL, 'voz_femenina_2'),

-- Sabado 14/11/2026
(19, 'novios.boda19@correo.com', 'Lucas Heredia', 'Constanza Arrieta',
 '58123456', '58123457', '+51987100019',
 '2026-11-14', '15:00', 'Casa de Eventos Pachacamac',
 'Av. Manuel Valle s/n, Pachacamac', -12.2245, -76.8612,
 'DRAFT', NULL, 'voz_femenina_2,voz_masculina,violin_1'),

-- Sabado 12/12/2026
(20, 'novios.boda20@correo.com', 'Tomas Murguia', 'Antonella Riesco',
 '59123456', '59123457', '+51987100020',
 '2026-12-12', '17:00', 'Hacienda La Caleta',
 'Antigua Panamericana Sur Km 32, Lurin', -12.2748, -76.8718,
 'DRAFT', NULL, 'voz_femenina_2,voz_femenina_3')


-- =============================================================================
-- INSERTAR USUARIOS + NOVIOS + BODAS + INSTRUMENTOS + SETLIST
-- =============================================================================

-- Para iterar usamos cursor (no es performance critico, son 20 filas)
DECLARE @n INT, @email VARCHAR(256), @nombre_novio VARCHAR(256),
        @nombre_novia VARCHAR(256), @doc_novio VARCHAR(20),
        @doc_novia VARCHAR(20), @telefono VARCHAR(30),
        @fecha DATE, @hora TIME, @nombre_local VARCHAR(256),
        @direccion_local VARCHAR(500), @lat FLOAT, @lng FLOAT,
        @estado VARCHAR(30), @id_planner INT, @instrumentos VARCHAR(500)

DECLARE @id_usuario INT, @id_novios INT, @id_boda INT

DECLARE cur CURSOR FOR
SELECT n, email, nombre_novio, nombre_novia, doc_novio, doc_novia,
       telefono, fecha, hora, nombre_local, direccion_local, lat, lng,
       estado, id_planner, instrumentos
FROM @bodas ORDER BY n

OPEN cur
FETCH NEXT FROM cur INTO @n, @email, @nombre_novio, @nombre_novia,
    @doc_novio, @doc_novia, @telefono, @fecha, @hora, @nombre_local,
    @direccion_local, @lat, @lng, @estado, @id_planner, @instrumentos

WHILE @@FETCH_STATUS = 0
BEGIN
    -- 1. Usuario
    INSERT INTO usuario (email, password_hash, rol, marker_seed)
    VALUES (@email, @password_hash, 'COUPLE', @marker)
    SET @id_usuario = SCOPE_IDENTITY()

    -- 2. Novios
    INSERT INTO novios (id_usuario, nombre_novio, nombre_novia,
                        tipo_doc_novio, documento_novio,
                        tipo_doc_novia, documento_novia, telefono,
                        como_se_entero)
    VALUES (@id_usuario, @nombre_novio, @nombre_novia,
            'DNI', @doc_novio, 'DNI', @doc_novia, @telefono, 'OTRO')
    SET @id_novios = SCOPE_IDENTITY()

    -- 3. Boda (precios en 0; el script Python los recalcula despues)
    INSERT INTO boda (id_novios, id_planner, fecha_boda, hora_boda,
                      nombre_local, direccion_local, latitud, longitud,
                      fuera_de_lima, precio_base, precio_instrumentos,
                      precio_movilidad, precio_total, estado, marker_seed)
    VALUES (@id_novios, @id_planner, @fecha, @hora, @nombre_local,
            @direccion_local, @lat, @lng, 0, 0, 0, 0, 0, @estado, @marker)
    SET @id_boda = SCOPE_IDENTITY()

    -- 4. Instrumentos adicionales segun la lista del seed
    IF @instrumentos IS NOT NULL AND LEN(@instrumentos) > 0
    BEGIN
        INSERT INTO boda_instrumento (id_boda, id_instrumento)
        SELECT  @id_boda, i.id_instrumento
        FROM    instrumento i
        WHERE   i.slug IN (SELECT value FROM STRING_SPLIT(@instrumentos, ','))
          AND   i.activo = 1
    END

    -- 5. Setlist completo: 8 momentos liturgicos con un canto aleatorio
    -- cada uno. Usamos slugs (estables) y un subquery correlacionado
    -- para que cada momento reciba su propio canto random.
    INSERT INTO setlist (id_boda, id_momento, id_cancion, orden)
    SELECT  @id_boda,
            m.id_momento,
            (SELECT TOP 1 id_cancion FROM cancion ORDER BY NEWID()),
            m.orden
    FROM    momento_liturgico m
    WHERE   m.slug IN ('entrada_novia','gloria','aleluya','ofertorio',
                       'santo','comunion','firma_pliego','salida')

    -- 6. Contrato (todas las bodas SUBMITTED+ tienen contrato existente)
    IF @estado IN ('SUBMITTED','APPROVED','CONTRACTED','COMPLETED')
    BEGIN
        INSERT INTO contrato (id_boda, firmado_novios, firmado_admin)
        VALUES (@id_boda,
                CASE WHEN @estado IN ('CONTRACTED','COMPLETED') THEN 1 ELSE 0 END,
                CASE WHEN @estado IN ('CONTRACTED','COMPLETED') THEN 1 ELSE 0 END)
    END

    FETCH NEXT FROM cur INTO @n, @email, @nombre_novio, @nombre_novia,
        @doc_novio, @doc_novia, @telefono, @fecha, @hora, @nombre_local,
        @direccion_local, @lat, @lng, @estado, @id_planner, @instrumentos
END

CLOSE cur
DEALLOCATE cur


-- =============================================================================
-- VERIFICACION
-- =============================================================================
PRINT '============================================================'
PRINT 'Seed completado. Resumen:'
PRINT '============================================================'

SELECT  COUNT(*) AS total_bodas,
        SUM(CASE WHEN estado = 'CONTRACTED' THEN 1 ELSE 0 END) AS contracted,
        SUM(CASE WHEN estado = 'APPROVED' THEN 1 ELSE 0 END) AS approved,
        SUM(CASE WHEN estado = 'SUBMITTED' THEN 1 ELSE 0 END) AS submitted,
        SUM(CASE WHEN estado = 'DRAFT' THEN 1 ELSE 0 END) AS draft
FROM    boda WHERE marker_seed = @marker

PRINT 'IMPORTANTE: ejecuta cotizar_seed.py para calcular precios reales.'
GO
