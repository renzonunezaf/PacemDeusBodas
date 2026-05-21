"""
get_boda_contrato_pdf.py
GET /v1/bodas/{id_boda}/contrato/pdf
Header: Authorization: Bearer <token>

Genera el contrato como una pieza visual estilo invitacion de boda:
fondo cream con marco doble dorado, tipografia serif (Times) con
letter-spacing amplio para evocar Cinzel, divisores con gradiente
dorado y florete decorativo entre secciones. Sin dependencias externas
(pdf_builder.py es puro stdlib).
"""

import base64
import datetime
import re
import unicodedata
import urllib.parse

from shared import db
from shared import auth
from shared import responses
from pdf_builder import PDFBuilder, _text_width_mm, MM_TO_PT


# ─── Paleta exacta del sitio web del coro ──────────────
# (extraida de css/style.css :root, alineada con la marca)
COLOR_GOLD     = (200, 148, 60)    # --gold       #C8943C
COLOR_GOLD_LT  = (232, 192, 112)   # --gold-lt    #E8C070
COLOR_GOLD_DK  = (139, 96, 32)     # --gold-dk    #8B6020
COLOR_CREAM    = (250, 245, 236)   # --cream      #FAF5EC
COLOR_CREAM_DK = (240, 232, 212)   # --cream-dk   #F0E8D4
COLOR_BROWN    = (61, 53, 48)      # --brown      #3D3530
COLOR_BROWN_LT = (107, 94, 84)     # --brown-lt   #6B5E54


def handle_get_boda_contrato_pdf(event, context):
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
        return responses.forbidden("No tienes acceso a este contrato")

    novios = db.fetch_one("usp_novios_obtener", (boda["id_novios"],)) or {}
    planner = None
    if boda.get("id_planner"):
        planner = db.fetch_one("usp_planner_obtener", (boda["id_planner"],))
    instrumentos = db.fetch_all("usp_boda_instrumentos_listar", (id_boda,))
    setlist = db.fetch_all("usp_setlist_listar", (id_boda,))

    pdf_bytes = _build_contract_pdf(
        boda=boda, novios=novios, planner=planner,
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

def _build_contract_pdf(boda, novios, planner, instrumentos, setlist):
    pdf = PDFBuilder()
    pdf.left_margin = 22
    pdf.right_margin = 22
    pdf.top_margin = 22
    pdf.bottom_margin = 22
    # Activar decoracion automatica para que cada pagina (incluidas las
    # nuevas creadas por auto-page-break) reciba el mismo marco + fondo.
    pdf.page_decorator = _decorate_page

    # ─── PAGINA 1: portada + ceremonia + cotizacion ──────────
    pdf.add_page()
    pdf.cur_y = 28
    _draw_brand_header(pdf)
    _draw_certificate_title(pdf)
    _draw_couple_block(pdf, novios)
    _draw_ceremony_block(pdf, boda)
    _draw_servicio_block(pdf, boda, instrumentos)
    _draw_brand_footer(pdf)

    # ─── PAGINA 2: cantos elegidos (siempre en pagina propia) ──
    # Sin footer: el contenido habla por si mismo.
    pdf.add_page()
    _draw_continuation_header(pdf)
    _draw_cantos_block(pdf, setlist)

    # ─── PAGINA 3: terminos + firmas ─────────────────────────
    # Sin footer: termina con las firmas, no necesita pie repetido.
    pdf.add_page()
    _draw_continuation_header(pdf)
    _draw_terminos_block(pdf)
    _draw_firmas_block(pdf, novios)

    return pdf.build()


def _draw_continuation_header(pdf):
    """
    Encabezado ligero para paginas 2 y 3: solo nombre del coro + linea
    fina. Conserva la unidad visual sin repetir la portada completa.
    """
    pdf.cur_y = 24
    pdf.set_font("SerifBold", 11)
    pdf.set_color(*COLOR_BROWN)
    pdf.text_line("CORO PACEM DEUS", align="center", char_space_pt=4)
    pdf.vertical_space(2)
    pdf.gradient_line(75, pdf.cur_y, 135, COLOR_GOLD, COLOR_CREAM, segments=20)
    pdf.vertical_space(8)


def _decorate_page(pdf):
    """Fondo cream uniforme + marco doble dorado + diamantes en esquinas."""
    # Fondo cream sólido (sin degradé en dos tonos)
    pdf.rect_filled(0, 0, 210, 297, COLOR_CREAM)

    # Marco doble dorado: exterior grueso, interior fino
    pdf.rect_outlined(8, 8, 194, 281, COLOR_GOLD, thickness=0.9)
    pdf.rect_outlined(11, 11, 188, 275, COLOR_GOLD_LT, thickness=0.3)

    # Esquinas decorativas con diamantes
    for (x, y) in [(11, 11), (199, 11), (11, 286), (199, 286)]:
        pdf.ornament_diamond(x, y, 1.2, COLOR_GOLD)


def _draw_brand_header(pdf):
    """Header con nombre del coro en serif grande + lema en italic."""
    # CORO PACEM DEUS en Times-Bold grande con tracking amplio (imita Cinzel)
    pdf.set_font("SerifBold", 24)
    pdf.set_color(*COLOR_BROWN)
    pdf.text_line("CORO PACEM DEUS", align="center", char_space_pt=6)
    pdf.vertical_space(3)
    # Linea con gradiente dorado
    pdf.gradient_line(60, pdf.cur_y, 150, COLOR_GOLD, COLOR_CREAM, segments=30)
    pdf.vertical_space(2)

    # Lema en italic Times
    pdf.set_font("SerifItalic", 12)
    pdf.set_color(*COLOR_BROWN_LT)
    pdf.text_line("Cantamos al Amor de los Amores", align="center")

    pdf.vertical_space(4)
    pdf.ornament_flourish(105, pdf.cur_y, 50, COLOR_GOLD)
    pdf.vertical_space(6)


def _draw_certificate_title(pdf):
    pdf.set_font("Serif", 9)
    pdf.set_color(*COLOR_GOLD_DK)
    pdf.text_line("— CONTRATO DE SERVICIO MUSICAL —", align="center", char_space_pt=2)
    pdf.vertical_space(8)


def _draw_couple_block(pdf, novios):
    """
    Bloque destacado con los nombres de la pareja en script-style (italic
    serif grande), centrado. Es la pieza emocional del documento.
    """
    novio = (novios.get("nombre_novio") or "").strip()
    novia = (novios.get("nombre_novia") or "").strip()

    pdf.set_font("Serif", 9)
    pdf.set_color(*COLOR_BROWN_LT)
    pdf.text_line("ENTRE LOS CONTRATANTES", align="center", char_space_pt=2.5)
    pdf.vertical_space(3)

    if novio:
        pdf.set_font("SerifItalic", 22)
        pdf.set_color(*COLOR_BROWN)
        pdf.text_line(novio, align="center")
        pdf.vertical_space(1)

    if novio and novia:
        pdf.set_font("SerifItalic", 14)
        pdf.set_color(*COLOR_GOLD_DK)
        pdf.text_line("&", align="center")
        pdf.vertical_space(1)

    if novia:
        pdf.set_font("SerifItalic", 22)
        pdf.set_color(*COLOR_BROWN)
        pdf.text_line(novia, align="center")

    # Documentos en linea fina
    pdf.vertical_space(3)
    doc_n = (novios.get("documento_novio") or "").strip()
    doc_a = (novios.get("documento_novia") or "").strip()
    tel = (novios.get("telefono") or "").strip()
    info_parts = []
    if doc_n:
        info_parts.append(f"DNI {doc_n}")
    if doc_a:
        info_parts.append(f"DNI {doc_a}")
    if tel:
        info_parts.append(f"Telefono {tel}")
    if info_parts:
        pdf.set_font("Serif", 9)
        pdf.set_color(*COLOR_BROWN_LT)
        pdf.text_line("  ·  ".join(info_parts), align="center")

    pdf.vertical_space(6)


def _draw_ceremony_block(pdf, boda):
    """Datos de la ceremonia: fecha, hora, local, direccion, ubicacion."""
    _draw_section_title(pdf, "LA CEREMONIA")
    pdf.vertical_space(2)

    fecha_label = _fmt_date_long(boda.get("fecha_boda"))
    hora = (boda.get("hora_boda") or "").strip()
    local = (boda.get("nombre_local") or "").strip() or "—"
    direccion = (boda.get("direccion_local") or "").strip() or "—"

    # Frase con fecha + hora en cuerpo elegante (no tabla)
    if fecha_label and hora:
        pdf.set_font("SerifItalic", 13)
        pdf.set_color(*COLOR_BROWN)
        pdf.text_line(f"El {fecha_label}, a las {hora} horas", align="center")
    elif fecha_label:
        pdf.set_font("SerifItalic", 13)
        pdf.set_color(*COLOR_BROWN)
        pdf.text_line(f"El {fecha_label}", align="center")

    pdf.vertical_space(4)

    # Local centrado en bold
    pdf.set_font("SerifBold", 13)
    pdf.set_color(*COLOR_BROWN)
    pdf.text_line(local, align="center", char_space_pt=1.5)

    # Direccion en regular
    pdf.set_font("Serif", 10)
    pdf.set_color(*COLOR_BROWN_LT)
    pdf.text_line(direccion, align="center")

    # Ubicacion como link si hay coordenadas
    lat = boda.get("latitud")
    lng = boda.get("longitud")
    if lat is not None and lng is not None:
        pdf.vertical_space(2)
        link_text = "Pulse aqui para ver la ubicacion exacta"
        # Construir URL de Google Maps con las coordenadas
        url = f"https://www.google.com/maps?q={lat},{lng}"

        pdf.set_font("SerifItalic", 10)
        pdf.set_color(*COLOR_GOLD_DK)
        # Centramos manualmente
        w = _text_width_mm(link_text, 10, "F7")
        x = pdf.left_margin + (pdf.usable_width() - w) / 2
        pdf.cur_y += 5
        pdf.link_text(x, pdf.cur_y, link_text, url)

    pdf.vertical_space(8)


def _draw_servicio_block(pdf, boda, instrumentos):
    _draw_section_title(pdf, "SERVICIO MUSICAL")
    pdf.vertical_space(3)

    precio_base = float(boda.get("precio_base") or 0)
    precio_instrumentos_total = float(boda.get("precio_instrumentos") or 0)
    precio_movilidad = float(boda.get("precio_movilidad") or 0)
    precio_total = float(boda.get("precio_total") or 0)

    # ─── Paquete base ──────────────
    _draw_service_item(pdf,
        "Paquete base",
        "Director musical, piano y voz femenina",
        precio_base,
        is_main=True
    )

    # ─── Instrumentos adicionales ──────────────
    # Cada instrumento extra cuesta el mismo precio unitario. Lo
    # derivamos del total guardado / cantidad para no depender de
    # la configuracion (que puede cambiar entre versiones).
    if instrumentos:
        precio_unitario = (
            precio_instrumentos_total / len(instrumentos)
            if len(instrumentos) > 0 else 0
        )
        for ins in instrumentos:
            nombre = ins.get("nombre", "")
            _draw_service_item(pdf,
                nombre,
                "Instrumento adicional incluido en la ceremonia",
                precio_unitario,
                is_main=False
            )

    # ─── Movilidad ──────────────
    if precio_movilidad > 0:
        _draw_service_item(pdf,
            "Movilidad",
            "Traslado del coro al local de la ceremonia",
            precio_movilidad,
            is_main=False
        )

    # ─── Inversion total ──────────────
    pdf.vertical_space(4)
    # Caja destacada en cream-dk con borde gold
    box_y = pdf.cur_y
    box_h = 14
    pdf.rect_filled(pdf.left_margin, box_y, pdf.usable_width(), box_h, COLOR_CREAM_DK)
    pdf.rect_outlined(pdf.left_margin, box_y, pdf.usable_width(), box_h, COLOR_GOLD, 0.4)

    pdf.cur_y = box_y + 5
    pdf.set_font("SerifBold", 10)
    pdf.set_color(*COLOR_BROWN_LT)
    label = "INVERSION TOTAL"
    pdf.text_at(pdf.left_margin + 8, pdf.cur_y, label)

    pdf.set_font("SerifBold", 16)
    pdf.set_color(*COLOR_GOLD_DK)
    total_str = f"S/. {precio_total:,.2f}"
    w = _text_width_mm(total_str, 16, "F6")
    pdf.text_at(210 - pdf.right_margin - 8 - w, pdf.cur_y + 2, total_str)

    pdf.cur_y = box_y + box_h + 6


def _draw_service_item(pdf, title, description, monto, is_main):
    """Item de servicio en estilo estado de cuenta elegante."""
    line_h = 5

    # Linea con titulo izq + monto der
    title_font_size = 11 if is_main else 10
    pdf.set_font("SerifBold" if is_main else "Serif", title_font_size)
    pdf.set_color(*COLOR_BROWN)
    pdf.cur_y += line_h
    pdf.text_at(pdf.left_margin, pdf.cur_y, title)

    if monto is not None:
        pdf.set_font("SerifBold", title_font_size)
        pdf.set_color(*COLOR_BROWN if is_main else COLOR_BROWN_LT)
        amount_str = f"S/. {monto:,.2f}"
        w = _text_width_mm(amount_str, title_font_size, pdf.cur_font)
        pdf.text_at(210 - pdf.right_margin - w, pdf.cur_y, amount_str)
    else:
        pdf.set_font("SerifItalic", title_font_size - 1)
        pdf.set_color(*COLOR_GOLD_DK)
        amount_str = "incluido en el paquete"
        w = _text_width_mm(amount_str, title_font_size - 1, pdf.cur_font)
        pdf.text_at(210 - pdf.right_margin - w, pdf.cur_y, amount_str)

    # Descripcion en gris
    pdf.set_font("SerifItalic", 9)
    pdf.set_color(*COLOR_BROWN_LT)
    pdf.text_line(description)

    # Linea separadora muy fina
    pdf.cur_y += 1
    pdf.set_color(*COLOR_GOLD_LT)
    pdf.line(pdf.left_margin + 20, pdf.cur_y,
             210 - pdf.right_margin - 20, pdf.cur_y, thickness=0.15)


def _draw_cantos_block(pdf, setlist):
    """
    Lista de cantos elegidos en pagina propia. Cada canto ocupa una sola
    linea (titulo en italic + autor en pequeno regular del lado derecho),
    agrupados por momento liturgico con encabezado dorado.
    """
    _draw_section_title(pdf, "CANTOS ELEGIDOS")
    pdf.vertical_space(2)

    # Subtitulo descriptivo
    pdf.set_font("SerifItalic", 10)
    pdf.set_color(*COLOR_BROWN_LT)
    pdf.text_line(
        "Programa musical seleccionado por la pareja para cada momento de la ceremonia",
        align="center"
    )
    pdf.vertical_space(6)

    if not setlist:
        pdf.set_font("SerifItalic", 11)
        pdf.set_color(*COLOR_BROWN_LT)
        pdf.text_line(
            "La pareja aun esta finalizando la seleccion de cantos.",
            align="center"
        )
        pdf.text_line(
            "El programa final se entregara antes de la ceremonia.",
            align="center"
        )
        return

    # Agrupar por momento liturgico
    momentos = {}
    for item in setlist:
        m_orden = item.get("orden_momento") or 0
        m_nombre = item.get("nombre_momento") or "Momento"
        if m_orden not in momentos:
            momentos[m_orden] = (m_nombre, [])
        momentos[m_orden][1].append(item)

    for orden in sorted(momentos.keys()):
        m_nombre, cantos = momentos[orden]

        # Encabezado del momento: nombre a la izq con diamond, linea
        # gradient hacia la derecha. Mas elegante que centrado con dos
        # diamonds y mas compacto.
        pdf.vertical_space(3)
        pdf.ornament_diamond(pdf.left_margin + 1, pdf.cur_y + 3, 0.7, COLOR_GOLD)

        pdf.set_font("SerifBold", 8)
        pdf.set_color(*COLOR_GOLD_DK)
        moment_label = _t(m_nombre.upper())
        pdf.text_at(pdf.left_margin + 5, pdf.cur_y + 4, moment_label, char_space_pt=1.8)

        # Linea fina hasta el final
        label_w = _text_width_mm(moment_label, 8, "F6") + \
                  (len(moment_label) - 1) * 1.8 / MM_TO_PT
        line_x_start = pdf.left_margin + 5 + label_w + 4
        line_x_end = 210 - pdf.right_margin
        pdf.set_color(*COLOR_GOLD_LT)
        pdf.line(line_x_start, pdf.cur_y + 3, line_x_end, pdf.cur_y + 3,
                 thickness=0.2)

        pdf.cur_y += 5

        # Cantos: una linea por canto con titulo + autor en columnas
        for c in cantos:
            titulo = (c.get("titulo") or "—").strip()
            autor = (c.get("autor") or "").strip()
            _draw_canto_row(pdf, titulo, autor)


def _draw_canto_row(pdf, titulo, autor):
    """
    Una linea: titulo en italic alineado a izquierda, autor en regular
    pequeño alineado a derecha. Si el titulo es muy largo, se trunca
    elegantemente.
    """
    pdf.cur_y += 5

    # Espacio reservado para el autor a la derecha
    autor_font_size = 8.5
    if autor:
        autor_w = _text_width_mm(autor, autor_font_size, "F5")
    else:
        autor_w = 0

    # Espacio disponible para el titulo (margen + dot leader + autor)
    available_for_title = pdf.usable_width() - autor_w - (4 if autor else 0) - 4

    # Truncar titulo si pasa del ancho (suficiente con ~70 chars en italic 11)
    titulo_font_size = 11
    titulo_full = titulo
    while (_text_width_mm(titulo_full, titulo_font_size, "F7") > available_for_title
           and len(titulo_full) > 10):
        titulo_full = titulo_full[:-1]
    if titulo_full != titulo:
        titulo_full = titulo_full[:-1] + "…"

    # Dibujar titulo
    pdf.set_font("SerifItalic", titulo_font_size)
    pdf.set_color(*COLOR_BROWN)
    pdf.text_at(pdf.left_margin + 4, pdf.cur_y, titulo_full)

    # Dot leader entre titulo y autor (puntos espaciados, opcional)
    if autor:
        title_w = _text_width_mm(titulo_full, titulo_font_size, "F7")
        leader_x_start = pdf.left_margin + 4 + title_w + 2
        leader_x_end = 210 - pdf.right_margin - autor_w - 2
        if leader_x_end > leader_x_start + 5:
            pdf.set_color(*COLOR_GOLD_LT)
            # Linea fina punteada simulada (segmentos cortos)
            x = leader_x_start
            while x < leader_x_end - 0.6:
                pdf.line(x, pdf.cur_y - 1, x + 0.3, pdf.cur_y - 1, thickness=0.25)
                x += 1.4

        # Autor a la derecha
        pdf.set_font("Serif", autor_font_size)
        pdf.set_color(*COLOR_BROWN_LT)
        pdf.text_at(210 - pdf.right_margin - autor_w, pdf.cur_y, autor)


def _draw_terminos_block(pdf):
    _draw_section_title(pdf, "TERMINOS Y CONDICIONES")
    pdf.vertical_space(3)

    pdf.set_font("Serif", 9)
    pdf.set_color(*COLOR_BROWN)

    terminos = [
        ("I",   "El coro se presentara en el lugar y hora acordados con anticipacion "
                "minima de cuarenta y cinco minutos para preparativos."),
        ("II",  "El presente contrato cubre exclusivamente el servicio musical durante "
                "la ceremonia liturgica."),
        ("III", "Cualquier cambio en fecha, hora o lugar debera comunicarse con un "
                "minimo de quince dias de anticipacion y estara sujeto a disponibilidad."),
        ("IV",  "El monto total acordado incluye paquete base, instrumentos seleccionados "
                "y movilidad. No se aceptan cargos adicionales no especificados."),
        ("V",   "Los cantos elegidos pueden ajustarse hasta siete dias antes de la "
                "ceremonia previa coordinacion con el director musical."),
    ]

    for numeral, texto in terminos:
        # Numeral romano destacado a la izquierda + texto
        pdf.cur_y += 4
        pdf.set_font("SerifBold", 9)
        pdf.set_color(*COLOR_GOLD_DK)
        pdf.text_at(pdf.left_margin, pdf.cur_y, numeral)
        # Texto en columna indentada
        pdf.set_font("Serif", 9)
        pdf.set_color(*COLOR_BROWN)
        # Manual wrap respetando indent
        text_x = pdf.left_margin + 7
        max_w = pdf.usable_width() - 7
        lines = _wrap_for_width(texto, 9, "F5", max_w)
        for i, line in enumerate(lines):
            if i > 0:
                pdf.cur_y += 4
            pdf.text_at(text_x, pdf.cur_y, line)

    pdf.vertical_space(8)


def _draw_firmas_block(pdf, novios):
    """Espacios para firma manuscrita en la pagina de terminos."""
    pdf.vertical_space(8)
    _draw_section_title(pdf, "FIRMAS")
    pdf.vertical_space(15)

    margin = pdf.left_margin
    col_w = (pdf.usable_width() - 16) / 2
    y_line = pdf.cur_y

    # Lineas finas para firmar
    pdf.set_color(*COLOR_BROWN_LT)
    pdf.line(margin, y_line, margin + col_w, y_line, thickness=0.4)
    pdf.line(margin + col_w + 16, y_line,
             margin + col_w + 16 + col_w, y_line, thickness=0.4)

    # Etiquetas debajo
    pdf.cur_y = y_line + 4.5
    pdf.set_font("SerifBold", 9)
    pdf.set_color(*COLOR_BROWN)

    novio = (novios.get("nombre_novio") or "").strip()
    novia = (novios.get("nombre_novia") or "").strip()
    pareja_lbl = f"{novio} y {novia}" if novio and novia else "Los contratantes"

    w1 = _text_width_mm(pareja_lbl, 9, "F6")
    pdf.text_at(margin + (col_w - w1) / 2, pdf.cur_y, pareja_lbl)

    w2_text = "Director musical"
    w2 = _text_width_mm(w2_text, 9, "F6")
    pdf.text_at(margin + col_w + 16 + (col_w - w2) / 2, pdf.cur_y, w2_text)

    # Etiqueta secundaria
    pdf.cur_y += 4
    pdf.set_font("SerifItalic", 8)
    pdf.set_color(*COLOR_BROWN_LT)

    lbl1 = "Contratantes del servicio"
    w1b = _text_width_mm(lbl1, 8, "F7")
    pdf.text_at(margin + (col_w - w1b) / 2, pdf.cur_y, lbl1)

    lbl2 = "Coro Pacem Deus"
    w2b = _text_width_mm(lbl2, 8, "F7")
    pdf.text_at(margin + col_w + 16 + (col_w - w2b) / 2, pdf.cur_y, lbl2)

    pdf.vertical_space(12)


def _draw_brand_footer(pdf):
    """
    Footer con lema repetido y fecha de generacion. Usa text_at con
    coordenadas absolutas para no disparar el auto-page-break del
    builder (que se activaria si avanzamos cur_y mas alla del bottom
    margin).
    """
    # Florete pequeno
    pdf.ornament_flourish(105, 277, 40, COLOR_GOLD_LT)

    # Lema en italic, centrado manualmente
    pdf.set_font("SerifItalic", 9)
    pdf.set_color(*COLOR_BROWN_LT)
    lema = _t("Cantamos al Amor de los Amores")
    lema_w = _text_width_mm(lema, 9, "F7")
    pdf.text_at(pdf.left_margin + (pdf.usable_width() - lema_w) / 2, 282, lema)

    # Fecha de generacion
    pdf.set_font("Serif", 7)
    pdf.set_color(*COLOR_GOLD_DK)
    fecha_gen = datetime.datetime.now().strftime("%d de %B de %Y").lower()
    fecha_gen = _t(fecha_gen)
    fecha_text = f"Documento generado el {fecha_gen}"
    fecha_w = (_text_width_mm(fecha_text, 7, "F5")
               + (len(fecha_text) - 1) * 1.0 / MM_TO_PT)
    pdf.text_at(pdf.left_margin + (pdf.usable_width() - fecha_w) / 2, 286,
                fecha_text, char_space_pt=1)


# ═══════════════════════════════════════════════════════════════════
# Helpers
# ═══════════════════════════════════════════════════════════════════

def _draw_section_title(pdf, text):
    """Titulo de seccion: pequeno, espaciado, en gold-dk, centrado, con florete arriba."""
    pdf.ornament_flourish(105, pdf.cur_y, 30, COLOR_GOLD_LT)
    pdf.vertical_space(2)
    pdf.set_font("SerifBold", 10)
    pdf.set_color(*COLOR_GOLD_DK)
    pdf.text_line(text, align="center", char_space_pt=3.5)


def _wrap_for_width(text, font_size, font_id, max_width_mm):
    """Wrap manual sin usar el state del builder (para indent custom)."""
    words = text.split(" ")
    lines = []
    cur = ""
    for w in words:
        tentative = w if not cur else cur + " " + w
        if _text_width_mm(tentative, font_size, font_id) <= max_width_mm:
            cur = tentative
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def _t(s):
    """Aproxima Unicode -> ASCII para WinAnsiEncoding (quita acentos)."""
    if s is None:
        return ""
    s = str(s)
    normalized = unicodedata.normalize("NFD", s)
    return "".join(c for c in normalized if unicodedata.category(c) != "Mn")


MESES_ES = [
    "", "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
]


def _fmt_date_long(value):
    """'08 de Junio de 2026' o '—' si no hay fecha."""
    if value is None:
        return ""
    if isinstance(value, (datetime.date, datetime.datetime)):
        dia = value.day
        mes = MESES_ES[value.month]
        ano = value.year
        return _t(f"{dia} de {mes} de {ano}")
    return str(value)


def _build_filename(novios):
    novio = (novios.get("nombre_novio") or "").strip()
    novia = (novios.get("nombre_novia") or "").strip()
    if novio and novia:
        base = f"Contrato_PacemDeus_{novio}_y_{novia}"
    else:
        base = "Contrato_PacemDeus"
    base = _slugify(base)
    return f"{base}.pdf"


def _slugify(text):
    text = unicodedata.normalize("NFD", text)
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    text = re.sub(r"[^A-Za-z0-9_]+", "_", text)
    text = re.sub(r"_+", "_", text).strip("_")
    return text or "Contrato"


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
