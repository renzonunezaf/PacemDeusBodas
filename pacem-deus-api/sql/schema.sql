-- ═══════════════════════════════════════════════════════════════
-- Pacem Deus Bodas — Esquema de Base de Datos
-- IS276 — Plataformas Móviles y Análisis Cloud — Grupo 2
-- ═══════════════════════════════════════════════════════════════
-- Ejecutar en RDS PostgreSQL para crear todas las tablas.
-- ═══════════════════════════════════════════════════════════════

-- ─── TIPOS ENUMERADOS ──────────────────────────────────────

CREATE TYPE role_type AS ENUM ('ADMIN', 'COUPLE', 'WEDDING_PLANNER');
CREATE TYPE wedding_status AS ENUM (
    'DRAFT', 'SUBMITTED', 'APPROVED',
    'CONTRACTED', 'CANCELLATION_REQUESTED', 'COMPLETED'
);

-- ─── USUARIOS ──────────────────────────────────────────────

CREATE TABLE users (
    id            VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          role_type NOT NULL DEFAULT 'COUPLE',
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- ─── PAREJAS DE NOVIOS ─────────────────────────────────────

CREATE TABLE couples (
    id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id     VARCHAR(36) UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    groom_name  VARCHAR(100) NOT NULL,
    bride_name  VARCHAR(100) NOT NULL,
    groom_dni   VARCHAR(20) NOT NULL,
    bride_dni   VARCHAR(20) NOT NULL,
    phone       VARCHAR(30) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- ─── WEDDING PLANNERS ──────────────────────────────────────

CREATE TABLE wedding_planners (
    id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id     VARCHAR(36) UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    company     VARCHAR(100),
    phone       VARCHAR(30) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- ─── BODAS (ENTIDAD CENTRAL) ───────────────────────────────

CREATE TABLE weddings (
    id                VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    couple_id         VARCHAR(36) NOT NULL REFERENCES couples(id) ON DELETE CASCADE,
    planner_id        VARCHAR(36) REFERENCES wedding_planners(id) ON DELETE SET NULL,
    wedding_date      DATE NOT NULL,
    wedding_time      VARCHAR(10) NOT NULL,
    venue_name        VARCHAR(200) NOT NULL,
    venue_address     VARCHAR(500) NOT NULL,
    venue_lat         DOUBLE PRECISION,
    venue_lng         DOUBLE PRECISION,
    venue_photo_url   TEXT,
    status            wedding_status NOT NULL DEFAULT 'DRAFT',
    base_price        NUMERIC(10,2) NOT NULL DEFAULT 0,
    instruments_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    mobility_price    NUMERIC(10,2) NOT NULL DEFAULT 0,
    total_price       NUMERIC(10,2) NOT NULL DEFAULT 0,
    notes             TEXT,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now()
);

-- ─── INSTRUMENTOS ──────────────────────────────────────────

CREATE TABLE instruments (
    id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    slug        VARCHAR(50) UNIQUE NOT NULL,
    name        VARCHAR(100) NOT NULL,
    icon        VARCHAR(10) DEFAULT '🎵',
    price_lima  NUMERIC(10,2) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT true
);

-- ─── INSTRUMENTOS POR BODA ─────────────────────────────────

CREATE TABLE wedding_instruments (
    id            VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    wedding_id    VARCHAR(36) NOT NULL REFERENCES weddings(id) ON DELETE CASCADE,
    instrument_id VARCHAR(36) NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    UNIQUE(wedding_id, instrument_id)
);

-- ─── MOMENTOS LITÚRGICOS ───────────────────────────────────

CREATE TABLE liturgical_moments (
    id            VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    slug          VARCHAR(50) UNIQUE NOT NULL,
    name          VARCHAR(100) NOT NULL,
    description   TEXT,
    icon          VARCHAR(10) DEFAULT '📖',
    display_order INT NOT NULL DEFAULT 0,
    max_songs     INT NOT NULL DEFAULT 1,
    is_active     BOOLEAN NOT NULL DEFAULT true
);

-- ─── CANCIONES ─────────────────────────────────────────────

CREATE TABLE songs (
    id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    title       VARCHAR(200) NOT NULL,
    author      VARCHAR(200) NOT NULL,
    language    VARCHAR(20) NOT NULL DEFAULT 'ES',
    is_active   BOOLEAN NOT NULL DEFAULT true
);

-- ─── RELACIÓN CANCIÓN ↔ MOMENTO ────────────────────────────

CREATE TABLE song_moments (
    id        VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    song_id   VARCHAR(36) NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
    moment_id VARCHAR(36) NOT NULL REFERENCES liturgical_moments(id) ON DELETE CASCADE,
    UNIQUE(song_id, moment_id)
);

-- ─── SETLIST (CANTOS SELECCIONADOS POR BODA) ───────────────

CREATE TABLE setlist_items (
    id            VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    wedding_id    VARCHAR(36) NOT NULL REFERENCES weddings(id) ON DELETE CASCADE,
    moment_id     VARCHAR(36) NOT NULL REFERENCES liturgical_moments(id),
    song_id       VARCHAR(36) NOT NULL REFERENCES songs(id),
    display_order INT NOT NULL DEFAULT 0,
    UNIQUE(wedding_id, moment_id, song_id)
);

-- ─── CONTRATOS ─────────────────────────────────────────────

CREATE TABLE contracts (
    id            VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    wedding_id    VARCHAR(36) UNIQUE NOT NULL REFERENCES weddings(id) ON DELETE CASCADE,
    couple_signed BOOLEAN NOT NULL DEFAULT false,
    admin_signed  BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- ─── ÍNDICES PARA RENDIMIENTO ──────────────────────────────

CREATE INDEX idx_weddings_couple ON weddings(couple_id);
CREATE INDEX idx_weddings_planner ON weddings(planner_id);
CREATE INDEX idx_weddings_date ON weddings(wedding_date);
CREATE INDEX idx_setlist_wedding ON setlist_items(wedding_id);
CREATE INDEX idx_song_moments_moment ON song_moments(moment_id);
CREATE INDEX idx_song_moments_song ON song_moments(song_id);
