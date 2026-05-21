"""
get_boda_setlist_pdf.py
GET /v1/bodas/{id_boda}/setlist/pdf
Header: Authorization: Bearer <token>

Genera un PDF imprimible/compartible con el setlist completo de la
boda: por momento liturgico, los cantos elegidos en orden. Disenio
en linea con el sitio del coro (gold/cream/brown) y con la misma
estructura visual del contrato (marco doble dorado + tipografia
serif con letter-spacing). Sin dependencias externas (pdf_builder
es stdlib puro).

Acceso:
  - ADMIN: cualquier boda
  - COUPLE: solo la suya
  - WEDDING_PLANNER: solo las que tiene asignadas
"""

import base64
import re
import unicodedata

from shared import db
from shared import auth
from shared import responses
from pdf_builder import PDFBuilder, MM_TO_PT


# Paleta del coro
COLOR_GOLD     = (200, 148, 60)
COLOR_GOLD_LT  = (232, 192, 112)
COLOR_GOLD_DK  = (139, 96, 32)
COLOR_CREAM    = (250, 245, 236)
COLOR_CREAM_DK = (240, 232, 212)
COLOR_BROWN    = (61, 53, 48)
COLOR_BROWN_LT = (107, 94, 84)


def handle_get_boda_setlist_pdf(event, context):
    try:
        payload = auth.authenticate(event)
    except auth.AuthError as e:
        return responses.unauthorized(e.message)

    id_boda = responses.parse_int_param(responses.get_path_param(event, "id_boda"))
    if not id_boda:
        return responses.bad_request("id_boda requerido")

    boda = db.fetch_one("usp_boda_obtener", (id_boda,))
    if not boda:
        return responses.not_found("Evento no encontrado")

    if not _tiene_acceso(payload, boda):
        return responses.forbidden("No tienes acceso a este setlist")

    novios = db.fetch_one("usp_novios_obtener", (boda["id_novios"],)) or {}
    instrumentos = db.fetch_all("usp_boda_instrumentos_listar", (id_boda,))
    setlist = db.fetch_all("usp_setlist_listar", (id_boda,))

    pdf_bytes = _build_setlist_pdf(
        boda=boda, novios=novios,
        instrumentos=instrumentos, setlist=setlist
    )

    return responses.ok({
        "filename": _build_filename(novios),
        "pdf_base64": base64.b64encode(pdf_bytes).decode("ascii"),
        "size_bytes": len(pdf_bytes),
    })


# ═══════════════════════════════════════════════════════════════════
# Builder visual
# ═══════════════════════════════════════════════════════════════════

def _build_setlist_pdf(boda, novios, instrumentos, setlist):
    pdf = PDFBuilder()
    pdf.add_page()

    # ─── Fondo cream uniforme + marco doble dorado ──────────
    # (mismo decorado que el contrato para mantener identidad visual)
    w = pdf.page_width()
    pdf.rect_filled(0, 0, w, 297, COLOR_CREAM)
    pdf.rect_outlined(8, 8, 194, 281, COLOR_GOLD, thickness=0.9)
    pdf.rect_outlined(11, 11, 188, 275, COLOR_GOLD_LT, thickness=0.3)
    # Diamantes en las 4 esquinas
    for (x, y) in [(11, 11), (199, 11), (11, 286), (199, 286)]:
        pdf.ornament_diamond(x, y, 1.2, COLOR_GOLD)

    margin_inner = 11.0

    # ─── Brand header (mismo estilo que el contrato) ─────────
    # "CORO PACEM DEUS" en serif grande con tracking amplio + linea
    # de gradiente dorado + lema en italic.
    # v05: spacing comprimido para que todo entre en 1 pagina (15 cantos
    # + footer instrumentos). El header v04 consumia ~71mm y forzaba
    # overflow del footer a pagina 2.
    pdf.vertical_space(4.0)
    pdf.set_font("SerifBold", 20)
    pdf.set_color(*COLOR_BROWN)
    pdf.text_line("CORO PACEM DEUS", align="center", char_space_pt=6)
    pdf.vertical_space(2.0)
    pdf.gradient_line(60, pdf.cur_y, 150, COLOR_GOLD, COLOR_CREAM, segments=30)
    pdf.vertical_space(1.5)
    pdf.set_font("SerifItalic", 11)
    pdf.set_color(*COLOR_BROWN_LT)
    pdf.text_line("Cantamos al Amor de los Amores", align="center")
    pdf.vertical_space(2.5)
    pdf.ornament_flourish(105, pdf.cur_y, 50, COLOR_GOLD)
    pdf.vertical_space(3.0)

    # Titulo del documento
    pdf.set_font("Bold", 14)
    pdf.set_color(*COLOR_BROWN)
    pdf.text_line(_ansi_safe("Setlist musical"), align="center", char_space_pt=1.0)

    # Nombres de los novios separados por "&" (en vez de "y")
    nombre_novio = _ansi_safe(novios.get("nombre_novio") or "").strip()
    nombre_novia = _ansi_safe(novios.get("nombre_novia") or "").strip()
    if nombre_novio or nombre_novia:
        pdf.vertical_space(2.0)
        pdf.set_font("Italic", 11)
        pdf.set_color(*COLOR_BROWN_LT)
        pareja = (f"{nombre_novio} & {nombre_novia}"
                  if nombre_novio and nombre_novia
                  else (nombre_novio or nombre_novia))
        pdf.text_line(pareja, align="center")

    # Fecha de la boda
    fecha_boda = boda.get("fecha_boda")
    if fecha_boda:
        pdf.vertical_space(1.0)
        pdf.set_font("Regular", 10)
        pdf.set_color(*COLOR_BROWN_LT)
        pdf.text_line(
            _ansi_safe(_formatear_fecha(str(fecha_boda))),
            align="center"
        )

    # Local
    nombre_local = _ansi_safe(boda.get("nombre_local") or "")
    if nombre_local:
        pdf.vertical_space(0.5)
        pdf.set_font("Regular", 10)
        pdf.set_color(*COLOR_BROWN_LT)
        pdf.text_line(nombre_local, align="center")

    pdf.vertical_space(3.0)
    pdf.ornament_diamond(w / 2 - 1.25, pdf.cur_y, 2.5, COLOR_GOLD)
    pdf.vertical_space(3.0)

    # ─── Cuerpo: setlist agrupado por momento ────────────
    if not setlist:
        pdf.set_font("Italic", 11)
        pdf.set_color(*COLOR_BROWN_LT)
        pdf.text_line(
            _ansi_safe("Aun no hay cantos seleccionados."),
            align="center"
        )
    else:
        # Agrupar por momento, respetando orden_momento + orden interno
        setlist_ordenado = sorted(
            setlist,
            key=lambda s: (
                int(s.get("orden_momento") or 9999),
                int(s.get("orden") or 9999)
            )
        )
        # Particionar
        momentos_orden = []
        cantos_por_momento = {}
        for s in setlist_ordenado:
            nombre = _ansi_safe(s.get("nombre_momento") or "Momento")
            if nombre not in cantos_por_momento:
                momentos_orden.append(nombre)
                cantos_por_momento[nombre] = []
            cantos_por_momento[nombre].append(s)

        global_idx = 0
        for momento_idx, nombre_momento in enumerate(momentos_orden):
            # Sub-header del momento (compacto, sin padding extra)
            pdf.set_font("Bold", 9)
            pdf.set_color(*COLOR_GOLD)
            pdf.text_line(
                nombre_momento.upper(),
                align="left",
                indent=margin_inner + 8.0,
                char_space_pt=1.8
            )
            pdf.vertical_space(0.5)

            # Cantos del momento: UNA sola linea por canto:
            # "N.  Titulo - Autor"  (em-dash separa autor cuando existe)
            for s in cantos_por_momento[nombre_momento]:
                global_idx += 1
                # FIX v05: el SP usp_setlist_listar devuelve `titulo` y
                # `autor` (no `titulo_cancion` / `autor_cancion`). Antes
                # caia siempre al fallback "(sin titulo)".
                titulo = _ansi_safe(s.get("titulo") or "(sin titulo)")
                autor = _ansi_safe(s.get("autor") or "")

                pdf.set_font("Regular", 10)
                pdf.set_color(*COLOR_BROWN)
                linea = f"{global_idx}.  {titulo}"
                if autor:
                    linea += f"  -  {autor}"
                pdf.text_line(
                    linea,
                    align="left",
                    indent=margin_inner + 12.0
                )
                pdf.vertical_space(0.4)

            # Espacio entre momentos (minimo)
            if momento_idx < len(momentos_orden) - 1:
                pdf.vertical_space(1.5)

    # ─── Footer: instrumentos ───────────────────────────
    if instrumentos:
        pdf.vertical_space(5.0)
        pdf.gradient_line(
            margin_inner + 10,
            pdf.cur_y,
            w - margin_inner - 10,
            COLOR_GOLD,
            COLOR_CREAM,
            segments=20
        )
        pdf.vertical_space(6.0)

        pdf.set_font("Bold", 10)
        pdf.set_color(*COLOR_GOLD)
        pdf.text_line(
            _ansi_safe("VOCES E INSTRUMENTOS"),
            align="center",
            char_space_pt=2.5
        )
        pdf.vertical_space(3.0)

        nombres = [_ansi_safe(i.get("nombre") or "") for i in instrumentos]
        nombres = [n for n in nombres if n]
        if nombres:
            pdf.set_font("Regular", 11)
            pdf.set_color(*COLOR_BROWN)
            pdf.text_line(", ".join(nombres), align="center")

    return pdf.build()


# ═══════════════════════════════════════════════════════════════════
# Helpers
# ═══════════════════════════════════════════════════════════════════

def _formatear_fecha(iso):
    """'2026-08-15' -> '15 de agosto de 2026'"""
    try:
        partes = iso.split("-")
        anio = partes[0]
        mes = int(partes[1])
        dia = int(partes[2][:2])
        meses = [
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        ]
        return f"{dia} de {meses[mes - 1]} de {anio}"
    except (IndexError, ValueError, TypeError):
        return iso


def _capitalize_safe(text):
    return text


def _ansi_safe(text):
    """Quita acentos para evitar problemas con WinAnsiEncoding."""
    if not text:
        return ""
    s = unicodedata.normalize("NFD", str(text))
    return "".join(c for c in s if unicodedata.category(c) != "Mn")


def _slugify(text):
    text = unicodedata.normalize("NFD", text)
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    text = re.sub(r"[^A-Za-z0-9_]+", "_", text)
    text = re.sub(r"_+", "_", text).strip("_")
    return text or "Setlist"


def _build_filename(novios):
    nombre_novio = (novios.get("nombre_novio") or "").strip()
    nombre_novia = (novios.get("nombre_novia") or "").strip()
    parts = []
    if nombre_novio:
        parts.append(_slugify(nombre_novio.split()[0]))
    if nombre_novia:
        parts.append(_slugify(nombre_novia.split()[0]))
    base = "_".join(parts) if parts else "Setlist"
    return f"Setlist_{base}.pdf"


def _tiene_acceso(payload, boda):
    rol = payload["rol"]
    id_usuario = payload["id_usuario"]
    if rol == "ADMIN":
        return True
    if rol == "COUPLE":
        couple = db.fetch_one("usp_novios_obtener_por_usuario", (id_usuario,))
        return couple and couple["id_novios"] == boda["id_novios"]
    if rol == "WEDDING_PLANNER":
        if not boda.get("id_planner"):
            return False
        planner = db.fetch_one("usp_planner_obtener_por_usuario", (id_usuario,))
        return planner and planner["id_planner"] == boda["id_planner"]
    return False
