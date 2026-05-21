-- =============================================================================
-- migracion_v5_fotos_caption_autor.sql
-- Pacem Deus Bodas - agrega caption y autor a las fotos del local.
--
-- Cambios:
--   1. boda_foto.caption           VARCHAR(500) NULL  -- comentario opcional
--   2. boda_foto.creado_por_id_usuario INT     NULL  -- quien subio la foto
--   3. SPs actualizados:
--      - usp_boda_foto_agregar    acepta caption + id_usuario
--      - usp_boda_foto_listar     joinea con usuario/novios/planner para
--                                 devolver el nombre del autor segun rol
--      - usp_boda_foto_editar_caption (nuevo)
--
-- Fotos existentes quedan con creado_por_id_usuario = NULL (sin firma).
-- En UI aparece como "Subida previamente" o sin autor.
--
-- Idempotente. Ejecutar en BD pacem_deus_bodas.
-- =============================================================================


-- =============================================================================
-- PASO 1: agregar columnas a boda_foto
-- =============================================================================
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('boda_foto') AND name = 'caption'
)
BEGIN
    ALTER TABLE boda_foto ADD caption VARCHAR(500) NULL
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('boda_foto') AND name = 'creado_por_id_usuario'
)
BEGIN
    ALTER TABLE boda_foto ADD creado_por_id_usuario INT NULL
    -- FK a usuario.id_usuario. Usamos ALTER TABLE separado porque al ADD
    -- COLUMN con FK la BD pone un nombre auto-generado feo.
END
GO

-- FK constraint si no existe
IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE name = 'fk_boda_foto_creado_por'
)
BEGIN
    ALTER TABLE boda_foto
    ADD CONSTRAINT fk_boda_foto_creado_por
    FOREIGN KEY (creado_por_id_usuario) REFERENCES usuario(id_usuario)
END
GO


-- =============================================================================
-- PASO 2: usp_boda_foto_agregar  (acepta caption y autor)
-- =============================================================================
CREATE OR ALTER PROC usp_boda_foto_agregar
(
    @id_boda                    INT,
    @url                        VARCHAR(500),
    @s3_key                     VARCHAR(500),
    @caption                    VARCHAR(500) = NULL,
    @creado_por_id_usuario      INT          = NULL
)
AS
BEGIN
    SET NOCOUNT ON

    DECLARE @next_orden INT
    SELECT @next_orden = ISNULL(MAX(orden), 0) + 1
    FROM   boda_foto
    WHERE  id_boda = @id_boda

    INSERT INTO boda_foto (id_boda, url, s3_key, orden, caption, creado_por_id_usuario)
    VALUES (@id_boda, @url, @s3_key, @next_orden, @caption, @creado_por_id_usuario)

    -- Devolver la fila completa (sin join, no necesitamos el autor aqui;
    -- el handler de Python ya conoce al usuario porque lo paso como param)
    SELECT  id_foto,
            id_boda,
            url,
            s3_key,
            orden,
            fecha_subida,
            caption,
            creado_por_id_usuario
    FROM    boda_foto
    WHERE   id_foto = SCOPE_IDENTITY()
END
GO


-- =============================================================================
-- PASO 3: usp_boda_foto_listar  (incluye nombre del autor)
-- =============================================================================
-- Resuelve el nombre del autor segun el rol del usuario que subio la foto:
--   - COUPLE             -> "nombre_novio & nombre_novia"
--   - WEDDING_PLANNER    -> "nombre del planner"
--   - ADMIN              -> "Coro Pacem Deus"
--   - sin autor (NULL)   -> NULL (UI lo oculta)

CREATE OR ALTER PROC usp_boda_foto_listar
(
    @id_boda INT
)
AS
BEGIN
    SET NOCOUNT ON
    SELECT  bf.id_foto,
            bf.id_boda,
            bf.url,
            bf.s3_key,
            bf.orden,
            bf.fecha_subida,
            bf.caption,
            bf.creado_por_id_usuario,
            CASE u.rol
                WHEN 'COUPLE'          THEN n.nombre_novio + N' y ' + n.nombre_novia
                WHEN 'WEDDING_PLANNER' THEN p.nombre
                WHEN 'ADMIN'           THEN N'Coro Pacem Deus'
                ELSE NULL
            END AS autor_nombre,
            u.rol AS autor_rol
    FROM    boda_foto bf
    LEFT JOIN usuario u ON u.id_usuario = bf.creado_por_id_usuario
    LEFT JOIN novios  n ON n.id_usuario = bf.creado_por_id_usuario
    LEFT JOIN planner p ON p.id_usuario = bf.creado_por_id_usuario
    WHERE   bf.id_boda = @id_boda
    ORDER BY bf.orden ASC
END
GO


-- =============================================================================
-- PASO 4: usp_boda_foto_editar_caption  (nuevo)
-- =============================================================================
-- Solo el autor original puede editar (validacion en handler Python).
-- El SP solo persiste; espera @id_foto + @id_boda + nuevo @caption (puede
-- ser NULL para limpiar). Devuelve 1 si actualizo, 0 si la foto no existe
-- o no pertenece a la boda.

CREATE OR ALTER PROC usp_boda_foto_editar_caption
(
    @id_boda    INT,
    @id_foto    INT,
    @caption    VARCHAR(500) = NULL
)
AS
BEGIN
    SET NOCOUNT ON

    UPDATE boda_foto
    SET    caption = @caption
    WHERE  id_foto = @id_foto AND id_boda = @id_boda

    SELECT @@ROWCOUNT AS filas_afectadas
END
GO


-- =============================================================================
-- VERIFICACION
-- =============================================================================
PRINT '==============================================================='
PRINT 'Migracion v5 caption + autor completada. Verificacion:'
PRINT '==============================================================='

SELECT 'Columnas nuevas' AS tabla, name, system_type_id
FROM sys.columns
WHERE object_id = OBJECT_ID('boda_foto')
  AND name IN ('caption', 'creado_por_id_usuario')

SELECT 'SPs actualizados' AS tabla, name FROM sys.procedures
WHERE name IN ('usp_boda_foto_agregar', 'usp_boda_foto_listar',
               'usp_boda_foto_editar_caption')
GO
