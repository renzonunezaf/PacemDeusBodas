-- =============================================================================
-- Coro Pacem Deus Bodas - Esquema de Base de Datos
-- SQL Server 2019+ (compatible con AWS RDS SQL Server Express Edition)
--
-- Sigue el patron enseñado por el profesor en el laboratorio de la Semana 5:
--   - PKs INT IDENTITY(1,1) (no UUID)
--   - VARCHAR(256) para texto general
--   - FK con FOREIGN KEY ... REFERENCES
--   - CHECK constraints en lugar de tipos ENUM (SQL Server no tiene ENUM)
--   - Stored procedures en archivo separado (02_procs.sql)
--
-- Orden de ejecucion en SSMS:
--   01_schema.sql              <- Este archivo
--   02_procs.sql               <- Stored procedures
--   03_seed_pricing.sql        <- Configuracion de precios
--   04_seed_instruments.sql    <- Catalogo de instrumentos
--   05_seed_moments.sql        <- 14 momentos liturgicos
--   06_seed_seasons.sql        <- Tiempos liturgicos
--   07_seed_season_dates.sql   <- Fechas por anio
--   08_seed_songs.sql          <- 250 cantos del repertorio
--   09_seed_song_moments.sql   <- Relacion canto-momento
--   10_seed_song_requirements.sql <- Requerimientos instrumentales
--   11_seed_song_styles.sql    <- Estilos por canto
-- =============================================================================

-- Asegura que estamos en la base de datos correcta antes de crear nada
-- (En RDS SQL Server, conectarse a la BD master, crear DBMOV, luego USE DBMOV)
-- USE DBMOV
-- GO

-- =============================================================================
-- TABLA: usuario
-- Usuarios del sistema (novios, admins y wedding planners)
-- =============================================================================
CREATE TABLE usuario
(
    id_usuario      INT             PRIMARY KEY IDENTITY(1,1),
    email           VARCHAR(256)    NOT NULL UNIQUE,
    password_hash   VARCHAR(256)    NOT NULL,
    rol             VARCHAR(20)     NOT NULL DEFAULT 'COUPLE',
    activo          BIT             NOT NULL DEFAULT 1,
    fecha_creacion  DATETIMEOFFSET  NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CHECK (rol IN ('ADMIN', 'COUPLE', 'WEDDING_PLANNER'))
)
GO

CREATE INDEX idx_usuario_email ON usuario(email)
GO
CREATE INDEX idx_usuario_rol ON usuario(rol)
GO

-- =============================================================================
-- TABLA: novios
-- Perfil de la pareja de novios (extiende usuario con rol=COUPLE)
-- =============================================================================
CREATE TABLE novios
(
    id_novios           INT             PRIMARY KEY IDENTITY(1,1),
    id_usuario          INT             NOT NULL UNIQUE,
    nombre_novio        VARCHAR(256)    NOT NULL,
    nombre_novia        VARCHAR(256)    NOT NULL,
    tipo_doc_novio      VARCHAR(15)     NOT NULL DEFAULT 'DNI',
    tipo_doc_novia      VARCHAR(15)     NOT NULL DEFAULT 'DNI',
    documento_novio     VARCHAR(20)     NOT NULL,
    documento_novia     VARCHAR(20)     NOT NULL,
    telefono            VARCHAR(30)     NOT NULL,
    como_se_entero      VARCHAR(30)     NOT NULL DEFAULT 'OTRO',
    fecha_creacion      DATETIMEOFFSET  NOT NULL DEFAULT SYSDATETIMEOFFSET(),

    FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    CHECK (tipo_doc_novio IN ('DNI', 'CE', 'PASAPORTE')),
    CHECK (tipo_doc_novia IN ('DNI', 'CE', 'PASAPORTE')),
    CHECK (como_se_entero IN
        ('REDES_SOCIALES', 'REFERIDO', 'BODA_PRESENCIADA',
         'YOUTUBE', 'SAGRADA_FAMILIA', 'OTRO'))
)
GO

CREATE INDEX idx_novios_id_usuario ON novios(id_usuario)
GO

-- =============================================================================
-- TABLA: planner
-- Wedding planners registrados (extiende usuario con rol=WEDDING_PLANNER)
-- =============================================================================
CREATE TABLE planner
(
    id_planner      INT             PRIMARY KEY IDENTITY(1,1),
    id_usuario      INT             NOT NULL UNIQUE,
    nombre          VARCHAR(256)    NOT NULL,
    empresa         VARCHAR(256),
    telefono        VARCHAR(30)     NOT NULL,
    fecha_creacion  DATETIMEOFFSET  NOT NULL DEFAULT SYSDATETIMEOFFSET(),

    FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
)
GO

CREATE INDEX idx_planner_id_usuario ON planner(id_usuario)
GO

-- =============================================================================
-- TABLA: instrumento
-- Catalogo de instrumentos y voces disponibles
-- =============================================================================
CREATE TABLE instrumento
(
    id_instrumento  INT             PRIMARY KEY IDENTITY(1,1),
    slug            VARCHAR(50)     NOT NULL UNIQUE,
    nombre          VARCHAR(256)    NOT NULL,
    icono           VARCHAR(10)     NOT NULL DEFAULT 'M',
    precio_lima     DECIMAL(10,2)   NOT NULL,
    precio_fuera    DECIMAL(10,2)   NOT NULL,
    es_voz          BIT             NOT NULL DEFAULT 0,
    canta_ingles    BIT             NOT NULL DEFAULT 0,
    orden           INT             NOT NULL DEFAULT 0,
    activo          BIT             NOT NULL DEFAULT 1
)
GO

CREATE INDEX idx_instrumento_slug ON instrumento(slug)
GO

-- =============================================================================
-- TABLA: momento_liturgico
-- Los momentos de una ceremonia de boda en orden estricto
-- =============================================================================
CREATE TABLE momento_liturgico
(
    id_momento              INT             PRIMARY KEY IDENTITY(1,1),
    slug                    VARCHAR(50)     NOT NULL UNIQUE,
    nombre                  VARCHAR(256)    NOT NULL,
    descripcion             VARCHAR(1000),
    icono                   VARCHAR(10)     NOT NULL DEFAULT 'M',
    orden                   INT             NOT NULL,
    categoria               VARCHAR(20)     NOT NULL,
    max_canciones           INT             NOT NULL DEFAULT 1,
    permite_repetidas       BIT             NOT NULL DEFAULT 0,
    activo                  BIT             NOT NULL DEFAULT 1,
    -- JSON con restricciones por temporada
    -- ej: {"deshabilitado_en":["cuaresma"], "oculto_en":["adviento"]}
    restricciones_temporada NVARCHAR(MAX),
    CHECK (categoria IN ('LITURGICAL', 'NON_LITURGICAL')),
    CHECK (restricciones_temporada IS NULL
           OR ISJSON(restricciones_temporada) = 1)
)
GO

CREATE INDEX idx_momento_slug ON momento_liturgico(slug)
GO
CREATE INDEX idx_momento_orden ON momento_liturgico(orden)
GO

-- =============================================================================
-- TABLA: temporada_liturgica
-- Tiempos liturgicos catolicos
-- =============================================================================
CREATE TABLE temporada_liturgica
(
    id_temporada    INT             PRIMARY KEY IDENTITY(1,1),
    slug            VARCHAR(50)     NOT NULL UNIQUE,
    nombre          VARCHAR(256)    NOT NULL,
    descripcion     VARCHAR(1000),
    -- JSON con momentos deshabilitados en esta temporada
    -- ej: {"momentos_deshabilitados":["gloria"]}
    restricciones   NVARCHAR(MAX)   NOT NULL DEFAULT '{}',
    CHECK (ISJSON(restricciones) = 1)
)
GO

-- =============================================================================
-- TABLA: temporada_fechas
-- Rangos de fechas para cada temporada por anio
-- =============================================================================
CREATE TABLE temporada_fechas
(
    id_temporada_fechas INT     PRIMARY KEY IDENTITY(1,1),
    id_temporada        INT     NOT NULL,
    anio                INT     NOT NULL,
    fecha_inicio        DATE    NOT NULL,
    fecha_fin           DATE    NOT NULL,

    FOREIGN KEY (id_temporada) REFERENCES temporada_liturgica(id_temporada),
    CONSTRAINT uq_temporada_anio UNIQUE (id_temporada, anio)
)
GO

CREATE INDEX idx_temporada_fechas_rango ON temporada_fechas(fecha_inicio, fecha_fin)
GO

-- =============================================================================
-- TABLA: cancion
-- Catalogo completo del repertorio
-- =============================================================================
CREATE TABLE cancion
(
    id_cancion      INT             PRIMARY KEY IDENTITY(1,1),
    titulo          VARCHAR(256)    NOT NULL,
    autor           VARCHAR(256)    NOT NULL,
    idioma          VARCHAR(5)      NOT NULL DEFAULT 'ES',
    es_liturgica    BIT             NOT NULL DEFAULT 1,
    voz_recomendada VARCHAR(15)     NOT NULL DEFAULT 'ANY',
    notas           VARCHAR(1000),
    activa          BIT             NOT NULL DEFAULT 1,
    fecha_creacion  DATETIMEOFFSET  NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CHECK (idioma IN ('ES', 'EN', 'IT', 'FR')),
    CHECK (voz_recomendada IN ('FEMININE', 'MASCULINE', 'ANY'))
)
GO

CREATE INDEX idx_cancion_titulo ON cancion(titulo)
GO

-- =============================================================================
-- TABLA: cancion_estilo
-- Estilos asignados a una cancion (varios por cancion)
-- =============================================================================
CREATE TABLE cancion_estilo
(
    id_cancion_estilo   INT             PRIMARY KEY IDENTITY(1,1),
    id_cancion          INT             NOT NULL,
    estilo              VARCHAR(20)     NOT NULL,

    FOREIGN KEY (id_cancion) REFERENCES cancion(id_cancion),
    CONSTRAINT uq_cancion_estilo UNIQUE (id_cancion, estilo),
    CHECK (estilo IN ('CLASSICAL', 'MODERN', 'LYRICAL', 'CONTEMPORARY'))
)
GO

-- =============================================================================
-- TABLA: cancion_momento
-- Relacion N:M entre canciones y momentos liturgicos
-- =============================================================================
CREATE TABLE cancion_momento
(
    id_cancion_momento  INT     PRIMARY KEY IDENTITY(1,1),
    id_cancion          INT     NOT NULL,
    id_momento          INT     NOT NULL,

    FOREIGN KEY (id_cancion) REFERENCES cancion(id_cancion),
    FOREIGN KEY (id_momento) REFERENCES momento_liturgico(id_momento),
    CONSTRAINT uq_cancion_momento UNIQUE (id_cancion, id_momento)
)
GO

CREATE INDEX idx_cancion_momento_cancion ON cancion_momento(id_cancion)
GO
CREATE INDEX idx_cancion_momento_momento ON cancion_momento(id_momento)
GO

-- =============================================================================
-- TABLA: cancion_requerimiento
-- Requerimientos instrumentales por cancion
--   tipo='MINIMUM': sin esto la cancion no suena bien (obligatorio)
--   tipo='OPTIMAL': con esto suena ideal (recomendado)
-- =============================================================================
CREATE TABLE cancion_requerimiento
(
    id_cancion_req      INT             PRIMARY KEY IDENTITY(1,1),
    id_cancion          INT             NOT NULL,
    id_instrumento      INT             NOT NULL,
    tipo                VARCHAR(15)     NOT NULL,

    FOREIGN KEY (id_cancion) REFERENCES cancion(id_cancion),
    FOREIGN KEY (id_instrumento) REFERENCES instrumento(id_instrumento),
    CONSTRAINT uq_cancion_req UNIQUE (id_cancion, id_instrumento, tipo),
    CHECK (tipo IN ('MINIMUM', 'OPTIMAL'))
)
GO

CREATE INDEX idx_cancion_req_cancion ON cancion_requerimiento(id_cancion)
GO

-- =============================================================================
-- TABLA: boda
-- Entidad central: cada boda planificada por una pareja
-- =============================================================================
CREATE TABLE boda
(
    id_boda             INT             PRIMARY KEY IDENTITY(1,1),
    id_novios           INT             NOT NULL,
    id_planner          INT,
    fecha_boda          DATE            NOT NULL,
    hora_boda           VARCHAR(5)      NOT NULL,
    nombre_local        VARCHAR(256)    NOT NULL,
    direccion_local     VARCHAR(500)    NOT NULL,
    latitud             FLOAT,
    longitud            FLOAT,
    foto_local_url      VARCHAR(500),
    fuera_de_lima       BIT             NOT NULL DEFAULT 0,
    precio_base         DECIMAL(10,2)   NOT NULL DEFAULT 0,
    precio_instrumentos DECIMAL(10,2)   NOT NULL DEFAULT 0,
    precio_movilidad    DECIMAL(10,2)   NOT NULL DEFAULT 0,
    precio_total        DECIMAL(10,2)   NOT NULL DEFAULT 0,
    estado              VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    notas               VARCHAR(2000),
    fecha_creacion      DATETIMEOFFSET  NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    fecha_actualizacion DATETIMEOFFSET  NOT NULL DEFAULT SYSDATETIMEOFFSET(),

    FOREIGN KEY (id_novios) REFERENCES novios(id_novios),
    FOREIGN KEY (id_planner) REFERENCES planner(id_planner),
    CHECK (estado IN
        ('DRAFT', 'SUBMITTED', 'APPROVED', 'CONTRACTED',
         'CANCELLATION_REQUESTED', 'COMPLETED'))
)
GO

CREATE INDEX idx_boda_novios ON boda(id_novios)
GO
CREATE INDEX idx_boda_planner ON boda(id_planner)
GO
CREATE INDEX idx_boda_estado ON boda(estado)
GO
CREATE INDEX idx_boda_fecha ON boda(fecha_boda)
GO

-- Trigger para mantener fecha_actualizacion al dia
CREATE TRIGGER trg_boda_actualizacion ON boda
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON
    UPDATE boda SET fecha_actualizacion = SYSDATETIMEOFFSET()
    WHERE id_boda IN (SELECT id_boda FROM inserted)
END
GO

-- =============================================================================
-- TABLA: boda_instrumento
-- Instrumentos seleccionados para una boda especifica
-- =============================================================================
CREATE TABLE boda_instrumento
(
    id_boda_instrumento INT     PRIMARY KEY IDENTITY(1,1),
    id_boda             INT     NOT NULL,
    id_instrumento      INT     NOT NULL,

    FOREIGN KEY (id_boda) REFERENCES boda(id_boda) ON DELETE CASCADE,
    FOREIGN KEY (id_instrumento) REFERENCES instrumento(id_instrumento),
    CONSTRAINT uq_boda_instrumento UNIQUE (id_boda, id_instrumento)
)
GO

CREATE INDEX idx_boda_instrumento_boda ON boda_instrumento(id_boda)
GO

-- =============================================================================
-- TABLA: setlist
-- Cada canto seleccionado por los novios para su ceremonia
-- =============================================================================
CREATE TABLE setlist
(
    id_setlist          INT     PRIMARY KEY IDENTITY(1,1),
    id_boda             INT     NOT NULL,
    id_momento          INT     NOT NULL,
    id_cancion          INT     NOT NULL,
    orden               INT     NOT NULL DEFAULT 0,

    FOREIGN KEY (id_boda) REFERENCES boda(id_boda) ON DELETE CASCADE,
    FOREIGN KEY (id_momento) REFERENCES momento_liturgico(id_momento),
    FOREIGN KEY (id_cancion) REFERENCES cancion(id_cancion),
    CONSTRAINT uq_setlist_orden UNIQUE (id_boda, id_momento, orden)
)
GO

CREATE INDEX idx_setlist_boda ON setlist(id_boda)
GO

-- =============================================================================
-- TABLA: contrato
-- Contrato generado para cada boda con firma electronica de doble via
-- =============================================================================
CREATE TABLE contrato
(
    id_contrato         INT             PRIMARY KEY IDENTITY(1,1),
    id_boda             INT             NOT NULL UNIQUE,
    pdf_url             VARCHAR(500),
    firmado_novios      BIT             NOT NULL DEFAULT 0,
    fecha_firma_novios  DATETIMEOFFSET,
    firmante_novios     VARCHAR(256),
    ip_firma_novios     VARCHAR(50),
    firmado_admin       BIT             NOT NULL DEFAULT 0,
    fecha_firma_admin   DATETIMEOFFSET,
    firmante_admin      VARCHAR(256),
    fecha_creacion      DATETIMEOFFSET  NOT NULL DEFAULT SYSDATETIMEOFFSET(),

    FOREIGN KEY (id_boda) REFERENCES boda(id_boda) ON DELETE CASCADE
)
GO

CREATE INDEX idx_contrato_boda ON contrato(id_boda)
GO

-- =============================================================================
-- TABLA: pago
-- Registro de pagos de cada boda
-- =============================================================================
CREATE TABLE pago
(
    id_pago         INT             PRIMARY KEY IDENTITY(1,1),
    id_boda         INT             NOT NULL,
    monto           DECIMAL(10,2)   NOT NULL,
    fecha_pago      DATE            NOT NULL,
    banco           VARCHAR(20)     NOT NULL,
    tipo_pago       VARCHAR(15)     NOT NULL,
    notas           VARCHAR(1000),
    fecha_creacion  DATETIMEOFFSET  NOT NULL DEFAULT SYSDATETIMEOFFSET(),

    FOREIGN KEY (id_boda) REFERENCES boda(id_boda) ON DELETE CASCADE,
    CHECK (banco IN ('SCOTIABANK', 'BCP', 'INTERBANK')),
    CHECK (tipo_pago IN ('ADVANCE', 'BALANCE'))
)
GO

CREATE INDEX idx_pago_boda ON pago(id_boda)
GO

-- =============================================================================
-- TABLA: configuracion_precios
-- Tabla de configuracion (single-row) para precios y parametros
-- TODO el motor de precios lee de aqui. NADA esta hardcodeado en codigo.
-- =============================================================================
CREATE TABLE configuracion_precios
(
    id_config                       INT             PRIMARY KEY IDENTITY(1,1),
    precio_base_lima                DECIMAL(10,2)   NOT NULL,
    precio_base_fuera               DECIMAL(10,2)   NOT NULL,
    precio_instrumento_lima         DECIMAL(10,2)   NOT NULL,
    precio_instrumento_fuera        DECIMAL(10,2)   NOT NULL,
    movilidad_minima                DECIMAL(10,2)   NOT NULL,
    movilidad_maxima                DECIMAL(10,2)   NOT NULL,
    latitud_base                    FLOAT           NOT NULL,
    longitud_base                   FLOAT           NOT NULL,
    radio_lima_km                   FLOAT           NOT NULL,
    movilidad_km_libres             FLOAT           NOT NULL DEFAULT 15,
    movilidad_minutos_libres        FLOAT           NOT NULL DEFAULT 25,
    movilidad_tarifa_km             DECIMAL(10,2)   NOT NULL DEFAULT 6,
    movilidad_tarifa_minuto         DECIMAL(10,2)   NOT NULL DEFAULT 3.5,
    movilidad_grupo_grande          DECIMAL(10,2)   NOT NULL DEFAULT 150,
    movilidad_umbral_grupo          INT             NOT NULL DEFAULT 5,
    movilidad_centro_historico      DECIMAL(10,2)   NOT NULL DEFAULT 100,
    centro_norte_lat                FLOAT           NOT NULL DEFAULT -12.038,
    centro_sur_lat                  FLOAT           NOT NULL DEFAULT -12.065,
    centro_oeste_lng                FLOAT           NOT NULL DEFAULT -77.050,
    centro_este_lng                 FLOAT           NOT NULL DEFAULT -77.010,
    fecha_actualizacion             DATETIMEOFFSET  NOT NULL DEFAULT SYSDATETIMEOFFSET()
)
GO

-- =============================================================================
-- VERIFICACION FINAL
-- =============================================================================
DECLARE @total INT
SELECT @total = COUNT(*)
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_TYPE = 'BASE TABLE'
  AND TABLE_NAME IN
    ('usuario', 'novios', 'planner',
     'instrumento', 'momento_liturgico',
     'temporada_liturgica', 'temporada_fechas',
     'cancion', 'cancion_estilo', 'cancion_momento', 'cancion_requerimiento',
     'boda', 'boda_instrumento', 'setlist',
     'contrato', 'pago', 'configuracion_precios')

PRINT 'Tablas creadas: ' + CAST(@total AS VARCHAR) + ' (esperado: 17)'
GO
