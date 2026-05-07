#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
from textwrap import wrap

from PIL import Image, ImageDraw, ImageFont

from generate_kotlin_explainer import (
    ROOT,
    PREVIEW_DIR,
    FONT_CANDIDATES,
    collect_function_ranges,
    collect_identifiers,
    collect_imports,
    collect_resource_index,
    detect_related_files,
    detect_role,
    detect_technologies,
    categorize_file,
    load_files,
    summarize_role_steps,
)


DOCS_DIR = ROOT / "docs"
FULL_PDF = DOCS_DIR / "kotlin_40_files_full_explained_visual_shareable.pdf"
CATEGORIZED_PDF = DOCS_DIR / "kotlin_40_files_by_function_visual_shareable.pdf"
PAGE_SIZE = (1240, 1754)  # close to A4 at 150 dpi
MARGIN = 70
TEXT_WIDTH = 64
TITLE_SIZE = 44
HEADING_SIZE = 28
BODY_SIZE = 22
SMALL_SIZE = 18


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for candidate in FONT_CANDIDATES:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def new_page(title: str | None = None, subtitle: str | None = None) -> Image.Image:
    image = Image.new("RGB", PAGE_SIZE, "#f7f3eb")
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((28, 28, PAGE_SIZE[0] - 28, PAGE_SIZE[1] - 28), radius=28, fill="#fffdf8", outline="#e2d8cb", width=2)
    if title:
        draw.text((MARGIN, MARGIN), title, font=load_font(TITLE_SIZE), fill="#4c3528")
    if subtitle:
        draw.text((MARGIN, MARGIN + 66), subtitle, font=load_font(BODY_SIZE), fill="#7a6554")
    return image


def draw_paragraph(draw: ImageDraw.ImageDraw, text: str, x: int, y: int, font: ImageFont.FreeTypeFont, fill: str, width_chars: int = TEXT_WIDTH, line_gap: int = 10) -> int:
    for raw in text.splitlines():
        segments = wrap(raw, width=width_chars) or [""]
        for segment in segments:
            draw.text((x, y), segment, font=font, fill=fill)
            y += font.size + line_gap
    return y


def intro_pages_for_file(path: Path, all_files: list[Path], identifiers: dict[Path, set[str]], resource_index: dict[str, list[str]]) -> list[Image.Image]:
    text = path.read_text(encoding="utf-8")
    imports = collect_imports(text)
    role = detect_role(text, path.name)
    technologies = detect_technologies(imports, text) or ["此檔案主要使用 Kotlin 與 Android 基本 API。"]
    related = detect_related_files(path, all_files, identifiers, resource_index) or ["暫時沒有透過靜態分析找到明顯的互相搭配檔案。"]
    steps = summarize_role_steps(role)
    functions = collect_function_ranges(text, role, all_files)

    pages: list[Image.Image] = []
    page = new_page(path.name, f"路徑：{path.relative_to(ROOT)}")
    draw = ImageDraw.Draw(page)
    body = load_font(BODY_SIZE)
    small = load_font(SMALL_SIZE)

    y = MARGIN + 120
    draw.text((MARGIN, y), f"角色定位：{role}", font=body, fill="#5e4737")
    y += 46
    draw.text((MARGIN, y), f"總行數：{len(text.splitlines())}", font=body, fill="#5e4737")
    y += 60

    sections = [
        ("使用到的技術 / 工具", technologies),
        ("常見步驟 / 固定寫法", steps),
        ("搭配檔案", related),
    ]
    for heading, items in sections:
        draw.text((MARGIN, y), heading, font=load_font(HEADING_SIZE), fill="#6f472f")
        y += 44
        for item in items:
            y = draw_paragraph(draw, f"• {item}", MARGIN + 12, y, body, "#2f2a24", width_chars=72)
            y += 6
        y += 16

    if y > PAGE_SIZE[1] - 420:
        pages.append(page)
        page = new_page(path.name, "函式總整理")
        draw = ImageDraw.Draw(page)
        y = MARGIN + 120

    draw.text((MARGIN, y), "函式總整理", font=load_font(HEADING_SIZE), fill="#6f472f")
    y += 44
    if not functions:
        y = draw_paragraph(draw, "這個檔案沒有辨識到一般函式區塊，通常代表它是資料模型、介面或狀態容器。", MARGIN, y, body, "#2f2a24", width_chars=72)
    else:
        for fn in functions:
            block = [
                f"{fn['name']}（L{int(fn['start_line']):04d}-L{int(fn['end_line']):04d}）",
                f"作用：{fn['summary']}",
                *[f"延伸：{ext}" for ext in fn["extensions"]],
            ]
            block_height = 24 * sum(max(1, len(wrap(line, width=70))) for line in block) + 22
            if y + block_height > PAGE_SIZE[1] - MARGIN:
                pages.append(page)
                page = new_page(path.name, "函式總整理（續）")
                draw = ImageDraw.Draw(page)
                y = MARGIN + 120
            draw.rounded_rectangle((MARGIN, y, PAGE_SIZE[0] - MARGIN, y + block_height), radius=18, fill="#fcf7ef", outline="#dfd2c3", width=2)
            inner_y = y + 16
            inner_y = draw_paragraph(draw, block[0], MARGIN + 16, inner_y, body, "#6f472f", width_chars=68)
            inner_y = draw_paragraph(draw, block[1], MARGIN + 16, inner_y, small, "#2f2a24", width_chars=72)
            for extra in block[2:]:
                inner_y = draw_paragraph(draw, f"• {extra[3:]}", MARGIN + 24, inner_y, small, "#4a3a2f", width_chars=70)
            y += block_height + 16

    pages.append(page)
    return pages


def screenshot_pages_for_file(path: Path) -> list[Image.Image]:
    previews = sorted(PREVIEW_DIR.glob(f"{path.stem}_part*.png"))
    pages: list[Image.Image] = []
    for idx, preview in enumerate(previews, start=1):
        page = new_page(path.name, f"程式碼截圖 {idx}/{len(previews)}")
        canvas = ImageDraw.Draw(page)
        img = Image.open(preview).convert("RGB")
        max_w = PAGE_SIZE[0] - MARGIN * 2
        max_h = PAGE_SIZE[1] - 220
        ratio = min(max_w / img.width, max_h / img.height, 1.0)
        resized = img.resize((int(img.width * ratio), int(img.height * ratio)))
        x = (PAGE_SIZE[0] - resized.width) // 2
        y = 160
        page.paste(resized, (x, y))
        canvas.text((MARGIN, PAGE_SIZE[1] - 72), f"{path.name}｜第 {idx} 張截圖", font=load_font(SMALL_SIZE), fill="#7a6554")
        pages.append(page)
    return pages


def build_pdf(output: Path, categorized: bool) -> None:
    all_files = load_files()
    identifiers = collect_identifiers(all_files)
    resource_index = collect_resource_index()

    if categorized:
        groups: dict[str, list[Path]] = {}
        order = [
            "應用入口與畫面導航篇",
            "資料庫與本機資料層篇",
            "Fragment 與互動畫面篇",
            "Adapter 與清單顯示篇",
            "Firebase 與登入同步篇",
            "備份、快照與同步狀態篇",
            "分析圖表與統計篇",
            "共用模型與工具型別篇",
            "其他共用支援篇",
        ]
        for category in order:
            groups[category] = []
        for path in all_files:
            text = path.read_text(encoding="utf-8")
            imports = collect_imports(text)
            role = detect_role(text, path.name)
            groups[categorize_file(path, role, imports, text)].append(path)
        ordered_items: list[tuple[str | None, Path]] = []
        for category in order:
            for path in groups[category]:
                ordered_items.append((category, path))
    else:
        ordered_items = [(None, path) for path in all_files]

    pdf_pages: list[Image.Image] = []
    cover = new_page(
        "CocoCoin Kotlin 視覺分享版 PDF",
        "含編輯器風格程式碼截圖、檔案重點、函式總整理",
    )
    cover_draw = ImageDraw.Draw(cover)
    cover_y = MARGIN + 180
    cover_y = draw_paragraph(cover_draw, "這份 PDF 適合直接分享給同學、老師或團隊成員閱讀。", MARGIN, cover_y, load_font(BODY_SIZE), "#2f2a24")
    cover_y = draw_paragraph(cover_draw, "若需要最完整逐行內容，請再對照同名的 HTML 視覺版。", MARGIN, cover_y + 10, load_font(BODY_SIZE), "#2f2a24")
    pdf_pages.append(cover)

    current_category = None
    for category, path in ordered_items:
        if categorized and category != current_category:
            current_category = category
            cat_page = new_page(category, "功能分類章節")
            cat_draw = ImageDraw.Draw(cat_page)
            draw_paragraph(cat_draw, "接下來會依這個章節列出每個 Kotlin 檔案的重點頁與程式碼截圖頁。", MARGIN, MARGIN + 180, load_font(BODY_SIZE), "#2f2a24")
            pdf_pages.append(cat_page)

        pdf_pages.extend(intro_pages_for_file(path, all_files, identifiers, resource_index))
        pdf_pages.extend(screenshot_pages_for_file(path))

    if not pdf_pages:
        raise RuntimeError("No pages generated for PDF")

    first, rest = pdf_pages[0], pdf_pages[1:]
    rgb_first = first.convert("RGB")
    rgb_rest = [img.convert("RGB") for img in rest]
    output.parent.mkdir(parents=True, exist_ok=True)
    rgb_first.save(output, "PDF", resolution=150.0, save_all=True, append_images=rgb_rest)


def main() -> None:
    build_pdf(FULL_PDF, categorized=False)
    print(f"Wrote {FULL_PDF}")
    build_pdf(CATEGORIZED_PDF, categorized=True)
    print(f"Wrote {CATEGORIZED_PDF}")


if __name__ == "__main__":
    main()
