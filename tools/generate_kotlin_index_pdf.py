#!/usr/bin/env python3
from __future__ import annotations

import html
import re
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

from generate_kotlin_explainer import (
    ROOT,
    categorize_file,
    collect_identifiers,
    collect_imports,
    collect_resource_index,
    detect_related_files,
    detect_role,
    detect_technologies,
    load_files,
)


OUTPUT_PATH = ROOT / "docs" / "kotlin_individual_pdfs_index.pdf"
ARIAL_UNICODE_PATH = "/System/Library/Fonts/Supplemental/Arial Unicode.ttf"
PAGE_MARGIN = 14 * mm
CATEGORY_ORDER = [
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

CATEGORY_COLORS = {
    "應用入口與畫面導航篇": ("#EAC9B5", "#6A3F2A"),
    "資料庫與本機資料層篇": ("#D8E7C8", "#3E5A2B"),
    "Fragment 與互動畫面篇": ("#CFE4F6", "#2C5073"),
    "Adapter 與清單顯示篇": ("#F7DFB9", "#73521D"),
    "Firebase 與登入同步篇": ("#FFD8CC", "#7B3A2B"),
    "備份、快照與同步狀態篇": ("#D9D6F5", "#4E447D"),
    "分析圖表與統計篇": ("#F8D9E8", "#7B385A"),
    "共用模型與工具型別篇": ("#E3E0D8", "#5F584C"),
    "其他共用支援篇": ("#E4F1EE", "#32594D"),
}

IMPORTANT_FILES = {
    "CocoCoinApp.kt": 3,
    "MainActivity.kt": 3,
    "CocoCoinRepository.kt": 3,
    "CocoCoinDatabaseHelper.kt": 3,
    "CocoCoinRoomDatabase.kt": 3,
    "Transaction.kt": 3,
    "TransactionDao.kt": 3,
    "HomeFragment.kt": 2,
    "AddTransactionFragment.kt": 2,
    "AnalysisFragment.kt": 2,
    "AssetsFragment.kt": 2,
    "SettingsFragment.kt": 2,
    "HomeViewModel.kt": 2,
    "FirebaseSyncManager.kt": 2,
    "FirebaseAuthManager.kt": 2,
}

RECOMMENDED_READING = [
    "CocoCoinApp.kt",
    "MainActivity.kt",
    "HomeFragment.kt",
    "AddTransactionFragment.kt",
    "CocoCoinRepository.kt",
    "Transaction.kt",
    "TransactionDao.kt",
    "CocoCoinRoomDatabase.kt",
    "CocoCoinDatabaseHelper.kt",
    "SettingsFragment.kt",
]


def ensure_fonts() -> None:
    try:
        pdfmetrics.getFont("ArialUnicode")
    except KeyError:
        pdfmetrics.registerFont(TTFont("ArialUnicode", ARIAL_UNICODE_PATH))


def styles() -> dict[str, ParagraphStyle]:
    ensure_fonts()
    base = getSampleStyleSheet()
    return {
        "title": ParagraphStyle(
            "IdxTitle",
            parent=base["Title"],
            fontName="ArialUnicode",
            fontSize=22,
            leading=28,
            textColor=colors.HexColor("#4c3528"),
            spaceAfter=12,
            alignment=1,
        ),
        "body": ParagraphStyle(
            "IdxBody",
            parent=base["BodyText"],
            fontName="ArialUnicode",
            fontSize=10,
            leading=15,
            textColor=colors.HexColor("#2f2a24"),
            spaceAfter=5,
        ),
        "cover_box": ParagraphStyle(
            "IdxCoverBox",
            parent=base["BodyText"],
            fontName="ArialUnicode",
            fontSize=10.2,
            leading=16,
            textColor=colors.HexColor("#2f2a24"),
            backColor=colors.HexColor("#F5EBDD"),
            borderPadding=8,
            spaceAfter=6,
        ),
        "category": ParagraphStyle(
            "IdxCategory",
            parent=base["Heading1"],
            fontName="ArialUnicode",
            fontSize=16,
            leading=22,
            textColor=colors.HexColor("#6f472f"),
            spaceBefore=10,
            spaceAfter=8,
        ),
        "category_meta": ParagraphStyle(
            "IdxCategoryMeta",
            parent=base["BodyText"],
            fontName="ArialUnicode",
            fontSize=9.4,
            leading=14,
            textColor=colors.HexColor("#5e5349"),
            spaceAfter=6,
        ),
        "file": ParagraphStyle(
            "IdxFile",
            parent=base["Heading2"],
            fontName="ArialUnicode",
            fontSize=11.5,
            leading=16,
            textColor=colors.HexColor("#5a3928"),
            backColor=colors.HexColor("#f5ede2"),
            borderPadding=5,
            spaceBefore=8,
            spaceAfter=4,
        ),
        "hint": ParagraphStyle(
            "IdxHint",
            parent=base["BodyText"],
            fontName="ArialUnicode",
            fontSize=9.2,
            leading=14,
            textColor=colors.HexColor("#6d5b4d"),
            spaceAfter=4,
        ),
        "mini": ParagraphStyle(
            "IdxMini",
            parent=base["BodyText"],
            fontName="ArialUnicode",
            fontSize=8.8,
            leading=13,
            textColor=colors.HexColor("#554A41"),
            spaceAfter=3,
        ),
    }


def first_hint(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    imports = collect_imports(text)
    techs = detect_technologies(imports, text)
    return techs[0] if techs else "以 Kotlin / Android 基本 API 為主。"


def importance_stars(path: Path) -> str:
    level = IMPORTANT_FILES.get(path.name, 1)
    return "★" * level + "☆" * (3 - level)


def recommended_reason(path: Path) -> str:
    reasons = {
        "CocoCoinApp.kt": "先理解 App 啟動後最早做哪些初始化。",
        "MainActivity.kt": "先掌握主畫面與底部導覽，後面 Fragment 才好接。",
        "HomeFragment.kt": "先看首頁怎麼把資料呈現給使用者。",
        "AddTransactionFragment.kt": "理解使用者新增記帳資料的主要流程。",
        "CocoCoinRepository.kt": "理解整個專案資料流的核心樞紐。",
        "Transaction.kt": "先看交易資料長什麼樣，後面資料庫和畫面都會用到。",
        "TransactionDao.kt": "理解交易資料怎麼進出 Room。",
        "CocoCoinRoomDatabase.kt": "看 Room 資料庫入口與註冊關係。",
        "CocoCoinDatabaseHelper.kt": "看資料操作被包裝成哪些方法。",
        "SettingsFragment.kt": "最後看設定、同步與進階功能如何串起來。",
    }
    return reasons.get(path.name, "這個檔案屬於輔助理解用，可在主線流程看完後再補。")


def related_file_names(path: Path, all_files: list[Path], identifiers: dict[Path, set[str]], resource_index: dict[str, list[str]]) -> list[str]:
    raw = detect_related_files(path, all_files, identifiers, resource_index)
    names: list[str] = []
    for item in raw:
        name = item.split("：", 1)[0].strip()
        if name.endswith(".kt") and name != path.name and name not in names:
            names.append(name)
    return names[:6]


def category_banner(category: str, count: int, s: dict[str, ParagraphStyle]) -> Table:
    bg, fg = CATEGORY_COLORS.get(category, ("#F0E8DC", "#5A4030"))
    title = Paragraph(f"<font color='{fg}'><b>{html.escape(category)}</b></font>", s["category"])
    meta = Paragraph(f"<font color='{fg}'>本章檔案數：{count}</font>", s["category_meta"])
    table = Table([[title], [meta]], colWidths=[A4[0] - PAGE_MARGIN * 2])
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor(bg)),
                ("BOX", (0, 0), (-1, -1), 1, colors.HexColor(bg)),
                ("LEFTPADDING", (0, 0), (-1, -1), 10),
                ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                ("TOPPADDING", (0, 0), (-1, -1), 6),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
            ]
        )
    )
    return table


def file_card(path: Path, role: str, line_count: int, hint: str, related: list[str], s: dict[str, ParagraphStyle]) -> Table:
    star_text = importance_stars(path)
    file_title = Paragraph(f"<b>{html.escape(path.name)}</b>　<font color='#B26A2B'>{star_text}</font>", s["file"])
    related_text = "、".join(related) if related else "目前沒有明顯抓到核心互相依賴的 Kotlin 檔。"
    rows = [
        [file_title],
        [Paragraph(f"角色定位：{role}｜總行數：{line_count}", s["body"])],
        [Paragraph(f"快速提示：{html.escape(hint)}", s["body"])],
        [Paragraph(f"互相關聯：{html.escape(related_text)}", s["mini"])],
        [Paragraph(f"對應單檔 PDF：kotlin_individual_pdfs/{path.name}.pdf", s["hint"])],
    ]
    table = Table(rows, colWidths=[A4[0] - PAGE_MARGIN * 2 - 8])
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F5EDE2")),
                ("BACKGROUND", (0, 1), (-1, -1), colors.white),
                ("BOX", (0, 0), (-1, -1), 1, colors.HexColor("#E2D8CB")),
                ("LEFTPADDING", (0, 0), (-1, -1), 8),
                ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ]
        )
    )
    return table


def build_story() -> list[object]:
    s = styles()
    files = load_files()
    identifiers = collect_identifiers(files)
    resource_index = collect_resource_index()
    grouped: dict[str, list[Path]] = {category: [] for category in CATEGORY_ORDER}
    for path in files:
        text = path.read_text(encoding="utf-8")
        imports = collect_imports(text)
        role = detect_role(text, path.name)
        grouped[categorize_file(path, role, imports, text)].append(path)

    story: list[object] = [
        Paragraph("CocoCoin Kotlin 單檔 PDF 總索引", s["title"]),
        Paragraph("這份索引是為了讓你在平板上更快找到要看的單檔 PDF。除了分類與快速提示，也額外標出推薦閱讀順序、重要檔案星號，以及檔案間的互相關聯。", s["body"]),
        Paragraph(f"單檔 PDF 資料夾：{html.escape(str((ROOT / 'docs' / 'kotlin_individual_pdfs').relative_to(ROOT)))}", s["hint"]),
        Spacer(1, 8),
    ]

    story.append(Paragraph("推薦閱讀順序", s["category"]))
    for index, file_name in enumerate(RECOMMENDED_READING, start=1):
        story.append(
            Paragraph(
                f"{index}. {file_name}：{html.escape(recommended_reason(Path(file_name)))}",
                s["cover_box"],
            )
        )
    story.append(Spacer(1, 8))
    story.append(Paragraph("星號說明：★★★ 核心主線、★★ 次要但很常碰到、★ 輔助或局部用途。", s["hint"]))

    for idx, category in enumerate(CATEGORY_ORDER):
        items = grouped.get(category) or []
        if not items:
            continue
        if idx > 0:
            story.append(PageBreak())
        items = sorted(items, key=lambda p: (-IMPORTANT_FILES.get(p.name, 1), p.name.lower()))
        story.append(category_banner(category, len(items), s))
        story.append(Spacer(1, 8))

        for path in items:
            text = path.read_text(encoding="utf-8")
            role = detect_role(text, path.name)
            line_count = len(text.splitlines())
            hint = first_hint(path)
            related = related_file_names(path, files, identifiers, resource_index)
            story.append(file_card(path, role, line_count, hint, related, s))
            story.append(Spacer(1, 8))

    return story


def main() -> None:
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(
        str(OUTPUT_PATH),
        pagesize=A4,
        leftMargin=PAGE_MARGIN,
        rightMargin=PAGE_MARGIN,
        topMargin=PAGE_MARGIN,
        bottomMargin=PAGE_MARGIN,
        title="CocoCoin Kotlin 單檔 PDF 總索引",
        author="OpenAI Codex",
    )
    doc.build(build_story())
    print(f"Wrote {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
