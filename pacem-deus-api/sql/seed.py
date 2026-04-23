# ═══════════════════════════════════════════════════════════════
# Pacem Deus Bodas — Script de datos de prueba
# IS276 — Plataformas Móviles y Análisis Cloud — Grupo 2
# ═══════════════════════════════════════════════════════════════
# Ejecutar después de schema.sql para poblar la BD con datos de prueba.
# Uso: python seed.py
# ═══════════════════════════════════════════════════════════════

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from shared.db import get_connection, hash_password

# Contraseña compartida para todas las cuentas de prueba
PASSWORD = "PacemDeus2026!"

def seed():
    conn = get_connection()
    cur = conn.cursor()

    hashed = hash_password(PASSWORD)

    print("🎵 Iniciando seed de Pacem Deus Bodas...\n")

    # ─── 1. ADMINS ──────────────────────────────────────────
    print("👤 Creando administradores...")
    admins = [
        ("renzonunez.af@gmail.com", "Renzo Núñez"),
        ("aldo@pacemdeus.com", "Aldo Cárdenas"),
    ]
    for email, name in admins:
        cur.execute("""
            INSERT INTO users (email, password_hash, role)
            VALUES (%s, %s, 'ADMIN')
            ON CONFLICT (email) DO NOTHING
        """, (email, hashed))
    print(f"   ✓ {len(admins)} administradores\n")

    # ─── 2. WEDDING PLANNERS ────────────────────────────────
    print("✦  Creando wedding planners...")
    planners = [
        ("wedding1@correo.com", "Valeria Campos", "Love & Co. Events", "+51 987 111 111"),
        ("wedding2@correo.com", "Daniela Ríos", "D'Rosas Wedding", "+51 987 222 222"),
        ("wedding3@correo.com", "Fernanda López", None, "+51 987 333 333"),
    ]
    planner_ids = []
    for email, name, company, phone in planners:
        cur.execute("""
            INSERT INTO users (email, password_hash, role)
            VALUES (%s, %s, 'WEDDING_PLANNER')
            ON CONFLICT (email) DO NOTHING
            RETURNING id
        """, (email, hashed))
        row = cur.fetchone()
        if row:
            user_id = row[0]
            cur.execute("""
                INSERT INTO wedding_planners (user_id, name, company, phone)
                VALUES (%s, %s, %s, %s)
                RETURNING id
            """, (user_id, name, company, phone))
            planner_ids.append(cur.fetchone()[0])
    print(f"   ✓ {len(planners)} wedding planners\n")

    # ─── 3. PAREJAS CON EVENTOS ─────────────────────────────
    print("💍 Creando parejas con eventos en borrador...")
    couples = [
        {
            "email": "novia1@correo.com",
            "groom": "Carlos Mendoza", "bride": "Ana Lucía Torres",
            "groom_dni": "71234567", "bride_dni": "71234568",
            "phone": "+51 999 111 111",
            "date": "2026-06-20", "time": "11:00",
            "venue": "Parroquia Virgen de Fátima",
            "address": "Av. Armendáriz 350, Miraflores, Lima",
            "lat": -12.1215, "lng": -77.0340,
            "planner_idx": 0,
        },
        {
            "email": "novia2@correo.com",
            "groom": "Diego Herrera", "bride": "Sofía Ramírez",
            "groom_dni": "72345678", "bride_dni": "72345679",
            "phone": "+51 999 222 222",
            "date": "2026-07-18", "time": "16:00",
            "venue": "Iglesia San Pedro",
            "address": "Jr. Ucayali 363, Cercado de Lima",
            "lat": -12.0464, "lng": -77.0307,
            "planner_idx": 1,
        },
    ]
    for c in couples:
        cur.execute("""
            INSERT INTO users (email, password_hash, role)
            VALUES (%s, %s, 'COUPLE')
            ON CONFLICT (email) DO NOTHING
            RETURNING id
        """, (c["email"], hashed))
        row = cur.fetchone()
        if not row:
            continue
        user_id = row[0]

        cur.execute("""
            INSERT INTO couples (user_id, groom_name, bride_name, groom_dni, bride_dni, phone)
            VALUES (%s, %s, %s, %s, %s, %s)
            RETURNING id
        """, (user_id, c["groom"], c["bride"], c["groom_dni"], c["bride_dni"], c["phone"]))
        couple_id = cur.fetchone()[0]

        planner_id = planner_ids[c["planner_idx"]] if c["planner_idx"] < len(planner_ids) else None
        cur.execute("""
            INSERT INTO weddings (couple_id, planner_id, wedding_date, wedding_time,
                                  venue_name, venue_address, venue_lat, venue_lng,
                                  base_price, total_price, status)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 350, 650, 'DRAFT')
        """, (couple_id, planner_id, c["date"], c["time"],
              c["venue"], c["address"], c["lat"], c["lng"]))
    print(f"   ✓ {len(couples)} parejas con bodas en borrador\n")

    # ─── 4. INSTRUMENTOS ────────────────────────────────────
    print("🎹 Insertando instrumentos...")
    instruments = [
        ("piano", "Pianista", "🎹", 150, 1),
        ("voz_femenina", "Voz Femenina", "🎤", 150, 2),
        ("voz_masculina", "Voz Masculina", "🎤", 150, 3),
        ("violin_1", "Violín I", "🎻", 180, 4),
        ("violin_2", "Violín II", "🎻", 180, 5),
        ("cello", "Violoncello", "🎻", 200, 6),
        ("flauta", "Flauta Traversa", "🎵", 150, 7),
        ("soprano", "Soprano", "🎤", 200, 8),
        ("tenor", "Tenor", "🎤", 200, 9),
    ]
    for slug, name, icon, price, order in instruments:
        cur.execute("""
            INSERT INTO instruments (slug, name, icon, price_lima, sort_order)
            VALUES (%s, %s, %s, %s, %s)
            ON CONFLICT (slug) DO NOTHING
        """, (slug, name, icon, price, order))
    print(f"   ✓ {len(instruments)} instrumentos\n")

    # ─── 5. MOMENTOS LITÚRGICOS ─────────────────────────────
    print("📖 Insertando momentos litúrgicos...")
    moments = [
        ("entrada", "Entrada", "Procesional de ingreso de la novia", "⛪", 1, 2),
        ("kyrie", "Kyrie", "Acto penitencial", "🙏", 2, 1),
        ("gloria", "Gloria", "Himno de alabanza", "✨", 3, 1),
        ("salmo", "Salmo", "Salmo responsorial", "📖", 4, 1),
        ("aleluya", "Aleluya", "Aclamación al evangelio", "🕊️", 5, 1),
        ("ofertorio", "Ofertorio", "Presentación de ofrendas", "🍷", 6, 2),
        ("santo", "Santo", "Aclamación eucarística", "🔔", 7, 1),
        ("paz", "Paz", "Rito de la paz", "🤝", 8, 1),
        ("cordero", "Cordero", "Cordero de Dios", "🐑", 9, 1),
        ("comunion", "Comunión", "Canto de comunión", "🍞", 10, 2),
        ("accion_gracias", "Acción de Gracias", "Canto de acción de gracias", "💛", 11, 1),
        ("virgen", "Virgen", "Canto a la Virgen María", "🌹", 12, 1),
        ("fotografias", "Fotografías", "Cantos durante las fotografías", "📸", 13, 4),
        ("salida", "Salida", "Marcha de salida", "🎉", 14, 1),
    ]
    for slug, name, desc, icon, order, max_s in moments:
        cur.execute("""
            INSERT INTO liturgical_moments (slug, name, description, icon, display_order, max_songs)
            VALUES (%s, %s, %s, %s, %s, %s)
            ON CONFLICT (slug) DO NOTHING
        """, (slug, name, desc, icon, order, max_s))
    print(f"   ✓ {len(moments)} momentos litúrgicos\n")

    # ─── 6. CANCIONES (MUESTRA REPRESENTATIVA) ──────────────
    print("🎶 Insertando canciones...")
    songs_data = [
        # (título, autor, idioma, [momentos])
        ("Canon en D", "Pachelbel", "INST", ["entrada"]),
        ("Trumpet Voluntary", "Clarke", "INST", ["entrada", "salida"]),
        ("Marcha Nupcial", "Mendelssohn", "INST", ["entrada", "salida"]),
        ("Ave María", "Schubert", "LA", ["ofertorio", "virgen", "comunion"]),
        ("Ave María", "Bach/Gounod", "LA", ["ofertorio", "virgen"]),
        ("Señor, ten piedad", "Liturgia", "ES", ["kyrie"]),
        ("Gloria a Dios", "M. Frisina", "ES", ["gloria"]),
        ("El Señor es mi pastor", "Salmo 23", "ES", ["salmo"]),
        ("Aleluya", "Haendel", "LA", ["aleluya"]),
        ("Pescador de hombres", "C. Gabaráin", "ES", ["ofertorio"]),
        ("Santo, Santo, Santo", "Liturgia", "ES", ["santo"]),
        ("Cordero de Dios", "Liturgia", "ES", ["cordero"]),
        ("La paz esté con nosotros", "Liturgia", "ES", ["paz"]),
        ("Pan de Vida", "B. Farrell", "ES", ["comunion"]),
        ("Alma misionera", "Liturgia", "ES", ["accion_gracias"]),
        ("Dios te salve María", "Tradicional", "ES", ["virgen"]),
        ("A thousand years", "C. Perri", "EN", ["entrada", "fotografias"]),
        ("Perfect", "Ed Sheeran", "EN", ["fotografias", "ofertorio"]),
        ("Thinking Out Loud", "Ed Sheeran", "EN", ["fotografias"]),
        ("All of Me", "John Legend", "EN", ["fotografias", "comunion"]),
        ("Hallelujah", "L. Cohen", "EN", ["ofertorio", "fotografias"]),
        ("Can't Help Falling in Love", "Elvis Presley", "EN", ["fotografias"]),
        ("Marcha triunfal", "Verdi", "INST", ["salida"]),
        ("Spring", "Vivaldi", "INST", ["fotografias", "entrada"]),
        ("Jesús de Nazareth", "Gounod", "ES", ["comunion", "accion_gracias"]),
    ]

    # Obtener IDs de momentos para las relaciones
    cur.execute("SELECT id, slug FROM liturgical_moments")
    moment_map = {row[1]: row[0] for row in cur.fetchall()}

    song_count = 0
    for title, author, lang, moment_slugs in songs_data:
        cur.execute("""
            INSERT INTO songs (title, author, language)
            VALUES (%s, %s, %s)
            RETURNING id
        """, (title, author, lang))
        song_id = cur.fetchone()[0]
        song_count += 1

        for m_slug in moment_slugs:
            if m_slug in moment_map:
                cur.execute("""
                    INSERT INTO song_moments (song_id, moment_id)
                    VALUES (%s, %s)
                    ON CONFLICT DO NOTHING
                """, (song_id, moment_map[m_slug]))

    print(f"   ✓ {song_count} canciones con relaciones a momentos\n")

    # ─── COMMIT ─────────────────────────────────────────────
    conn.commit()
    cur.close()
    conn.close()

    print("═══════════════════════════════════════════")
    print("✅ Seed completado exitosamente")
    print("═══════════════════════════════════════════")
    print(f"🔑 Contraseña de prueba: {PASSWORD}")
    print("═══════════════════════════════════════════\n")


if __name__ == "__main__":
    seed()
