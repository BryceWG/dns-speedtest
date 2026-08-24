from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "screen-test.png"
SETTINGS = ROOT / "screen-settings.png"
OUT = ROOT / "poster.png"


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    names = (
        "msyhbd.ttc" if bold else "msyh.ttc",
        "msyhbd.ttf" if bold else "msyh.ttf",
        "msyh.ttc",
        "simhei.ttf",
    )
    for name in names:
        path = Path(r"C:\Windows\Fonts") / name
        if path.exists():
            try:
                return ImageFont.truetype(str(path), size=size, index=0)
            except OSError:
                continue
    return ImageFont.load_default()


def round_phone(src: Image.Image, radius: int = 72, max_height: int = 1180) -> Image.Image:
    img = src.convert("RGBA")
    ratio = max_height / img.height
    img = img.resize((int(img.width * ratio), max_height), Image.Resampling.LANCZOS)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.width, img.height), radius, fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0))
    out.putalpha(mask)
    return out


def drop_shadow(img: Image.Image, offset: int = 28, blur: int = 42) -> Image.Image:
    shadow = Image.new("RGBA", (img.width + offset * 4, img.height + offset * 4), (0, 0, 0, 0))
    layer = Image.new("RGBA", img.size, (20, 28, 45, 70))
    shadow.paste(layer, (offset * 2, offset * 2), img.split()[-1])
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    shadow.paste(img, (offset * 2 - 8, offset * 2 - 18), img)
    return shadow


def main() -> None:
    canvas_w, canvas_h = 2200, 1320
    canvas = Image.new("RGB", (canvas_w, canvas_h), "#F4F7FB")
    draw = ImageDraw.Draw(canvas)
    draw.rectangle((0, 0, canvas_w, 420), fill="#EAF1F8")
    draw.ellipse((-180, -220, 620, 520), fill="#D9E8F6")
    draw.ellipse((1680, 820, 2480, 1520), fill="#E3EEF7")

    title = load_font(86, bold=True)
    subtitle = load_font(32)
    caption = load_font(28, bold=True)
    draw.text((120, 88), "DNS 测速", font=title, fill="#1A2332")
    draw.text(
        (120, 198),
        "主动发起 DoH / DoT 查询，对比延迟、稳定性与解析结果",
        font=subtitle,
        fill="#5B6778",
    )
    draw.text((120, 252), "测试页  ·  设置与服务器管理", font=subtitle, fill="#7A8699")

    left = drop_shadow(round_phone(Image.open(TEST)))
    right = drop_shadow(round_phone(Image.open(SETTINGS)))
    left_x, right_x = 120, 1120
    phone_y = 300
    canvas.paste(left, (left_x, phone_y), left)
    canvas.paste(right, (right_x, phone_y), right)

    draw.text((left_x + left.width // 2 - 40, 1248), "测试", font=caption, fill="#3D4A5C")
    draw.text((right_x + right.width // 2 - 40, 1248), "设置", font=caption, fill="#5B6778")

    canvas.save(OUT, "PNG", optimize=True)
    print(f"wrote {OUT} {canvas.size}")


if __name__ == "__main__":
    main()
