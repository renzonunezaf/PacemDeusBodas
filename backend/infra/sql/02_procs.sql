-- =============================================================================
-- 02_procs.sql
-- Stored Procedures siguiendo el patron del profesor: SP por operacion CRUD.
-- Las Lambdas invocan estos SPs y solo orquestan la respuesta.
--
-- Nomenclatura: usp_<entidad>_<accion>
-- =============================================================================

-- =============================================================================
-- USUARIO Y AUTENTICACION
-- =============================================================================

CREATE OR ALTER PROC usp_usuario_obtener_por_email
(
    @email VARCHAR(256)
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  u.id_usuario,
            u.email,
            u.password_hash,
            u.rol,
            u.activo
    FROM    usuario u
    WHERE   LOWER(u.email) = LOWER(@email)
END
GO

CREATE OR ALTER PROC usp_usuario_obtener_por_id
(
    @id_usuario INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  u.id_usuario,
            u.email,
            u.rol,
            u.activo,
            u.fecha_creacion
    FROM    usuario u
    WHERE   u.id_usuario = @id_usuario
END
GO

CREATE OR ALTER PROC usp_usuario_crear
(
    @email          VARCHAR(256),
    @password_hash  VARCHAR(256),
    @rol            VARCHAR(20)
)
AS
BEGIN
    SET NOCOUNT ON
    INSERT INTO usuario(email, password_hash, rol)
    VALUES (@email, @password_hash, @rol)

    SELECT SCOPE_IDENTITY() AS id_usuario
END
GO

CREATE OR ALTER PROC usp_novios_crear
(
    @id_usuario         INT,
    @nombre_novio       VARCHAR(256),
    @nombre_novia       VARCHAR(256),
    @tipo_doc_novio     VARCHAR(15),
    @tipo_doc_novia     VARCHAR(15),
    @documento_novio    VARCHAR(20),
    @documento_novia    VARCHAR(20),
    @telefono           VARCHAR(30),
    @como_se_entero     VARCHAR(30)
)
AS
BEGIN
    SET NOCOUNT ON
    INSERT INTO novios(
        id_usuario, nombre_novio, nombre_novia,
        tipo_doc_novio, tipo_doc_novia,
        documento_novio, documento_novia,
        telefono, como_se_entero
    )
    VALUES (
        @id_usuario, @nombre_novio, @nombre_novia,
        @tipo_doc_novio, @tipo_doc_novia,
        @documento_novio, @documento_novia,
        @telefono, @como_se_entero
    )

    SELECT SCOPE_IDENTITY() AS id_novios
END
GO

CREATE OR ALTER PROC usp_novios_obtener_por_usuario
(
    @id_usuario INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  n.id_novios,
            n.id_usuario,
            n.nombre_novio,
            n.nombre_novia,
            n.tipo_doc_novio,
            n.tipo_doc_novia,
            n.documento_novio,
            n.documento_novia,
            n.telefono,
            n.como_se_entero
    FROM    novios n
    WHERE   n.id_usuario = @id_usuario
END
GO

CREATE OR ALTER PROC usp_planner_crear
(
    @id_usuario INT,
    @nombre     VARCHAR(256),
    @empresa    VARCHAR(256),
    @telefono   VARCHAR(30)
)
AS
BEGIN
    SET NOCOUNT ON
    INSERT INTO planner(id_usuario, nombre, empresa, telefono)
    VALUES (@id_usuario, @nombre, @empresa, @telefono)

    SELECT SCOPE_IDENTITY() AS id_planner
END
GO

CREATE OR ALTER PROC usp_planner_obtener_por_usuario
(
    @id_usuario INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  p.id_planner,
            p.id_usuario,
            p.nombre,
            p.empresa,
            p.telefono
    FROM    planner p
    WHERE   p.id_usuario = @id_usuario
END
GO

CREATE OR ALTER PROC usp_planner_listar
AS
BEGIN
    SET NOCOUNT ON
    SELECT  p.id_planner,
            p.nombre,
            p.empresa,
            p.telefono,
            u.email,
            u.activo,
            (SELECT COUNT(*) FROM boda b WHERE b.id_planner = p.id_planner) AS bodas_asignadas,
            (SELECT COUNT(*) FROM boda b
             WHERE b.id_planner = p.id_planner
               AND b.estado <> 'COMPLETED'
               AND b.fecha_boda >= CAST(GETDATE() AS DATE)) AS bodas_proximas
    FROM    planner p
    INNER JOIN usuario u ON u.id_usuario = p.id_usuario
    WHERE   u.activo = 1
    ORDER BY p.nombre
END
GO

-- =============================================================================
-- CATALOGO
-- =============================================================================

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
            orden
    FROM    instrumento
    WHERE   activo = 1
    ORDER BY orden ASC
END
GO

CREATE OR ALTER PROC usp_momentos_listar
AS
BEGIN
    SET NOCOUNT ON
    SELECT  id_momento,
            slug,
            nombre,
            descripcion,
            icono,
            orden,
            categoria,
            max_canciones,
            permite_repetidas,
            restricciones_temporada
    FROM    momento_liturgico
    WHERE   activo = 1
    ORDER BY orden ASC
END
GO

CREATE OR ALTER PROC usp_temporada_obtener_por_fecha
(
    @fecha DATE
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT TOP 1
            t.id_temporada,
            t.slug,
            t.nombre,
            t.restricciones
    FROM    temporada_fechas tf
    INNER JOIN temporada_liturgica t ON t.id_temporada = tf.id_temporada
    WHERE   @fecha BETWEEN tf.fecha_inicio AND tf.fecha_fin
END
GO

CREATE OR ALTER PROC usp_canciones_listar
(
    @id_momento INT = NULL,
    @criterio   VARCHAR(256) = NULL,
    @idioma     VARCHAR(5) = NULL
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  s.id_cancion,
            s.titulo,
            s.autor,
            s.idioma,
            s.es_liturgica,
            s.voz_recomendada
    FROM    cancion s
    LEFT JOIN cancion_momento sm ON sm.id_cancion = s.id_cancion
    WHERE   s.activa = 1
        AND (@id_momento IS NULL OR sm.id_momento = @id_momento)
        AND (@criterio IS NULL
             OR s.titulo LIKE '%' + @criterio + '%'
             OR s.autor LIKE '%' + @criterio + '%')
        AND (@idioma IS NULL OR s.idioma = @idioma)
    GROUP BY s.id_cancion, s.titulo, s.autor, s.idioma, s.es_liturgica, s.voz_recomendada
    ORDER BY s.titulo ASC
END
GO

CREATE OR ALTER PROC usp_cancion_requerimientos_minimos
(
    @id_cancion INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  i.slug, i.nombre
    FROM    cancion_requerimiento cr
    INNER JOIN instrumento i ON i.id_instrumento = cr.id_instrumento
    WHERE   cr.id_cancion = @id_cancion
        AND cr.tipo = 'MINIMUM'
END
GO

-- =============================================================================
-- BODAS
-- =============================================================================

CREATE OR ALTER PROC usp_bodas_listar
(
    @id_novios  INT = NULL,
    @id_planner INT = NULL,
    @estado     VARCHAR(30) = NULL
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  b.id_boda,
            b.fecha_boda,
            b.hora_boda,
            b.nombre_local,
            b.direccion_local,
            b.latitud,
            b.longitud,
            b.foto_local_url,
            b.fuera_de_lima,
            b.precio_base,
            b.precio_instrumentos,
            b.precio_movilidad,
            b.precio_total,
            b.estado,
            b.notas,
            n.id_novios,
            n.nombre_novio,
            n.nombre_novia,
            n.telefono AS telefono_novios,
            p.id_planner,
            p.nombre AS nombre_planner,
            p.empresa AS empresa_planner,
            p.telefono AS telefono_planner
    FROM    boda b
    INNER JOIN novios n ON n.id_novios = b.id_novios
    LEFT JOIN  planner p ON p.id_planner = b.id_planner
    WHERE   (@id_novios IS NULL OR b.id_novios = @id_novios)
        AND (@id_planner IS NULL OR b.id_planner = @id_planner)
        AND (@estado IS NULL OR b.estado = @estado)
    ORDER BY b.fecha_boda DESC, b.fecha_creacion DESC
END
GO

CREATE OR ALTER PROC usp_boda_obtener
(
    @id_boda INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  b.id_boda,
            b.id_novios,
            b.id_planner,
            b.fecha_boda,
            b.hora_boda,
            b.nombre_local,
            b.direccion_local,
            b.latitud,
            b.longitud,
            b.foto_local_url,
            b.fuera_de_lima,
            b.precio_base,
            b.precio_instrumentos,
            b.precio_movilidad,
            b.precio_total,
            b.estado,
            b.notas,
            b.fecha_creacion,
            n.nombre_novio,
            n.nombre_novia,
            n.telefono AS telefono_novios,
            n.documento_novio,
            n.documento_novia,
            p.nombre AS nombre_planner,
            p.empresa AS empresa_planner,
            p.telefono AS telefono_planner
    FROM    boda b
    INNER JOIN novios n ON n.id_novios = b.id_novios
    LEFT JOIN  planner p ON p.id_planner = b.id_planner
    WHERE   b.id_boda = @id_boda
END
GO

CREATE OR ALTER PROC usp_boda_crear
(
    @id_novios          INT,
    @fecha_boda         DATE,
    @hora_boda          VARCHAR(5),
    @nombre_local       VARCHAR(256),
    @direccion_local    VARCHAR(500),
    @latitud            FLOAT,
    @longitud           FLOAT,
    @fuera_de_lima      BIT,
    @precio_base        DECIMAL(10,2),
    @precio_instrumentos DECIMAL(10,2),
    @precio_movilidad   DECIMAL(10,2),
    @precio_total       DECIMAL(10,2)
)
AS
BEGIN
    SET NOCOUNT ON
    INSERT INTO boda(
        id_novios, fecha_boda, hora_boda,
        nombre_local, direccion_local, latitud, longitud,
        fuera_de_lima,
        precio_base, precio_instrumentos, precio_movilidad, precio_total,
        estado
    )
    VALUES (
        @id_novios, @fecha_boda, @hora_boda,
        @nombre_local, @direccion_local, @latitud, @longitud,
        @fuera_de_lima,
        @precio_base, @precio_instrumentos, @precio_movilidad, @precio_total,
        'DRAFT'
    )

    DECLARE @id_boda INT = SCOPE_IDENTITY()

    -- Crea contrato vacio asociado
    INSERT INTO contrato(id_boda) VALUES (@id_boda)

    SELECT @id_boda AS id_boda
END
GO

CREATE OR ALTER PROC usp_boda_actualizar_precios
(
    @id_boda                INT,
    @fuera_de_lima          BIT,
    @precio_base            DECIMAL(10,2),
    @precio_instrumentos    DECIMAL(10,2),
    @precio_movilidad       DECIMAL(10,2),
    @precio_total           DECIMAL(10,2)
)
AS
BEGIN
    SET NOCOUNT ON
    UPDATE boda
    SET fuera_de_lima       = @fuera_de_lima,
        precio_base         = @precio_base,
        precio_instrumentos = @precio_instrumentos,
        precio_movilidad    = @precio_movilidad,
        precio_total        = @precio_total
    WHERE id_boda = @id_boda
END
GO

CREATE OR ALTER PROC usp_boda_cambiar_estado
(
    @id_boda    INT,
    @estado     VARCHAR(30),
    @notas      VARCHAR(2000) = NULL
)
AS
BEGIN
    SET NOCOUNT ON
    UPDATE boda
    SET estado = @estado,
        notas = COALESCE(@notas, notas)
    WHERE id_boda = @id_boda
END
GO

CREATE OR ALTER PROC usp_boda_actualizar_foto
(
    @id_boda    INT,
    @foto_url   VARCHAR(500)
)
AS
BEGIN
    SET NOCOUNT ON
    UPDATE boda SET foto_local_url = @foto_url WHERE id_boda = @id_boda
END
GO

CREATE OR ALTER PROC usp_boda_eliminar
(
    @id_boda INT
)
AS
BEGIN
    SET NOCOUNT ON
    DELETE FROM boda WHERE id_boda = @id_boda
END
GO

CREATE OR ALTER PROC usp_boda_asignar_planner
(
    @id_boda    INT,
    @id_planner INT
)
AS
BEGIN
    SET NOCOUNT ON
    UPDATE boda SET id_planner = @id_planner WHERE id_boda = @id_boda
END
GO

-- =============================================================================
-- INSTRUMENTOS DE BODA
-- =============================================================================

CREATE OR ALTER PROC usp_boda_instrumentos_listar
(
    @id_boda INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  i.id_instrumento, i.slug, i.nombre, i.icono, i.es_voz, i.canta_ingles
    FROM    boda_instrumento bi
    INNER JOIN instrumento i ON i.id_instrumento = bi.id_instrumento
    WHERE   bi.id_boda = @id_boda
    ORDER BY i.orden ASC
END
GO

CREATE OR ALTER PROC usp_boda_instrumentos_reemplazar
(
    @id_boda    INT,
    @slugs_csv  VARCHAR(2000)
)
AS
BEGIN
    SET NOCOUNT ON
    -- Borra los actuales
    DELETE FROM boda_instrumento WHERE id_boda = @id_boda

    -- Inserta los nuevos a partir del CSV
    INSERT INTO boda_instrumento(id_boda, id_instrumento)
    SELECT  @id_boda, i.id_instrumento
    FROM    instrumento i
    WHERE   i.activo = 1
        AND i.slug IN (
            SELECT TRIM(value)
            FROM STRING_SPLIT(@slugs_csv, ',')
            WHERE LEN(TRIM(value)) > 0
        )
END
GO

-- =============================================================================
-- SETLIST
-- =============================================================================

CREATE OR ALTER PROC usp_setlist_listar
(
    @id_boda INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  sl.id_setlist,
            sl.orden,
            m.id_momento, m.slug AS slug_momento, m.nombre AS nombre_momento,
            m.orden AS orden_momento, m.icono AS icono_momento,
            s.id_cancion, s.titulo, s.autor, s.idioma, s.es_liturgica,
            s.voz_recomendada
    FROM    setlist sl
    INNER JOIN momento_liturgico m ON m.id_momento = sl.id_momento
    INNER JOIN cancion s ON s.id_cancion = sl.id_cancion
    WHERE   sl.id_boda = @id_boda
    ORDER BY m.orden ASC, sl.orden ASC
END
GO

CREATE OR ALTER PROC usp_setlist_validar_compatibilidad
(
    @id_cancion INT,
    @id_momento INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT COUNT(*) AS compatible
    FROM cancion_momento
    WHERE id_cancion = @id_cancion AND id_momento = @id_momento
END
GO

CREATE OR ALTER PROC usp_setlist_agregar
(
    @id_boda    INT,
    @id_momento INT,
    @id_cancion INT
)
AS
BEGIN
    SET NOCOUNT ON

    -- Verifica que no este duplicado
    IF EXISTS (
        SELECT 1 FROM setlist
        WHERE id_boda = @id_boda
          AND id_momento = @id_momento
          AND id_cancion = @id_cancion
    )
    BEGIN
        SELECT 0 AS id_setlist, 'duplicado' AS error
        RETURN
    END

    -- Verifica capacidad maxima del momento
    DECLARE @actual INT, @maximo INT
    SELECT @actual = COUNT(*) FROM setlist
        WHERE id_boda = @id_boda AND id_momento = @id_momento
    SELECT @maximo = max_canciones FROM momento_liturgico
        WHERE id_momento = @id_momento

    IF @actual >= @maximo
    BEGIN
        SELECT 0 AS id_setlist, 'limite_alcanzado' AS error
        RETURN
    END

    INSERT INTO setlist(id_boda, id_momento, id_cancion, orden)
    VALUES (@id_boda, @id_momento, @id_cancion, @actual)

    SELECT SCOPE_IDENTITY() AS id_setlist, NULL AS error
END
GO

CREATE OR ALTER PROC usp_setlist_quitar
(
    @id_setlist INT
)
AS
BEGIN
    SET NOCOUNT ON

    DECLARE @id_boda INT, @id_momento INT, @orden INT
    SELECT  @id_boda = id_boda,
            @id_momento = id_momento,
            @orden = orden
    FROM    setlist
    WHERE   id_setlist = @id_setlist

    IF @id_boda IS NULL
    BEGIN
        SELECT 0 AS eliminado
        RETURN
    END

    DELETE FROM setlist WHERE id_setlist = @id_setlist

    -- Reordena los restantes en el mismo momento
    UPDATE setlist
    SET orden = orden - 1
    WHERE id_boda = @id_boda
      AND id_momento = @id_momento
      AND orden > @orden

    SELECT 1 AS eliminado
END
GO

CREATE OR ALTER PROC usp_setlist_obtener
(
    @id_setlist INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  sl.id_setlist,
            sl.id_boda,
            sl.orden,
            m.id_momento, m.slug AS slug_momento, m.nombre AS nombre_momento,
            s.id_cancion, s.titulo, s.autor, s.idioma, s.es_liturgica
    FROM    setlist sl
    INNER JOIN momento_liturgico m ON m.id_momento = sl.id_momento
    INNER JOIN cancion s ON s.id_cancion = sl.id_cancion
    WHERE   sl.id_setlist = @id_setlist
END
GO

-- =============================================================================
-- CONTRATO
-- =============================================================================

CREATE OR ALTER PROC usp_contrato_obtener
(
    @id_boda INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  id_contrato,
            id_boda,
            pdf_url,
            firmado_novios,
            fecha_firma_novios,
            firmante_novios,
            firmado_admin,
            fecha_firma_admin,
            firmante_admin,
            fecha_creacion
    FROM    contrato
    WHERE   id_boda = @id_boda
END
GO

CREATE OR ALTER PROC usp_contrato_firmar_novios
(
    @id_boda    INT,
    @firmante   VARCHAR(256),
    @ip         VARCHAR(50)
)
AS
BEGIN
    SET NOCOUNT ON
    UPDATE contrato
    SET firmado_novios = 1,
        fecha_firma_novios = SYSDATETIMEOFFSET(),
        firmante_novios = @firmante,
        ip_firma_novios = @ip
    WHERE id_boda = @id_boda
END
GO

CREATE OR ALTER PROC usp_contrato_firmar_admin
(
    @id_boda    INT,
    @firmante   VARCHAR(256)
)
AS
BEGIN
    SET NOCOUNT ON
    UPDATE contrato
    SET firmado_admin = 1,
        fecha_firma_admin = SYSDATETIMEOFFSET(),
        firmante_admin = @firmante
    WHERE id_boda = @id_boda
END
GO

CREATE OR ALTER PROC usp_contrato_estado_firmas
(
    @id_boda INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT firmado_novios, firmado_admin
    FROM contrato
    WHERE id_boda = @id_boda
END
GO

-- =============================================================================
-- PAGOS
-- =============================================================================

CREATE OR ALTER PROC usp_pagos_listar
(
    @id_boda INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  id_pago, monto, fecha_pago, banco, tipo_pago, notas, fecha_creacion
    FROM    pago
    WHERE   id_boda = @id_boda
    ORDER BY fecha_pago ASC
END
GO

CREATE OR ALTER PROC usp_pago_crear
(
    @id_boda    INT,
    @monto      DECIMAL(10,2),
    @fecha_pago DATE,
    @banco      VARCHAR(20),
    @tipo_pago  VARCHAR(15),
    @notas      VARCHAR(1000)
)
AS
BEGIN
    SET NOCOUNT ON
    INSERT INTO pago(id_boda, monto, fecha_pago, banco, tipo_pago, notas)
    VALUES (@id_boda, @monto, @fecha_pago, @banco, @tipo_pago, @notas)

    SELECT SCOPE_IDENTITY() AS id_pago
END
GO

-- =============================================================================
-- CONFIGURACION DE PRECIOS
-- =============================================================================

CREATE OR ALTER PROC usp_pricing_obtener
AS
BEGIN
    SET NOCOUNT ON
    SELECT TOP 1 *
    FROM configuracion_precios
END
GO

CREATE OR ALTER PROC usp_pricing_actualizar
(
    @precio_base_lima               DECIMAL(10,2),
    @precio_base_fuera              DECIMAL(10,2),
    @precio_instrumento_lima        DECIMAL(10,2),
    @precio_instrumento_fuera       DECIMAL(10,2),
    @movilidad_minima               DECIMAL(10,2),
    @movilidad_maxima               DECIMAL(10,2),
    @latitud_base                   FLOAT,
    @longitud_base                  FLOAT,
    @radio_lima_km                  FLOAT,
    @movilidad_km_libres            FLOAT,
    @movilidad_minutos_libres       FLOAT,
    @movilidad_tarifa_km            DECIMAL(10,2),
    @movilidad_tarifa_minuto        DECIMAL(10,2),
    @movilidad_grupo_grande         DECIMAL(10,2),
    @movilidad_umbral_grupo         INT,
    @movilidad_centro_historico     DECIMAL(10,2),
    @centro_norte_lat               FLOAT,
    @centro_sur_lat                 FLOAT,
    @centro_oeste_lng               FLOAT,
    @centro_este_lng                FLOAT
)
AS
BEGIN
    SET NOCOUNT ON
    UPDATE configuracion_precios
    SET precio_base_lima            = @precio_base_lima,
        precio_base_fuera           = @precio_base_fuera,
        precio_instrumento_lima     = @precio_instrumento_lima,
        precio_instrumento_fuera    = @precio_instrumento_fuera,
        movilidad_minima            = @movilidad_minima,
        movilidad_maxima            = @movilidad_maxima,
        latitud_base                = @latitud_base,
        longitud_base               = @longitud_base,
        radio_lima_km               = @radio_lima_km,
        movilidad_km_libres         = @movilidad_km_libres,
        movilidad_minutos_libres    = @movilidad_minutos_libres,
        movilidad_tarifa_km         = @movilidad_tarifa_km,
        movilidad_tarifa_minuto     = @movilidad_tarifa_minuto,
        movilidad_grupo_grande      = @movilidad_grupo_grande,
        movilidad_umbral_grupo      = @movilidad_umbral_grupo,
        movilidad_centro_historico  = @movilidad_centro_historico,
        centro_norte_lat            = @centro_norte_lat,
        centro_sur_lat              = @centro_sur_lat,
        centro_oeste_lng            = @centro_oeste_lng,
        centro_este_lng             = @centro_este_lng,
        fecha_actualizacion         = SYSDATETIMEOFFSET()
END
GO

PRINT 'Stored procedures creados correctamente'
GO
