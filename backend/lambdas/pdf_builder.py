"""
pdf_builder.py
Generador de PDF minimalista escrito en Python puro (solo stdlib).

Reemplazo de fpdf2 para evitar la dependencia con fontTools que rompe
en Lambda. Solo soporta lo que necesitamos para el contrato:
  - Texto en Helvetica, Helvetica-Bold, Helvetica-Oblique (fuentes base
    del estandar PDF, NO requieren embedding)
  - Lineas rectas
  - Rectangulos rellenos
  - Multiples paginas
  - Encoding WinAnsi (latin-1) - los acentos se aproximan a ASCII

El API imita la parte de fpdf2 que usamos para minimizar cambios en
get_boda_contrato_pdf.py.
"""

import datetime
import unicodedata
import zlib


# ─── Constantes PDF ────────────────────────────────────────────
PAGE_WIDTH_MM = 210      # A4
PAGE_HEIGHT_MM = 297
MM_TO_PT = 72.0 / 25.4

# Coordenadas PDF: origen abajo-izquierda. Nosotros trabajamos arriba-
# izquierda como fpdf2 y convertimos al renderizar.


class PDFBuilder:
    """
    Builder PDF muy simple. API tipo fpdf:
        pdf = PDFBuilder()
        pdf.add_page()
        pdf.set_font("Bold", 12)
        pdf.set_color(74, 60, 41)
        pdf.text_line(x_mm, y_mm, "Hola")
        ...
        bytes_pdf = pdf.build()
    """

    def __init__(self):
        self.pages = []                  # cada pagina: lista de comandos PDF
        self.current_page = None
        self.cur_font = "F1"
        self.cur_font_size = 10
        self.cur_color = (0, 0, 0)       # RGB 0..255 (texto y stroke)
        self.cur_y = 0.0
        self.cur_x = 20.0
        self.left_margin = 20.0
        self.right_margin = 20.0
        self.top_margin = 20.0
        self.bottom_margin = 20.0
        # Hook para decoracion automatica: si se setea, se llama justo
        # despues de crear una pagina (antes de cualquier contenido).
        # Permite que cada pagina herede el mismo marco/fondo sin que el
        # caller tenga que recordarlo en cada auto-page-break.
        self.page_decorator = None

    # ─── API de pagina ────────────────────────────────
    def add_page(self):
        self.current_page = []
        self.pages.append(self.current_page)
        self.cur_y = self.top_margin
        if self.page_decorator is not None:
            self.page_decorator(self)

    def page_width(self):
        return PAGE_WIDTH_MM

    def page_height(self):
        return PAGE_HEIGHT_MM

    def usable_width(self):
        return PAGE_WIDTH_MM - self.left_margin - self.right_margin

    # ─── API de fuente y color ────────────────────────
    def set_font(self, style, size):
        """
        style: 'Regular' | 'Bold' | 'Italic' | 'BoldItalic'
               | 'Serif' | 'SerifBold' | 'SerifItalic' | 'SerifBoldItalic'

        Helvetica = F1/F2/F3/F4, Times = F5/F6/F7/F8.
        """
        self.cur_font = {
            "Regular":         "F1",
            "Bold":            "F2",
            "Italic":          "F3",
            "BoldItalic":      "F4",
            "Serif":           "F5",
            "SerifBold":       "F6",
            "SerifItalic":     "F7",
            "SerifBoldItalic": "F8",
        }.get(style, "F1")
        self.cur_font_size = size

    def set_color(self, r, g, b):
        self.cur_color = (r, g, b)

    # ─── Primitivas de dibujo ─────────────────────────
    def text_at(self, x_mm, y_mm, text, char_space_pt=0.0):
        """Dibuja texto con su esquina inferior izquierda en (x, y) mm.

        char_space_pt: espacio extra entre caracteres en puntos. Util para
        evocar el look de fuentes con tracking amplio (Cinzel) usando
        Times-Bold.
        """
        text = _ansi_safe(text)
        if not text:
            return
        x_pt = x_mm * MM_TO_PT
        y_pt = (PAGE_HEIGHT_MM - y_mm) * MM_TO_PT
        r, g, b = self.cur_color
        cmd = (
            f"q\n"
            f"{r / 255:.3f} {g / 255:.3f} {b / 255:.3f} rg\n"
            f"BT /{self.cur_font} {self.cur_font_size} Tf "
        )
        if char_space_pt != 0:
            cmd += f"{char_space_pt:.2f} Tc "
        cmd += (
            f"{x_pt:.2f} {y_pt:.2f} Td ({_escape_pdf_string(text)}) Tj ET\n"
            f"Q\n"
        )
        self.current_page.append(cmd)

    def text_line(self, text, align="left", indent=0.0, char_space_pt=0.0):
        """
        Dibuja texto en (cur_x + indent, cur_y) y avanza cur_y por
        font_size+leading. Auto-wrap si el texto excede el ancho usable.
        """
        if text is None:
            text = ""
        text = _ansi_safe(text)

        lines = self._wrap_text(text, self.usable_width() - indent, char_space_pt)
        line_height_mm = self.cur_font_size * 0.45

        for line in lines:
            self._maybe_new_page(line_height_mm)
            self.cur_y += line_height_mm
            if align == "center":
                w = _text_width_mm(line, self.cur_font_size, self.cur_font) + \
                    _char_spacing_width_mm(line, char_space_pt)
                x = self.left_margin + (self.usable_width() - w) / 2
            elif align == "right":
                w = _text_width_mm(line, self.cur_font_size, self.cur_font) + \
                    _char_spacing_width_mm(line, char_space_pt)
                x = PAGE_WIDTH_MM - self.right_margin - w
            else:
                x = self.left_margin + indent
            self.text_at(x, self.cur_y, line, char_space_pt=char_space_pt)

    def text_columns(self, left_text, right_text, left_style="Regular",
                      right_style="Bold", left_color=None, right_color=None):
        """Una linea con texto izquierdo y derecho en la misma altura."""
        line_height_mm = self.cur_font_size * 0.45
        self._maybe_new_page(line_height_mm)
        self.cur_y += line_height_mm

        self.set_font(left_style, self.cur_font_size)
        if left_color: self.set_color(*left_color)
        self.text_at(self.left_margin, self.cur_y, left_text)

        self.set_font(right_style, self.cur_font_size)
        if right_color: self.set_color(*right_color)
        w = _text_width_mm(right_text, self.cur_font_size, self.cur_font)
        x = PAGE_WIDTH_MM - self.right_margin - w
        self.text_at(x, self.cur_y, right_text)

    def link_text(self, x_mm, y_mm, text, url):
        """
        Dibuja texto que es un hyperlink. Lo registra como anotacion del
        PDF para que apps lectoras lo abran. Visualmente el texto se
        muestra subrayado en el color actual.
        """
        text = _ansi_safe(text)
        if not text:
            return
        # Renderear texto normal
        self.text_at(x_mm, y_mm, text)
        # Subrayar
        w = _text_width_mm(text, self.cur_font_size, self.cur_font)
        underline_y = y_mm + 0.8
        self.line(x_mm, underline_y, x_mm + w, underline_y, thickness=0.25)
        # Registrar link annotation
        self._add_link_annotation(x_mm, y_mm, w, self.cur_font_size * 0.4, url)

    def _add_link_annotation(self, x_mm, y_top_mm, w_mm, h_mm, url):
        """Almacena rect + URL para emitir como annotation en build()."""
        if not hasattr(self, "_page_links"):
            self._page_links = {}
        page_idx = len(self.pages) - 1
        self._page_links.setdefault(page_idx, []).append(
            (x_mm, y_top_mm - h_mm, w_mm, h_mm, url)
        )

    def line(self, x1_mm, y1_mm, x2_mm, y2_mm, thickness=0.3):
        x1 = x1_mm * MM_TO_PT
        y1 = (PAGE_HEIGHT_MM - y1_mm) * MM_TO_PT
        x2 = x2_mm * MM_TO_PT
        y2 = (PAGE_HEIGHT_MM - y2_mm) * MM_TO_PT
        r, g, b = self.cur_color
        cmd = (
            f"q\n"
            f"{r / 255:.3f} {g / 255:.3f} {b / 255:.3f} RG\n"
            f"{thickness:.2f} w\n"
            f"{x1:.2f} {y1:.2f} m {x2:.2f} {y2:.2f} l S\n"
            f"Q\n"
        )
        self.current_page.append(cmd)

    def rect_filled(self, x_mm, y_mm, w_mm, h_mm, rgb):
        """Rectangulo relleno (sin borde). Coords desde arriba-izquierda."""
        x = x_mm * MM_TO_PT
        y = (PAGE_HEIGHT_MM - y_mm - h_mm) * MM_TO_PT
        w = w_mm * MM_TO_PT
        h = h_mm * MM_TO_PT
        r, g, b = rgb
        cmd = (
            f"q\n"
            f"{r / 255:.3f} {g / 255:.3f} {b / 255:.3f} rg\n"
            f"{x:.2f} {y:.2f} {w:.2f} {h:.2f} re f\n"
            f"Q\n"
        )
        self.current_page.append(cmd)

    def rect_outlined(self, x_mm, y_mm, w_mm, h_mm, rgb, thickness=0.5):
        x = x_mm * MM_TO_PT
        y = (PAGE_HEIGHT_MM - y_mm - h_mm) * MM_TO_PT
        w = w_mm * MM_TO_PT
        h = h_mm * MM_TO_PT
        r, g, b = rgb
        cmd = (
            f"q\n"
            f"{r / 255:.3f} {g / 255:.3f} {b / 255:.3f} RG\n"
            f"{thickness:.2f} w\n"
            f"{x:.2f} {y:.2f} {w:.2f} {h:.2f} re S\n"
            f"Q\n"
        )
        self.current_page.append(cmd)

    def vertical_gradient(self, x_mm, y_mm, w_mm, h_mm, color_top, color_bottom,
                          bands=40):
        """
        Simula un gradiente vertical con N franjas horizontales muy finas.
        bands=40 es imperceptible al ojo a tamano de pagina A4.
        """
        for i in range(bands):
            t = i / (bands - 1)
            r = int(color_top[0] + (color_bottom[0] - color_top[0]) * t)
            g = int(color_top[1] + (color_bottom[1] - color_top[1]) * t)
            b = int(color_top[2] + (color_bottom[2] - color_top[2]) * t)
            band_y = y_mm + (h_mm / bands) * i
            band_h = (h_mm / bands) + 0.1   # solape minimo para evitar lineas
            self.rect_filled(x_mm, band_y, w_mm, band_h, (r, g, b))

    def gradient_line(self, x1_mm, y_mm, x2_mm, color_center, color_edges,
                       segments=24, thickness=0.4):
        """
        Linea horizontal con gradiente: transparente -> color_center -> transparente.
        Replica el divisor de la web (linear-gradient transparent -> gold -> transparent).
        Como PDF no tiene alpha facil, simulamos transicionando hacia color_edges
        (que debe ser el fondo: cream).
        """
        length = x2_mm - x1_mm
        seg_len = length / segments
        for i in range(segments):
            # Curva de campana centrada
            t = (i + 0.5) / segments  # 0..1
            # Atenuacion suave: peak en t=0.5
            attenuation = 1.0 - abs(t - 0.5) * 2  # triangle 0..1..0
            # Suavizar (ease)
            attenuation = attenuation ** 0.6
            r = int(color_edges[0] + (color_center[0] - color_edges[0]) * attenuation)
            g = int(color_edges[1] + (color_center[1] - color_edges[1]) * attenuation)
            b = int(color_edges[2] + (color_center[2] - color_edges[2]) * attenuation)
            self.set_color(r, g, b)
            x1 = x1_mm + seg_len * i
            x2 = x1 + seg_len + 0.1
            self.line(x1, y_mm, x2, y_mm, thickness=thickness)

    def ornament_diamond(self, x_mm, y_mm, size_mm, rgb):
        """Pequeno rombo decorativo (florete simple)."""
        cx = x_mm * MM_TO_PT
        cy = (PAGE_HEIGHT_MM - y_mm) * MM_TO_PT
        s = size_mm * MM_TO_PT
        r, g, b = rgb
        cmd = (
            f"q\n"
            f"{r / 255:.3f} {g / 255:.3f} {b / 255:.3f} rg\n"
            f"{cx:.2f} {cy - s:.2f} m "
            f"{cx + s:.2f} {cy:.2f} l "
            f"{cx:.2f} {cy + s:.2f} l "
            f"{cx - s:.2f} {cy:.2f} l "
            f"h f\n"
            f"Q\n"
        )
        self.current_page.append(cmd)

    def ornament_flourish(self, cx_mm, y_mm, width_mm, rgb):
        """
        Florete decorativo: linea horizontal con un diamond al centro y
        flecos en los extremos. Sirve como separador elegante entre
        secciones.
        """
        self.set_color(*rgb)
        half = width_mm / 2
        # Diamantes pequenos a los extremos
        self.ornament_diamond(cx_mm - half, y_mm, 0.6, rgb)
        self.ornament_diamond(cx_mm + half, y_mm, 0.6, rgb)
        # Lineas finas hacia el centro
        self.line(cx_mm - half + 1, y_mm, cx_mm - 2.5, y_mm, thickness=0.3)
        self.line(cx_mm + 2.5, y_mm, cx_mm + half - 1, y_mm, thickness=0.3)
        # Diamond grande al centro
        self.ornament_diamond(cx_mm, y_mm, 1.2, rgb)

    def horizontal_line(self, length_mm=None, thickness=0.3):
        if length_mm is None:
            length_mm = self.usable_width()
        self.cur_y += 1
        self.line(self.left_margin, self.cur_y,
                  self.left_margin + length_mm, self.cur_y,
                  thickness=thickness)
        self.cur_y += 1

    def horizontal_line_full(self, thickness=0.3):
        self.cur_y += 1
        self.line(self.left_margin, self.cur_y,
                  PAGE_WIDTH_MM - self.right_margin, self.cur_y,
                  thickness=thickness)
        self.cur_y += 1

    def vertical_space(self, mm):
        self.cur_y += mm
        if self.cur_y > PAGE_HEIGHT_MM - self.bottom_margin:
            self.add_page()

    # ─── Wrapping y nueva pagina ──────────────────────
    def _maybe_new_page(self, needed_mm):
        if self.cur_y + needed_mm > PAGE_HEIGHT_MM - self.bottom_margin:
            self.add_page()

    def _wrap_text(self, text, max_width_mm, char_space_pt=0.0):
        """Wrap simple por palabras."""
        if not text:
            return [""]
        words = text.split(" ")
        lines = []
        cur = ""
        for w in words:
            tentative = w if not cur else cur + " " + w
            width = (_text_width_mm(tentative, self.cur_font_size, self.cur_font)
                     + _char_spacing_width_mm(tentative, char_space_pt))
            if width <= max_width_mm:
                cur = tentative
            else:
                if cur:
                    lines.append(cur)
                cur = w
        if cur:
            lines.append(cur)
        return lines

    # ─── Generacion del archivo PDF ───────────────────
    def build(self):
        """Construye el PDF final como bytes."""
        objects = []
        page_kids_objs = []
        page_annot_obj_ids = {}   # page_idx -> [annot_obj_ids]

        # Objeto 1: Catalog (placeholder)
        objects.append(None)
        # Objeto 2: Pages (placeholder)
        objects.append(None)
        # Objetos 3..10: Fuentes Helvetica + Times
        objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Oblique /Encoding /WinAnsiEncoding >>")
        objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-BoldOblique /Encoding /WinAnsiEncoding >>")
        objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Times-Roman /Encoding /WinAnsiEncoding >>")
        objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Times-Bold /Encoding /WinAnsiEncoding >>")
        objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Times-Italic /Encoding /WinAnsiEncoding >>")
        objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Times-BoldItalic /Encoding /WinAnsiEncoding >>")

        page_links = getattr(self, "_page_links", {})

        # Generar objetos Page + Content (y Annotations si hay links)
        for page_idx, page_cmds in enumerate(self.pages):
            content_stream = "".join(page_cmds).encode("latin-1", errors="replace")
            content_compressed = zlib.compress(content_stream)
            content_obj = (
                b"<< /Length " + str(len(content_compressed)).encode() +
                b" /Filter /FlateDecode >>\nstream\n" +
                content_compressed +
                b"\nendstream"
            )

            # Crear annotations para links de esta pagina
            annot_ids = []
            for (x_mm, y_mm, w_mm, h_mm, url) in page_links.get(page_idx, []):
                # Coordenadas PDF (origen abajo-izquierda)
                x1 = x_mm * MM_TO_PT
                y1 = (PAGE_HEIGHT_MM - y_mm - h_mm) * MM_TO_PT
                x2 = (x_mm + w_mm) * MM_TO_PT
                y2 = (PAGE_HEIGHT_MM - y_mm) * MM_TO_PT
                annot = (
                    b"<< /Type /Annot /Subtype /Link "
                    b"/Rect [" + f"{x1:.2f} {y1:.2f} {x2:.2f} {y2:.2f}".encode() + b"] "
                    b"/Border [0 0 0] "
                    b"/A << /Type /Action /S /URI /URI ("
                    + _escape_pdf_string(url).encode("latin-1", errors="replace") +
                    b") >> >>"
                )
                annot_id = len(objects) + 1
                objects.append(annot)
                annot_ids.append(annot_id)

            page_obj_id = len(objects) + 1
            content_obj_id = page_obj_id + 1

            # Resources con las 8 fuentes
            resources = (
                b"/Resources << /Font << "
                b"/F1 3 0 R /F2 4 0 R /F3 5 0 R /F4 6 0 R "
                b"/F5 7 0 R /F6 8 0 R /F7 9 0 R /F8 10 0 R "
                b">> >>"
            )
            annots_str = b""
            if annot_ids:
                kids = " ".join(f"{i} 0 R" for i in annot_ids)
                annots_str = b" /Annots [" + kids.encode() + b"]"

            page_obj = (
                b"<< /Type /Page /Parent 2 0 R "
                b"/MediaBox [0 0 595.28 841.89] " +
                resources +
                b" /Contents " + str(content_obj_id).encode() + b" 0 R" +
                annots_str +
                b" >>"
            )
            objects.append(page_obj)
            objects.append(content_obj)
            page_kids_objs.append(page_obj_id)

        # Llenar Catalog (objeto 1) y Pages (objeto 2)
        objects[0] = b"<< /Type /Catalog /Pages 2 0 R >>"
        kids = " ".join(f"{i} 0 R" for i in page_kids_objs)
        objects[1] = (
            b"<< /Type /Pages /Count " + str(len(page_kids_objs)).encode() +
            b" /Kids [" + kids.encode() + b"] >>"
        )

        # Escribir
        out = bytearray()
        out += b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n"
        offsets = [0]

        for i, obj in enumerate(objects, start=1):
            offsets.append(len(out))
            out += f"{i} 0 obj\n".encode()
            out += obj
            out += b"\nendobj\n"

        xref_offset = len(out)
        out += f"xref\n0 {len(objects) + 1}\n".encode()
        out += b"0000000000 65535 f \n"
        for off in offsets[1:]:
            out += f"{off:010d} 00000 n \n".encode()

        out += b"trailer\n"
        out += (
            b"<< /Size " + str(len(objects) + 1).encode() +
            b" /Root 1 0 R >>\n"
        )
        out += f"startxref\n{xref_offset}\n%%EOF".encode()

        return bytes(out)


# ─── Helpers internos ──────────────────────────────────────────

# Anchos aproximados de caracteres Helvetica en unidades 1/1000 em.
# Suficiente para wrapping correcto. Para Bold usamos 105% de los mismos.
_HELVETICA_WIDTHS = {
    " ": 278, "!": 278, "\"": 355, "#": 556, "$": 556, "%": 889,
    "&": 667, "'": 191, "(": 333, ")": 333, "*": 389, "+": 584,
    ",": 278, "-": 333, ".": 278, "/": 278,
    "0": 556, "1": 556, "2": 556, "3": 556, "4": 556,
    "5": 556, "6": 556, "7": 556, "8": 556, "9": 556,
    ":": 278, ";": 278, "<": 584, "=": 584, ">": 584, "?": 556,
    "@": 1015,
    "A": 667, "B": 667, "C": 722, "D": 722, "E": 667, "F": 611,
    "G": 778, "H": 722, "I": 278, "J": 500, "K": 667, "L": 556,
    "M": 833, "N": 722, "O": 778, "P": 667, "Q": 778, "R": 722,
    "S": 667, "T": 611, "U": 722, "V": 667, "W": 944, "X": 667,
    "Y": 667, "Z": 611,
    "[": 278, "\\": 278, "]": 278, "^": 469, "_": 556, "`": 333,
    "a": 556, "b": 556, "c": 500, "d": 556, "e": 556, "f": 278,
    "g": 556, "h": 556, "i": 222, "j": 222, "k": 500, "l": 222,
    "m": 833, "n": 556, "o": 556, "p": 556, "q": 556, "r": 333,
    "s": 500, "t": 278, "u": 556, "v": 500, "w": 722, "x": 500,
    "y": 500, "z": 500,
    "{": 334, "|": 260, "}": 334, "~": 584,
}

# Anchos aproximados Times-Roman 1/1000 em. Times es serif y mas estrecho.
_TIMES_WIDTHS = {
    " ": 250, "!": 333, "\"": 408, "#": 500, "$": 500, "%": 833,
    "&": 778, "'": 180, "(": 333, ")": 333, "*": 500, "+": 564,
    ",": 250, "-": 333, ".": 250, "/": 278,
    "0": 500, "1": 500, "2": 500, "3": 500, "4": 500,
    "5": 500, "6": 500, "7": 500, "8": 500, "9": 500,
    ":": 278, ";": 278, "<": 564, "=": 564, ">": 564, "?": 444,
    "@": 921,
    "A": 722, "B": 667, "C": 667, "D": 722, "E": 611, "F": 556,
    "G": 722, "H": 722, "I": 333, "J": 389, "K": 722, "L": 611,
    "M": 889, "N": 722, "O": 722, "P": 556, "Q": 722, "R": 667,
    "S": 556, "T": 611, "U": 722, "V": 722, "W": 944, "X": 722,
    "Y": 722, "Z": 611,
    "[": 333, "\\": 278, "]": 333, "^": 469, "_": 500, "`": 333,
    "a": 444, "b": 500, "c": 444, "d": 500, "e": 444, "f": 333,
    "g": 500, "h": 500, "i": 278, "j": 278, "k": 500, "l": 278,
    "m": 778, "n": 500, "o": 500, "p": 500, "q": 500, "r": 333,
    "s": 389, "t": 278, "u": 500, "v": 500, "w": 722, "x": 500,
    "y": 500, "z": 444,
    "{": 480, "|": 200, "}": 480, "~": 541,
}


def _text_width_mm(text, font_size_pt, font_id):
    # Times es ~5% mas estrecho que Helvetica
    is_serif = font_id in ("F5", "F6", "F7", "F8")
    is_bold = font_id in ("F2", "F4", "F6", "F8")
    widths = _TIMES_WIDTHS if is_serif else _HELVETICA_WIDTHS
    width_1000em = sum(widths.get(c, 500) for c in text)
    if is_bold:
        width_1000em = int(width_1000em * 1.05)
    width_pt = width_1000em / 1000.0 * font_size_pt
    return width_pt / MM_TO_PT


def _char_spacing_width_mm(text, char_space_pt):
    """Ancho extra producido por char-space (espaciado entre letras)."""
    if not char_space_pt or len(text) < 2:
        return 0.0
    # Cada caracter agrega char_space_pt al hueco, excepto el ultimo
    return (len(text) - 1) * char_space_pt / MM_TO_PT


def _ansi_safe(text):
    """
    Aproxima Unicode -> ASCII para WinAnsiEncoding. Quita acentos.
    """
    if text is None:
        return ""
    s = str(text)
    normalized = unicodedata.normalize("NFD", s)
    cleaned = "".join(c for c in normalized if unicodedata.category(c) != "Mn")
    # Reemplazar caracteres especiales conocidos
    cleaned = (cleaned
        .replace("\u2013", "-")  # en-dash
        .replace("\u2014", "-")  # em-dash
        .replace("\u2018", "'")
        .replace("\u2019", "'")
        .replace("\u201c", '"')
        .replace("\u201d", '"')
        .replace("\u2026", "...")
    )
    return cleaned


def _escape_pdf_string(text):
    """Escapa parentesis y backslash para literales PDF."""
    return (text
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")
    )
