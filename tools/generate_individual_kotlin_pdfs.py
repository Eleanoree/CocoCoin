#!/usr/bin/env python3
from __future__ import annotations

import argparse
import html
from pathlib import Path
from textwrap import wrap

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Paragraph, Preformatted, SimpleDocTemplate, Spacer

from generate_kotlin_explainer import (
    ROOT,
    classify_line,
    collect_function_ranges,
    collect_identifiers,
    collect_imports,
    collect_resource_index,
    detect_inline_relations,
    detect_related_files,
    detect_role,
    detect_technologies,
    explain_line,
    load_files,
    setup_pdf_fonts,
    summarize_role_steps,
)


OUTPUT_DIR = ROOT / "docs" / "kotlin_individual_pdfs"
PAGE_MARGIN = 14 * mm
ARIAL_UNICODE_PATH = "/System/Library/Fonts/Supplemental/Arial Unicode.ttf"


def ensure_better_fonts() -> None:
    try:
        pdfmetrics.getFont("ArialUnicode")
    except KeyError:
        pdfmetrics.registerFont(TTFont("ArialUnicode", ARIAL_UNICODE_PATH))


def build_styles() -> dict[str, ParagraphStyle]:
    setup_pdf_fonts()
    ensure_better_fonts()
    base = getSampleStyleSheet()
    return {
        "title": ParagraphStyle(
            "IndTitle",
            parent=base["Title"],
            fontName="ArialUnicode",
            fontSize=20,
            leading=26,
            textColor=colors.HexColor("#4c3528"),
            spaceAfter=12,
        ),
        "meta": ParagraphStyle(
            "IndMeta",
            parent=base["BodyText"],
            fontName="ArialUnicode",
            fontSize=9.5,
            leading=14,
            textColor=colors.HexColor("#6d5b4d"),
            spaceAfter=4,
        ),
        "h1": ParagraphStyle(
            "IndH1",
            parent=base["Heading1"],
            fontName="ArialUnicode",
            fontSize=14,
            leading=20,
            textColor=colors.HexColor("#6f472f"),
            spaceBefore=8,
            spaceAfter=8,
        ),
        "body": ParagraphStyle(
            "IndBody",
            parent=base["BodyText"],
            fontName="ArialUnicode",
            fontSize=9.3,
            leading=14,
            textColor=colors.HexColor("#2f2a24"),
            spaceAfter=4,
        ),
        "bullet": ParagraphStyle(
            "IndBullet",
            parent=base["BodyText"],
            fontName="ArialUnicode",
            fontSize=9.3,
            leading=14,
            leftIndent=12,
            bulletIndent=0,
            spaceAfter=3,
        ),
        "line_head": ParagraphStyle(
            "IndLineHead",
            parent=base["BodyText"],
            fontName="ArialUnicode",
            fontSize=9.6,
            leading=14,
            textColor=colors.HexColor("#8c5a3c"),
            spaceBefore=8,
            spaceAfter=4,
        ),
        "code": ParagraphStyle(
            "IndCode",
            parent=base["Code"],
            fontName="ArialUnicode",
            fontSize=8,
            leading=11,
            textColor=colors.HexColor("#1f1f1f"),
            backColor=colors.HexColor("#f7f3ec"),
            borderPadding=6,
            leftIndent=8,
            rightIndent=8,
            spaceAfter=4,
        ),
        "summary_title": ParagraphStyle(
            "IndSummaryTitle",
            parent=base["Heading2"],
            fontName="ArialUnicode",
            fontSize=11,
            leading=16,
            textColor=colors.HexColor("#5b3b2a"),
            backColor=colors.HexColor("#f1e5d8"),
            borderPadding=6,
            spaceBefore=10,
            spaceAfter=4,
        ),
        "summary_body": ParagraphStyle(
            "IndSummaryBody",
            parent=base["BodyText"],
            fontName="ArialUnicode",
            fontSize=8.9,
            leading=13,
            textColor=colors.HexColor("#3a2d25"),
            leftIndent=8,
            rightIndent=8,
            spaceAfter=3,
        ),
    }


def wrapped_code_block(line: str, width: int = 78) -> str:
    if not line:
        return " "
    parts = wrap(line, width=width, replace_whitespace=False, drop_whitespace=False) or [line]
    return "\n".join(parts)


def add_bullets(story: list[object], title: str, items: list[str], styles: dict[str, ParagraphStyle]) -> None:
    story.append(Paragraph(title, styles["h1"]))
    for item in items:
        story.append(Paragraph(html.escape(item), styles["bullet"], bulletText="•"))
    story.append(Spacer(1, 4))


def build_story_for_file(
    path: Path,
    all_files: list[Path],
    identifiers: dict[Path, set[str]],
    resource_index: dict[str, list[str]],
) -> list[object]:
    styles = build_styles()
    text = path.read_text(encoding="utf-8")
    imports = collect_imports(text)
    role = detect_role(text, path.name)
    file_names = {item.stem for item in all_files}
    technologies = detect_technologies(imports, text) or ["此檔案主要使用 Kotlin 與 Android 基本 API。"]
    related = detect_related_files(path, all_files, identifiers, resource_index) or ["暫時沒有透過靜態分析找到明顯的互相搭配檔案。"]
    steps = summarize_role_steps(role)
    functions = collect_function_ranges(text, role, all_files)
    functions_by_end = {int(item["end_line"]): item for item in functions}

    story: list[object] = [
        Paragraph(path.name, styles["title"]),
        Paragraph(f"路徑：{path.relative_to(ROOT)}", styles["meta"]),
        Paragraph(f"角色定位：{role}", styles["meta"]),
        Paragraph(f"總行數：{len(text.splitlines())}", styles["meta"]),
        Spacer(1, 6),
    ]

    add_bullets(story, "使用到的技術 / 工具", technologies, styles)
    add_bullets(story, "這種寫法是否固定 / 常見步驟", steps, styles)
    add_bullets(story, "跟哪邊的程式碼互相搭配", related, styles)

    story.append(Paragraph("原始碼逐行說明", styles["h1"]))
    for idx, line in enumerate(text.splitlines(), start=1):
        line_kind = classify_line(line)
        explanation = explain_line(line, role, path.stem, file_names, resource_index)
        relations = detect_inline_relations(line.strip(), path.stem, file_names, resource_index)

        story.append(Paragraph(f"L{idx:04d}｜{line_kind}", styles["line_head"]))
        story.append(Preformatted(wrapped_code_block(line), styles["code"]))
        story.append(Paragraph(f"說明：{html.escape(explanation)}", styles["body"]))
        story.append(
            Paragraph(
                f"搭配：{html.escape('；'.join(relations) if relations else '此行未直接辨識到明顯的跨檔案引用。')}",
                styles["body"],
            )
        )

        if idx in functions_by_end:
            fn = functions_by_end[idx]
            story.append(Paragraph(f"函式總整理：{fn['name']}", styles["summary_title"]))
            story.append(Paragraph(f"作用：{html.escape(str(fn['summary']))}", styles["summary_body"]))
            for ext in fn["extensions"]:
                story.append(Paragraph(html.escape(str(ext)), styles["bullet"], bulletText="•"))
        story.append(Spacer(1, 5))

    return story


def output_name_for(path: Path) -> Path:
    return OUTPUT_DIR / f"{path.name}.pdf"


def generate(paths: list[Path]) -> list[Path]:
    all_files = load_files()
    identifiers = collect_identifiers(all_files)
    resource_index = collect_resource_index()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    outputs: list[Path] = []
    for path in paths:
        out = output_name_for(path)
        doc = SimpleDocTemplate(
            str(out),
            pagesize=A4,
            leftMargin=PAGE_MARGIN,
            rightMargin=PAGE_MARGIN,
            topMargin=PAGE_MARGIN,
            bottomMargin=PAGE_MARGIN,
            title=path.name,
            author="OpenAI Codex",
        )
        doc.build(build_story_for_file(path, all_files, identifiers, resource_index))
        outputs.append(out)
        print(f"Wrote {out}")
    return outputs


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate one PDF per Kotlin file.")
    parser.add_argument("--only", help="Generate only the given Kotlin filename, e.g. MainActivity.kt")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    files = load_files()
    if args.only:
        matched = [path for path in files if path.name == args.only]
        if not matched:
            raise SystemExit(f"File not found: {args.only}")
        generate(matched)
    else:
        generate(files)


if __name__ == "__main__":
    main()
