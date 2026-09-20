"""Genera i due mockup del README dalla palette reale dell'app (Vetro,
vedi android/app/src/main/java/com/sosound/app/ui/theme/Theme.kt),
con contenuti di fantasia — non uno screenshot vero, cosi' non porta
dentro ne' una libreria personale ne' un bug di rendering da capire."""

from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math

W = 1080
FONT_DIR = "/usr/share/fonts/truetype/dejavu"


def font(size, bold=False):
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    return ImageFont.truetype(f"{FONT_DIR}/{name}", size)


GROUND = (14, 17, 22, 255)
WASH1 = (96, 126, 166)
WASH2 = (120, 150, 180)
WASH3 = (80, 110, 150)
ACCENT = (165, 148, 255, 255)
ON_ACCENT = (18, 22, 25, 255)
INK = (233, 236, 241, 255)
INK_SOFT = (180, 188, 198, 255)
INK_FAINT = (139, 147, 158, 255)
GLASS = (255, 255, 255, 20)
GLASS_STRONG = (255, 255, 255, 31)
GLASS_EDGE = (255, 255, 255, 26)
DANGER = (255, 143, 163, 255)

# Tinte diverse per le copertine finte, cosi' le righe si distinguono
# senza dover disegnare copertine vere.
COVERS = [
    (214, 128, 96), (120, 168, 150), (150, 130, 200),
    (200, 170, 100), (110, 140, 190), (190, 110, 140),
    (140, 180, 120), (170, 150, 210),
]


def base_canvas(h):
    img = Image.new("RGBA", (W, h), GROUND)
    wash = Image.new("RGBA", (W, h), (0, 0, 0, 0))
    wd = ImageDraw.Draw(wash)
    for (cx, cy, r, color) in [
        (W * 0.15, h * 0.08, W * 0.55, (*WASH1, 46)),
        (W * 0.9, h * 0.28, W * 0.5, (*WASH2, 36)),
        (W * 0.25, h * 0.85, W * 0.6, (*WASH3, 40)),
    ]:
        wd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    wash = wash.filter(ImageFilter.GaussianBlur(120))
    img = Image.alpha_composite(img, wash)
    return img


def glass_panel(draw, box, radius=26, strong=False):
    draw.rounded_rectangle(box, radius=radius, fill=(GLASS_STRONG if strong else GLASS))
    draw.rounded_rectangle(box, radius=radius, outline=GLASS_EDGE, width=2)


def text_ellipsis(draw, text, f, max_width):
    if draw.textlength(text, font=f) <= max_width:
        return text
    while text and draw.textlength(text + "…", font=f) > max_width:
        text = text[:-1]
    return text + "…"


def nav_bar(draw, active, h):
    y0 = h - 190
    draw.rectangle([0, y0, W, h], fill=(0, 0, 0, 0))
    items = [("Libreria", "libreria"), ("Cerca", "cerca"), ("Impostazioni", "impostazioni")]
    cell = W / 3
    f = font(28)
    for i, (label, key) in enumerate(items):
        cx = cell * i + cell / 2
        color = ACCENT if key == active else INK_FAINT
        # icone semplificate, non i glifi Material veri: bastano a
        # leggersi come "libreria/cerca/impostazioni" in miniatura.
        icy = y0 + 55
        if key == "libreria":
            draw.rounded_rectangle([cx - 26, icy - 22, cx + 26, icy + 22], radius=6, outline=color, width=4)
            draw.line([cx - 26, icy - 6, cx + 26, icy - 6], fill=color, width=3)
        elif key == "cerca":
            draw.ellipse([cx - 18, icy - 26, cx + 10, icy + 2], outline=color, width=4)
            draw.line([cx + 6, icy - 2, cx + 22, icy + 18], fill=color, width=4)
        else:
            draw.ellipse([cx - 22, icy - 22, cx + 22, icy + 22], outline=color, width=4)
            draw.ellipse([cx - 8, icy - 8, cx + 8, icy + 8], fill=color)
        tw = draw.textlength(label, font=f)
        draw.text((cx - tw / 2, y0 + 100), label, font=f, fill=color)


def mockup_library(path):
    h = 2160
    img = base_canvas(h)
    d = ImageDraw.Draw(img)

    # barra di ricerca
    glass_panel(d, [40, 60, W - 40, 170], radius=55)
    d.ellipse([76, 96, 112, 132], outline=INK_FAINT, width=5)
    d.line([108, 128, 124, 144], fill=INK_FAINT, width=5)
    d.text((150, 96), "Filtra la tua musica", font=font(34), fill=INK_FAINT)

    tracks = [
        ("Controluce", "Meridiana · Onde lunghe", "3:12"),
        ("Ottavo piano", "Nastri Paralleli · Città sospesa", "2:47"),
        ("Deriva", "Sale Fine · Deriva EP", "4:05"),
        ("Vetro e sale", "Meridiana · Onde lunghe", "3:38"),
        ("Radio interna", "Costa Bassa · Radio interna", "3:21"),
        ("Fine turno", "Nastri Paralleli · Città sospesa", "2:58"),
        ("Un altro giro", "Sale Fine · Deriva EP", "3:44"),
    ]

    y = 210
    row_h = 168
    for i, (title, sub, dur) in enumerate(tracks):
        glass_panel(d, [40, y, W - 40, y + row_h - 20])
        cover = COVERS[i % len(COVERS)]
        cd = [64, y + 24, 64 + 120, y + row_h - 44]
        d.rounded_rectangle(cd, radius=18, fill=(*cover, 255))
        tx = 220
        d.text((tx, y + 34), title, font=font(38), fill=INK)
        sub_full = f"{sub} · {dur}"
        sub_shown = text_ellipsis(d, sub_full, font(28), W - 40 - 100 - tx)
        d.text((tx, y + 88), sub_shown, font=font(28), fill=INK_FAINT)
        # cestino, minimale
        txx = W - 110
        d.rounded_rectangle([txx, y + 50, txx + 48, y + 100], radius=6, outline=INK_FAINT, width=4)
        d.line([txx - 6, y + 50, txx + 54, y + 50], fill=INK_FAINT, width=4)
        y += row_h

    # barra del player
    py = h - 190 - 150
    glass_panel(d, [24, py, W - 24, py + 134], radius=30, strong=True)
    d.rounded_rectangle([48, py + 22, 48 + 90, py + 22 + 90], radius=14, fill=(*COVERS[1], 255))
    d.text((162, py + 26), "Ottavo piano", font=font(34, bold=True), fill=INK)
    d.text((162, py + 72), "Nastri Paralleli", font=font(26), fill=INK_FAINT)
    cx = W - 170
    d.polygon([(cx, py + 42), (cx, py + 92), (cx + 46, py + 67)], fill=ACCENT)
    cx2 = W - 80
    d.polygon([(cx2, py + 42), (cx2, py + 92), (cx2 + 34, py + 67)], fill=INK_SOFT)
    d.rectangle([cx2 + 34, py + 42, cx2 + 40, py + 92], fill=INK_SOFT)

    nav_bar(d, "libreria", h)

    Image.alpha_composite(Image.new("RGBA", (W, h), GROUND), img).convert("RGB").save(path)


def mockup_settings(path):
    h = 1150
    img = base_canvas(h)
    d = ImageDraw.Draw(img)

    d.text((48, 64), "Impostazioni", font=font(48, bold=True), fill=INK)

    cards = [
        {
            "title": "Sul telefono",
            "lines": [("128 brani · 412 MB", INK, 36)],
        },
        {
            "title": "Motore di scaricamento",
            "lines": [("yt-dlp pronto", INK_SOFT, 32)],
            "button": "Aggiorna yt-dlp",
        },
        {
            "title": "Aggiornamenti dell'app",
            "lines": [("Hai la versione 1.1.0", INK_SOFT, 32)],
            "button": "Controlla aggiornamenti",
        },
    ]

    y = 200
    for card in cards:
        h_card = 210 + (70 if "button" in card else 0)
        glass_panel(d, [40, y, W - 40, y + h_card])
        d.text((72, y + 34), card["title"], font=font(38, bold=True), fill=INK)
        ly = y + 94
        for text, color, size in card["lines"]:
            d.text((72, ly), text, font=font(size), fill=color)
            ly += size + 14
        if "button" in card:
            by = y + h_card - 90
            d.rounded_rectangle([72, by, W - 72, by + 60], radius=18, fill=ACCENT)
            bw = d.textlength(card["button"], font=font(30, bold=True))
            d.text(((W - bw) / 2, by + 12), card["button"], font=font(30, bold=True), fill=ON_ACCENT)
        y += h_card + 32

    Image.alpha_composite(Image.new("RGBA", (W, h), GROUND), img).convert("RGB").save(path)


if __name__ == "__main__":
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    mockup_library(f"{out}/libreria.png")
    mockup_settings(f"{out}/impostazioni.png")
    print("fatto")
