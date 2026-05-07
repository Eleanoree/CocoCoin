#!/usr/bin/env python3
from __future__ import annotations

import re
import html
from collections import defaultdict
from pathlib import Path
from textwrap import shorten

from PIL import Image, ImageDraw, ImageFont
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.platypus import Image as RLImage
from reportlab.platypus import PageBreak, Paragraph, Preformatted, SimpleDocTemplate, Spacer


ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT / "app" / "src" / "main" / "java" / "com" / "example" / "cococoin"
RES_DIR = ROOT / "app" / "src" / "main" / "res"
MANIFEST_PATH = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
APP_GRADLE_PATH = ROOT / "app" / "build.gradle.kts"
PROJECT_GRADLE_PATH = ROOT / "build.gradle.kts"
SETTINGS_GRADLE_PATH = ROOT / "settings.gradle.kts"
OUTPUT_PATH = ROOT / "docs" / "kotlin_40_files_full_explained.txt"
CATEGORIZED_OUTPUT_PATH = ROOT / "docs" / "kotlin_40_files_by_function.txt"
PRETTY_OUTPUT_PATH = ROOT / "docs" / "kotlin_40_files_full_explained_pretty.txt"
PRETTY_CATEGORIZED_OUTPUT_PATH = ROOT / "docs" / "kotlin_40_files_by_function_pretty.txt"
VISUAL_OUTPUT_PATH = ROOT / "docs" / "kotlin_40_files_full_explained_visual.md"
VISUAL_CATEGORIZED_OUTPUT_PATH = ROOT / "docs" / "kotlin_40_files_by_function_visual.md"
VISUAL_HTML_OUTPUT_PATH = ROOT / "docs" / "kotlin_40_files_full_explained_visual.html"
VISUAL_CATEGORIZED_HTML_OUTPUT_PATH = ROOT / "docs" / "kotlin_40_files_by_function_visual.html"
VISUAL_PDF_OUTPUT_PATH = ROOT / "docs" / "kotlin_40_files_full_explained_visual.pdf"
VISUAL_CATEGORIZED_PDF_OUTPUT_PATH = ROOT / "docs" / "kotlin_40_files_by_function_visual.pdf"
PREVIEW_DIR = ROOT / "docs" / "code_previews"
FONT_CANDIDATES = [
    Path("/System/Library/Fonts/Hiragino Sans GB.ttc"),
    Path("/System/Library/Fonts/STHeiti Light.ttc"),
    Path("/System/Library/Fonts/Supplemental/Songti.ttc"),
    Path("/System/Library/Fonts/Menlo.ttc"),
]
PREVIEW_FONT_SIZE = 22
PREVIEW_LINE_HEIGHT = 34
PREVIEW_LINES_PER_IMAGE = 90
PREVIEW_PADDING_X = 28
PREVIEW_PADDING_Y = 28
PREVIEW_GUTTER = 70
PDF_MARGIN = 14 * mm
PDF_MAX_IMAGE_WIDTH = A4[0] - PDF_MARGIN * 2
PDF_MAX_IMAGE_HEIGHT = 230 * mm


IMPORT_TECH_MAP = [
    (
        "androidx.room.",
        "Room 資料庫",
        "需要 `app/build.gradle.kts` 裡的 `room-runtime`、`room-ktx` 與 `ksp(room-compiler)`，並且對應的 Entity / Dao / Database 類別要互相註冊。",
    ),
    (
        "com.google.firebase.",
        "Firebase",
        "需要 `app/build.gradle.kts` 啟用 Firebase 相依、專案套用 `google-services` plugin，並搭配 `google-services.json`、`AndroidManifest.xml` 的網路權限。",
    ),
    (
        "androidx.fragment.app.",
        "Fragment / DialogFragment",
        "通常要搭配 `MainActivity.kt` 或其他宿主容器，以及對應 `res/layout` 版面檔一起使用。",
    ),
    (
        "androidx.recyclerview.widget.",
        "RecyclerView",
        "通常需要 Adapter、資料模型與 `res/layout/item_*.xml` 一起配合，畫面端還要設定 `LayoutManager`。",
    ),
    (
        "androidx.lifecycle.",
        "Lifecycle / ViewModel",
        "需要 AndroidX lifecycle 相依，並搭配 Activity/Fragment 的生命週期使用，避免背景工作在畫面銷毀後持續執行。",
    ),
    (
        "kotlinx.coroutines.",
        "Kotlin Coroutines 協程",
        "需要 Kotlin 協程支援，並依工作類型切到 `Dispatchers.IO` 或主執行緒；常和 Repository、ViewModel、Firebase callback 互相配合。",
    ),
    (
        "androidx.credentials.",
        "Credentials API",
        "需要 Credentials 相關 dependency，並搭配 Google Sign-In 設定與 Firebase Auth 一起使用。",
    ),
    (
        "com.google.android.libraries.identity.googleid.",
        "Google Identity",
        "需要 Google Identity dependency，通常和 `CredentialManager`、`FirebaseAuthManager.kt` 及 Firebase 登入流程配合。",
    ),
    (
        "com.github.mikephil.charting.",
        "MPAndroidChart",
        "需要在 Gradle 加入圖表套件，並搭配 `AnalysisFragment.kt`、圖表 View、資料格式化器一起使用。",
    ),
    (
        "androidx.documentfile.",
        "DocumentFile / 儲存框架",
        "需要使用 Android Storage Access Framework，通常還要配合 URI 權限與使用者選擇的資料夾。",
    ),
]


ANNOTATION_EXPLANATIONS = {
    "@Dao": "用 Room 標記這是一個資料存取介面，讓編譯器自動生成查詢實作。",
    "@Database": "宣告 Room 資料庫入口，集中註冊所有 Entity 與 Dao。",
    "@Entity": "告訴 Room 這個類別要映射成資料表。",
    "@PrimaryKey": "指定主鍵欄位，確保每筆資料可被唯一識別。",
    "@ColumnInfo": "自訂欄位名稱或欄位細節，讓 Kotlin 屬性和資料庫欄位對齊。",
    "@Query": "宣告 SQL 查詢內容，讓 Room 生成對應存取程式。",
    "@Insert": "宣告新增資料操作，通常搭配衝突策略使用。",
    "@Update": "宣告更新資料操作，讓 Room 依主鍵更新既有資料。",
    "@Volatile": "確保多執行緒下讀到最新值，避免同步狀態過期。",
}


ROLE_PATTERNS = [
    ("Application()", "Application"),
    ("AppCompatActivity", "Activity"),
    ("DialogFragment", "DialogFragment"),
    ("RecyclerView.Adapter", "Adapter"),
    ("@Dao", "Dao"),
    ("@Entity", "Entity"),
    ("data class", "DataClass"),
    ("object ", "SingletonObject"),
    ("Repository", "Repository"),
    ("Manager", "Manager"),
    ("Fragment", "Fragment"),
]


FUNCTION_START_RE = re.compile(
    r"^(?P<indent>\s*)(?:(?:private|public|internal|protected|override|open|final|abstract|suspend|tailrec|operator|inline|infix|external)\s+)*fun\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*\("
)


def detect_role(text: str, file_name: str) -> str:
    for needle, role in ROLE_PATTERNS:
        if needle in text:
            return role
    if file_name.endswith("Dao.kt"):
        return "Dao"
    if file_name.endswith("Adapter.kt"):
        return "Adapter"
    if file_name.endswith("Fragment.kt"):
        return "Fragment"
    if file_name.endswith("Activity.kt"):
        return "Activity"
    return "GeneralKotlin"


def load_preview_font() -> ImageFont.FreeTypeFont:
    for candidate in FONT_CANDIDATES:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), PREVIEW_FONT_SIZE)
    return ImageFont.load_default()


def setup_pdf_fonts() -> None:
    try:
        pdfmetrics.getFont("STSong-Light")
    except KeyError:
        pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"))


def summarize_role_steps(role: str) -> list[str]:
    mapping = {
        "Application": [
            "常見固定步驟是建立 `Application` 子類別，然後到 `AndroidManifest.xml` 的 `application` 標籤用 `android:name` 註冊它。",
            "這裡通常放全 App 啟動就要做一次的初始化，例如 Firebase、Repository、全域設定，不適合塞畫面元件操作。",
        ],
        "Activity": [
            "常見固定步驟是 `onCreate()` -> `setContentView()` -> `findViewById()/bindViews()` -> 綁定事件 -> 切換 Fragment 或其他畫面。",
            "如果要跳到其他頁面，通常是建立 `Intent` 或做 `FragmentTransaction`，最後再 `startActivity()` 或 `commit()`。",
        ],
        "Fragment": [
            "常見固定步驟是 `onCreateView()` 載入 XML -> `onViewCreated()` 綁定元件/事件 -> 在合適生命週期讀資料或更新畫面。",
            "Fragment 通常不單獨存在，必須由 `Activity` 或 `FragmentManager` 載入到容器中。",
        ],
        "DialogFragment": [
            "固定寫法通常是建立 `DialogFragment`、用 `arguments` 傳資料、在 `show()` 時交給 `FragmentManager` 顯示。",
            "若有回傳結果，常搭配介面 callback、共享 ViewModel，或直接呼叫宿主 Fragment/Activity 的方法。",
        ],
        "Adapter": [
            "RecyclerView Adapter 的固定骨架通常是：準備資料清單 -> 建立 ViewHolder -> `onCreateViewHolder()` 載入 item XML -> `onBindViewHolder()` 綁資料 -> `getItemCount()` 回傳筆數。",
            "若畫面要更新，通常會改資料來源後呼叫 `notifyDataSetChanged()` 或更細的 `notifyItem...()` 系列方法。",
        ],
        "Dao": [
            "Room DAO 的固定寫法通常是：`@Dao` 介面 + `@Query/@Insert/@Update` 方法，然後到 `CocoCoinRoomDatabase.kt` 註冊、再由 Repository 呼叫。",
            "如果新增查詢方法，通常還要同步確認 Entity 欄位名稱和 Repository 包裝方法是否一致。",
        ],
        "Entity": [
            "Room Entity 的固定寫法通常是：`@Entity` 類別 + 主鍵 + 欄位宣告，之後由 Dao 查詢、Database 註冊、Repository/畫面層讀取。",
            "若欄位異動，通常要同步更新 DAO、migration、備份編解碼與雲端同步資料格式。",
        ],
        "DataClass": [
            "這類資料模型通常是先宣告欄位，再讓其他畫面、Adapter、Repository 或資料轉換流程持有它。",
            "欄位命名通常不是完全隨意，會受 JSON、Room、Firebase 或 UI 顯示需求影響。",
        ],
        "Repository": [
            "Repository 常見固定寫法是集中資料來源、包住資料庫與網路邏輯，再提供 suspend function 或 callback 給 ViewModel/Fragment 呼叫。",
            "這種寫法不是語法強制，但幾乎是 Android 分層的常見做法，目的在於把 UI 和資料存取解耦。",
        ],
        "Manager": [
            "Manager 類別通常負責封裝某一塊流程，例如登入、同步、備份；固定寫法取決於它封裝的系統 API。",
            "這種檔案常需要額外搭配 Manifest、Gradle、Firebase 或 Android 系統權限設定。",
        ],
    }
    return mapping.get(role, ["這個檔案沒有被框架強制成唯一寫法，但目前結構仍遵守 Kotlin/Android 常見分層與呼叫流程。"])


def infer_function_purpose(name: str, body_lines: list[str], role: str) -> str:
    lower_name = name.lower()
    joined = "\n".join(body_lines)
    if name == "onCreate":
        if role == "Application":
            return "這個 `onCreate()` 是整個 App 啟動時最早執行的入口之一，主要在做全域初始化。"
        return "這個 `onCreate()` 是 Android 生命週期的初始化入口，主要在建立畫面、綁定元件或準備初始資料。"
    if lower_name.startswith("bind"):
        return "這個函式的重點是把 XML / View / 資料和 Kotlin 變數綁在一起，讓後續流程能直接操作。"
    if lower_name.startswith("setup") or lower_name.startswith("init"):
        return "這個函式主要在做初始化設定，把事件、樣式或預設值一次整理好。"
    if lower_name.startswith("show") or lower_name.startswith("navigate") or lower_name.startswith("open"):
        return "這個函式主要負責導頁或切換顯示內容，讓使用者看到指定畫面。"
    if lower_name.startswith("get") or lower_name.startswith("load") or lower_name.startswith("fetch"):
        return "這個函式的職責偏向讀取資料，可能來自資料庫、記憶體、設定檔或雲端。"
    if lower_name.startswith("save") or lower_name.startswith("insert") or lower_name.startswith("update") or lower_name.startswith("delete"):
        return "這個函式主要在修改資料狀態，通常會影響資料庫、記憶體資料或同步內容。"
    if "setOnClickListener" in joined:
        return "這個函式重點在註冊互動事件，定義使用者點擊後要走哪條流程。"
    if "FragmentTransaction" in joined or ".replace(" in joined:
        return "這個函式負責切換 Fragment 或畫面容器內容，是導頁邏輯的一部分。"
    if "Firebase" in joined:
        return "這個函式和 Firebase 流程有關，通常負責登入、同步、初始化或雲端資料處理。"
    if "Dispatchers.IO" in joined or "withContext(" in joined or "launch {" in joined:
        return "這個函式包含背景執行或協程流程，目的是避免重工作卡住主執行緒。"
    return "這個函式把一段獨立職責包裝起來，讓外部流程可以更清楚地呼叫與重用。"


def infer_function_extensions(name: str, body_lines: list[str], files: list[Path]) -> list[str]:
    joined = "\n".join(body_lines)
    file_names = {path.stem for path in files}
    extensions = []
    referenced = [stem for stem in sorted(file_names) if stem != name and re.search(rf"\b{re.escape(stem)}\b", joined)]
    if referenced:
        extensions.append("這個函式會直接連動：" + "、".join(f"`{item}.kt`" for item in referenced[:6]) + "。")
    if "setOnClickListener" in joined:
        extensions.append("如果之後要改按鈕互動，通常優先回來看這個函式。")
    if "findViewById(" in joined or ".inflate(" in joined:
        extensions.append("這裡和 XML 版面高度相關；如果畫面元件 id 變了，這裡通常也要一起改。")
    if "Firebase" in joined:
        extensions.append("涉及 Firebase 的地方，除了 Kotlin 程式外，也要一起確認 Gradle、`google-services.json` 和網路權限。")
    if "@Query" in joined or "database" in joined.lower():
        extensions.append("若這裡修改資料欄位或查詢邏輯，往往也要同步檢查 Entity、Dao、Repository 與 migration。")
    if not extensions:
        extensions.append("延伸閱讀通常要搭配這個函式呼叫到的上下游流程一起看，會比只讀單一函式更完整。")
    return extensions[:3]


def collect_function_ranges(text: str, role: str, files: list[Path]) -> list[dict[str, object]]:
    lines = text.splitlines()
    brace_depth = 0
    pending_function: dict[str, object] | None = None
    active_stack: list[dict[str, object]] = []
    functions: list[dict[str, object]] = []

    for idx, line in enumerate(lines, start=1):
        match = FUNCTION_START_RE.match(line)
        if match:
            pending_function = {
                "name": match.group("name"),
                "start_line": idx,
                "indent": len(match.group("indent")),
                "start_brace_depth": brace_depth,
            }

        opens = line.count("{")
        closes = line.count("}")

        if pending_function and opens > 0:
            fn = dict(pending_function)
            fn["target_depth"] = brace_depth + opens
            active_stack.append(fn)
            pending_function = None

        brace_depth += opens
        brace_depth -= closes

        while active_stack and brace_depth < int(active_stack[-1]["target_depth"]):
            fn = active_stack.pop()
            start_line = int(fn["start_line"])
            end_line = idx
            body_lines = lines[start_line - 1 : end_line]
            functions.append(
                {
                    "name": fn["name"],
                    "start_line": start_line,
                    "end_line": end_line,
                    "summary": infer_function_purpose(str(fn["name"]), body_lines, role),
                    "extensions": infer_function_extensions(str(fn["name"]), body_lines, files),
                }
            )

    functions.sort(key=lambda item: int(item["end_line"]))
    return functions


def load_files() -> list[Path]:
    return sorted(SRC_DIR.glob("*.kt"))


def collect_identifiers(files: list[Path]) -> dict[Path, set[str]]:
    declaration_re = re.compile(
        r"\b(?:class|data\s+class|interface|object|enum\s+class|sealed\s+class)\s+([A-Z][A-Za-z0-9_]*)|\bfun\s+([a-zA-Z_][A-Za-z0-9_]*)\s*\("
    )
    identifiers: dict[Path, set[str]] = {}
    for path in files:
        names = set()
        for match in declaration_re.finditer(path.read_text(encoding="utf-8")):
            name = match.group(1) or match.group(2)
            if name:
                names.add(name)
        names.add(path.stem)
        identifiers[path] = names
    return identifiers


def collect_resource_index() -> dict[str, list[str]]:
    index: dict[str, list[str]] = defaultdict(list)
    if not RES_DIR.exists():
        return index
    for path in RES_DIR.rglob("*"):
        if path.is_file():
            index[path.stem].append(str(path.relative_to(ROOT)))
    return index


def collect_imports(text: str) -> list[str]:
    imports = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("import "):
            imports.append(stripped.split("import ", 1)[1].split("//", 1)[0].strip())
    return imports


def detect_technologies(imports: list[str], text: str) -> list[str]:
    found = []
    for prefix, label, details in IMPORT_TECH_MAP:
        if any(item.startswith(prefix) for item in imports):
            found.append(f"{label}：{details}")
    if "findViewById(" in text or "R.layout." in text:
        found.append("傳統 XML View 系統：需要對應 `res/layout/*.xml`，通常透過 `setContentView()` 或 `inflate()` 載入，再用 `findViewById()` 綁定。")
    if "SharedPreferences" in text or "getSharedPreferences(" in text:
        found.append("SharedPreferences：用來保存輕量設定，不需要額外 Manifest 權限，但要注意 key 命名與資料遷移。")
    if "Intent(" in text or "startActivity(" in text:
        found.append("Android 畫面跳轉：若是 Activity 跳轉需有 `Intent`，目標 Activity 也要在 `AndroidManifest.xml` 註冊；Fragment 切換則交由 `FragmentManager`。")
    if "FirebaseAuthManager" in text or "FirebaseSyncManager" in text:
        found.append("專案內同步/登入封裝：此檔案會依賴本專案自訂的 Firebase 管理類，不能只看單檔理解。")
    return found


def detect_related_files(current: Path, files: list[Path], identifiers: dict[Path, set[str]], resource_index: dict[str, list[str]]) -> list[str]:
    text = current.read_text(encoding="utf-8")
    related: list[str] = []
    own_names = identifiers[current]

    for other in files:
        if other == current:
            continue
        other_names = identifiers[other]
        if any(re.search(rf"\b{re.escape(name)}\b", text) for name in other_names if len(name) > 2):
            related.append(f"{other.name}：本檔直接使用了其中的類別、函式或資料模型。")
            continue
        other_text = other.read_text(encoding="utf-8")
        if any(re.search(rf"\b{re.escape(name)}\b", other_text) for name in own_names if len(name) > 2):
            related.append(f"{other.name}：其他檔案會反向引用本檔定義的型別或方法。")

    seen_resources = sorted(set(re.findall(r"R\.(?:layout|id|drawable|string|xml)\.([A-Za-z0-9_]+)", text)))
    for resource_name in seen_resources:
        for resource_path in resource_index.get(resource_name, []):
            related.append(f"{Path(resource_path).name}：本檔透過 `R` 類別使用這個資源。")

    return related[:16]


def explain_import(import_text: str) -> str:
    base = f"匯入 `{import_text}`，讓本檔可以直接使用該型別或工具，而不用每次都寫完整套件名稱。"
    for prefix, label, details in IMPORT_TECH_MAP:
        if import_text.startswith(prefix):
            return f"{base} 這也表示本行和 {label} 技術有關；{details}"
    return base


def detect_inline_relations(stripped: str, current_name: str, file_names: set[str], resource_index: dict[str, list[str]]) -> list[str]:
    relations = []
    for token in sorted(file_names):
        if token == current_name:
            continue
        if re.search(rf"\b{re.escape(token)}\b", stripped):
            relations.append(f"呼叫或引用 `{token}` 對應的 `{token}.kt`。")
    for resource_name in sorted(set(re.findall(r"R\.(?:layout|id|drawable|string|xml)\.([A-Za-z0-9_]+)", stripped))):
        paths = resource_index.get(resource_name, [])
        if paths:
            relations.append(f"配合資源 `{resource_name}`，實際檔案如 `{Path(paths[0]).name}`。")
    return relations[:3]


def explain_line(
    line: str,
    role: str,
    current_name: str,
    file_names: set[str],
    resource_index: dict[str, list[str]],
) -> str:
    stripped = line.strip()
    if not stripped:
        return "空白行，用來分隔段落，讓不同概念區塊更容易閱讀。"
    if stripped.startswith("package "):
        return "宣告 Kotlin 套件位置，讓這個檔案落在 `com.example.cococoin` 命名空間下，方便其他同專案類別引用。"
    if stripped.startswith("import "):
        return explain_import(stripped.split("import ", 1)[1].split("//", 1)[0].strip())
    if stripped.startswith("//") or stripped.startswith("/*") or stripped.startswith("*") or stripped.startswith("*/"):
        return "這是原始註解行，用來保留作者對用途、流程或背景知識的說明，方便後續維護。"
    if stripped.startswith("@"):
        for key, message in ANNOTATION_EXPLANATIONS.items():
            if stripped.startswith(key):
                return message
        return "這是一個註解（annotation），用來把額外規則交給框架或編譯器處理。"
    if re.match(r"(private|public|internal|protected)?\s*(data\s+)?class\s+\w+", stripped):
        return f"宣告類別本體，這是 `{current_name}` 的核心型別定義；這樣寫是為了把狀態與行為收在同一個可重用單位裡。"
    if re.match(r"(private|public|internal|protected)?\s*interface\s+\w+", stripped):
        return "宣告介面，目的是先定義可被實作的能力契約，讓實際實作可以交給 Room 或其他類別提供。"
    if re.match(r"(private|public|internal|protected)?\s*object\s+\w+", stripped):
        return "宣告單例物件，表示這份狀態或工具在整個 App 只需要一份共享實例。"
    if "override fun" in stripped:
        return "覆寫框架生命週期或父類別方法，因為 Android/RecyclerView/Room 會在固定時機呼叫這裡。"
    if re.match(r"(private|public|internal|protected)?\s*suspend fun\s+\w+", stripped):
        return "宣告 suspend 函式，代表這段流程可能做資料庫或網路工作，需要在協程中安全地暫停與恢復。"
    if re.match(r"(private|public|internal|protected)?\s*fun\s+\w+", stripped):
        return "宣告函式，把一段明確職責封裝起來，讓呼叫端可以重複使用並保持主流程乾淨。"
    if re.match(r"(private|public|internal|protected)?\s*(lateinit\s+)?var\s+\w+", stripped) or re.match(r"(private|public|internal|protected)?\s*val\s+\w+", stripped):
        if "findViewById" in stripped:
            return "把 XML 裡的 View 綁到 Kotlin 變數，這樣後續才能對按鈕、文字或列表元件做操作。"
        if "mutableStateOf" in stripped or "MutableStateFlow" in stripped or "MutableSharedFlow" in stripped:
            return "宣告可觀察狀態容器，讓其他地方在資料改變時能收到通知並更新 UI。"
        return "宣告屬性或區域變數，先把後面會反覆使用的資料、依賴或狀態存起來。"
    if "setContentView(" in stripped:
        return "載入 Activity 對應的 XML 畫面；沒有這步，後面的 `findViewById()` 就找不到任何元件。"
    if ".inflate(" in stripped:
        return "把 XML 版面轉成實際的 View 物件，這是 Fragment/Adapter 建立畫面的固定步驟。"
    if "findViewById(" in stripped:
        return "透過資源 ID 找到畫面元件並綁定到變數，因為後續事件處理與畫面更新都要靠這個引用。"
    if "setOnClickListener" in stripped:
        return "註冊點擊事件，讓使用者操作 UI 時能觸發對應的商業流程或頁面切換。"
    if "Intent(" in stripped:
        return "建立 Intent 物件，這是 Android Activity 跳轉與資料傳遞的標準做法。"
    if "startActivity(" in stripped:
        return "正式要求 Android 啟動畫面跳轉；只有前面先準備好 Intent，這一步才有意義。"
    if "beginTransaction()" in stripped or ".replace(" in stripped or ".commit()" in stripped:
        return "這是 FragmentTransaction 的標準鏈式寫法，用來把容器中的 Fragment 內容切換成新畫面。"
    if "withContext(" in stripped or "Dispatchers." in stripped:
        return "切換協程執行環境，目的是把 IO 工作放到背景執行，避免卡住主畫面。"
    if "launch {" in stripped or stripped.endswith(".launch {"):
        return "啟動新的協程工作，讓資料讀寫或同步流程能非同步執行。"
    if "return " in stripped:
        return "回傳這個函式需要交給呼叫端的結果，或提早結束目前流程。"
    if stripped.startswith("if ") or stripped.startswith("if(") or stripped.startswith("if ("):
        return "條件判斷，用來根據目前狀態選擇不同處理分支，避免所有情況都走同一套流程。"
    if stripped.startswith("when ") or stripped.startswith("when(") or stripped.startswith("when ("):
        return "多分支判斷，適合處理多個狀態值，比多層 `if/else` 更清楚。"
    if stripped.startswith("for ") or stripped.startswith("for(") or ".forEach" in stripped:
        return "逐一處理集合中的資料，因為這段邏輯需要對每筆項目做同樣的事情。"
    if stripped in {"{", "}"} or stripped.endswith("{") or stripped == "})" or stripped == ")" or stripped == "},":
        return "這行主要是語法結構的開關，用來界定函式、類別、lambda 或參數區塊的範圍。"
    if "=" in stripped:
        return "這行在做賦值或參數設定，把前面宣告的變數、屬性或元件配置成目前需要的值。"

    relations = detect_inline_relations(stripped, current_name, file_names, resource_index)
    if relations:
        return " ".join(relations)

    generic = {
        "Activity": "這行屬於 Activity 內部流程的一部分，通常是畫面初始化、事件綁定或導頁控制。",
        "Fragment": "這行屬於 Fragment 畫面生命週期或資料顯示流程，通常和宿主 Activity、View 以及 Repository 配合。",
        "DialogFragment": "這行屬於彈窗畫面流程，通常負責顯示資料、接收輸入或回傳結果給外層畫面。",
        "Adapter": "這行屬於列表項目的建立或綁定流程，會和 RecyclerView 及 item XML 一起運作。",
        "Dao": "這行屬於資料存取層設計的一部分，目的是把 SQL 操作封裝成可重用方法。",
        "Entity": "這行屬於資料模型欄位或資料表定義的一部分，會被 Room、同步和 UI 共用。",
        "Repository": "這行屬於資料整合流程的一部分，通常在協調本機資料、雲端同步與回呼。",
        "Manager": "這行屬於某個系統功能封裝流程的一部分，目的是隔離平台 API 細節。",
    }
    return generic.get(role, "這行是一般 Kotlin/Android 實作的一部分，作用要和前後文一起看。")


def build_intro(files: list[Path]) -> str:
    return "\n".join(
        [
            "CocoCoin Kotlin 40 檔完整整理報告",
            "====================================",
            "",
            f"產出時間：自動由 `tools/generate_kotlin_explainer.py` 生成",
            f"來源資料夾：{SRC_DIR.relative_to(ROOT)}",
            f"主程式 Kotlin 檔案數：{len(files)}（不含 `app/src/test` 與 `app/src/androidTest`）",
            "",
            "閱讀方式",
            "------------------------------------",
            "1. 每個 Kotlin 檔案都保留完整原始內容（含註解）。",
            "2. 每一行後面都會補上「為什麼這樣寫」的說明。",
            "3. 每個檔案都會整理：技術/工具、是否有固定寫法、互相搭配的其他檔案、其他補充。",
            "4. 如果涉及框架設定，會特別指出是否需要同步調整 `AndroidManifest.xml`、Gradle 或 `res/layout` 等資源檔。",
            "",
            "全域搭配檔案",
            "------------------------------------",
            f"- Manifest：{MANIFEST_PATH.relative_to(ROOT)}",
            f"- App Gradle：{APP_GRADLE_PATH.relative_to(ROOT)}",
            f"- Project Gradle：{PROJECT_GRADLE_PATH.relative_to(ROOT)}",
            f"- Settings Gradle：{SETTINGS_GRADLE_PATH.relative_to(ROOT)}",
            "",
            "備註",
            "------------------------------------",
            "這份文件的逐行說明以靜態分析為主，重點在幫助閱讀與教學；若要 100% 確認執行路徑，仍建議搭配 Android Studio 的搜尋、跳轉定義與執行除錯一起看。",
            "",
        ]
    )


def build_categorized_intro(files: list[Path]) -> str:
    return "\n".join(
        [
            "CocoCoin Kotlin 40 檔功能分類整理報告",
            "====================================",
            "",
            f"產出時間：自動由 `tools/generate_kotlin_explainer.py` 生成",
            f"來源資料夾：{SRC_DIR.relative_to(ROOT)}",
            f"主程式 Kotlin 檔案數：{len(files)}（不含 `app/src/test` 與 `app/src/androidTest`）",
            "",
            "這份檔案和完整版的差異",
            "------------------------------------",
            "1. 保留每個 Kotlin 檔案的完整內容與逐行說明。",
            "2. 重新依功能分章，方便從特定主題閱讀，例如資料庫篇、Fragment 篇、Firebase 篇、Adapter 篇。",
            "3. 不會覆蓋原本的完整版，兩份文件會並存。",
            "",
            "建議閱讀順序",
            "------------------------------------",
            "1. 先看「應用入口與畫面導航篇」，理解 App 是怎麼啟動與切頁。",
            "2. 再看「資料庫與本機資料層篇」，理解 Room / Repository / Entity / Dao 的結構。",
            "3. 最後看 Firebase、備份、圖表、設定等專題章節。",
            "",
        ]
    )


def build_file_section(path: Path, files: list[Path], identifiers: dict[Path, set[str]], resource_index: dict[str, list[str]]) -> str:
    text = path.read_text(encoding="utf-8")
    imports = collect_imports(text)
    role = detect_role(text, path.name)
    file_names = {item.stem for item in files}
    technologies = detect_technologies(imports, text)
    related_files = detect_related_files(path, files, identifiers, resource_index)
    steps = summarize_role_steps(role)

    lines = text.splitlines()
    rendered_lines = []
    for idx, line in enumerate(lines, start=1):
        explanation = explain_line(line, role, path.stem, file_names, resource_index)
        relations = detect_inline_relations(line.strip(), path.stem, file_names, resource_index)
        relation_text = f"；相關：{' '.join(relations)}" if relations else ""
        rendered_lines.append(f"L{idx:04d} | {line}")
        rendered_lines.append(f"說明  | {explanation}{relation_text}")

    tech_lines = technologies or ["此檔案主要使用 Kotlin 與 Android 基本 API，沒有額外辨識到特殊第三方技術。"]
    related_lines = related_files or ["暫時沒有透過靜態分析找到明顯的互相搭配檔案，但仍可能透過 XML、反射、資源 ID 或執行階段 callback 連動。"]

    section = [
        "",
        f"檔案：{path.relative_to(ROOT)}",
        "------------------------------------",
        f"角色定位：{role}",
        "",
        "1. 原始碼 + 逐行說明",
        "------------------------------------",
        *rendered_lines,
        "",
        "2. 使用到的技術 / 工具",
        "------------------------------------",
        *[f"- {item}" for item in tech_lines],
        "",
        "3. 這種寫法是否固定 / 常見步驟",
        "------------------------------------",
        *[f"- {item}" for item in steps],
        "",
        "4. 跟哪邊的程式碼互相搭配",
        "------------------------------------",
        *[f"- {item}" for item in related_lines],
        "",
        "5. 其他補充",
        "------------------------------------",
        f"- 檔案總行數：{len(lines)}。",
        f"- 此段整理重點以 `{path.name}` 為主；如果它是 Room / Firebase / Fragment 相關檔案，建議再連同上面的搭配檔案一起讀，理解會更完整。",
    ]
    return "\n".join(section)


def classify_line(line: str) -> str:
    stripped = line.strip()
    if not stripped:
        return "空白"
    if stripped.startswith("package "):
        return "套件"
    if stripped.startswith("import "):
        return "匯入"
    if stripped.startswith("//") or stripped.startswith("/*") or stripped.startswith("*") or stripped.startswith("*/"):
        return "原註解"
    if stripped.startswith("@"):
        return "註解標記"
    return "程式碼"


def build_pretty_intro(title: str, files: list[Path], categorized: bool) -> str:
    lines = [
        title,
        "============================================================",
        "",
        "閱讀圖例",
        "------------------------------------------------------------",
        "📄 `檔案資訊`：目前在看哪個 Kotlin 檔。",
        "🧩 `角色定位`：這個檔案在專案中的主要職責。",
        "🏷️ `行別`：幫你快速看出這一行是程式碼、作者原註解、匯入或 annotation。",
        "💡 `逐行說明`：補充這一行為什麼這樣寫。",
        "🔗 `相關搭配`：指出和其他檔案、資源、設定的連動。",
        "",
        f"主程式 Kotlin 檔案數：{len(files)}",
    ]
    if categorized:
        lines.extend(
            [
                "",
                "這份是功能分類版：會先依主題分章，再列出各檔案的完整內容與逐行說明。",
            ]
        )
    else:
        lines.extend(
            [
                "",
                "這份是完整依檔案順序版：適合從頭到尾逐檔閱讀。",
            ]
        )
    return "\n".join(lines) + "\n"


def build_pretty_file_section(
    path: Path,
    files: list[Path],
    identifiers: dict[Path, set[str]],
    resource_index: dict[str, list[str]],
) -> str:
    text = path.read_text(encoding="utf-8")
    imports = collect_imports(text)
    role = detect_role(text, path.name)
    file_names = {item.stem for item in files}
    technologies = detect_technologies(imports, text)
    related_files = detect_related_files(path, files, identifiers, resource_index)
    steps = summarize_role_steps(role)

    rendered_lines = []
    for idx, line in enumerate(text.splitlines(), start=1):
        line_kind = classify_line(line)
        explanation = explain_line(line, role, path.stem, file_names, resource_index)
        relations = detect_inline_relations(line.strip(), path.stem, file_names, resource_index)
        rendered_lines.extend(
            [
                f"【L{idx:04d}｜{line_kind}】",
                f"原文：{line if line else '（空白行）'}",
                f"💡 逐行說明：{explanation}",
                f"🔗 相關搭配：{'；'.join(relations) if relations else '此行未直接辨識到明顯的跨檔案引用。'}",
                "",
            ]
        )

    tech_lines = technologies or ["此檔案主要使用 Kotlin 與 Android 基本 API，沒有額外辨識到特殊第三方技術。"]
    related_lines = related_files or ["暫時沒有透過靜態分析找到明顯的互相搭配檔案，但仍可能透過 XML、反射、資源 ID 或執行階段 callback 連動。"]
    line_count = len(text.splitlines())

    section = [
        "",
        "╔════════════════════════════════════════════════════════════╗",
        f"║ 檔案資訊：{path.name}",
        "╠════════════════════════════════════════════════════════════╣",
        f"║ 路徑：{path.relative_to(ROOT)}",
        f"║ 角色定位：{role}",
        f"║ 總行數：{line_count}",
        "╚════════════════════════════════════════════════════════════╝",
        "",
        "🧠 使用到的技術 / 工具",
        "------------------------------------------------------------",
        *[f"- {item}" for item in tech_lines],
        "",
        "🪜 這種寫法是否固定 / 常見步驟",
        "------------------------------------------------------------",
        *[f"- {item}" for item in steps],
        "",
        "🤝 跟哪邊的程式碼互相搭配",
        "------------------------------------------------------------",
        *[f"- {item}" for item in related_lines],
        "",
        "📝 原始碼逐行閱讀",
        "------------------------------------------------------------",
        *rendered_lines,
        "📌 其他補充",
        "------------------------------------------------------------",
        f"- 這一節保留了原始程式碼與原註解，並額外把每一行標示成「程式碼 / 原註解 / 匯入 / annotation / 空白」。",
        f"- 如果你讀到這個檔案的某段流程還是卡住，建議優先再一起對照上面列出的搭配檔案。",
    ]
    return "\n".join(section)


def ensure_preview_dir() -> None:
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)


def wrap_code_line(line: str, width: int) -> list[str]:
    if not line:
        return [""]
    chunks = []
    current = line
    while len(current) > width:
        chunks.append(current[:width])
        current = current[width:]
    chunks.append(current)
    return chunks


def render_code_preview_images(path: Path) -> list[Path]:
    ensure_preview_dir()
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    font = load_preview_font()
    usable_width_chars = 105
    segments: list[list[tuple[int, str, bool]]] = []
    current_segment: list[tuple[int, str, bool]] = []
    visual_lines_count = 0

    for idx, raw_line in enumerate(lines, start=1):
        wrapped = wrap_code_line(raw_line, usable_width_chars)
        for part_idx, part in enumerate(wrapped):
            current_segment.append((idx, part, part_idx == 0))
            visual_lines_count += 1
            if visual_lines_count >= PREVIEW_LINES_PER_IMAGE:
                segments.append(current_segment)
                current_segment = []
                visual_lines_count = 0
    if current_segment:
        segments.append(current_segment)

    image_paths = []
    for seg_idx, segment in enumerate(segments, start=1):
        max_code_width = max((int(font.getlength(part)) for _, part, _ in segment), default=0)
        width = PREVIEW_PADDING_X * 2 + PREVIEW_GUTTER + max_code_width + 42
        top_bar_height = 54
        height = PREVIEW_PADDING_Y * 2 + len(segment) * PREVIEW_LINE_HEIGHT + top_bar_height + 18
        image = Image.new("RGB", (width, height), "#1e1e1e")
        draw = ImageDraw.Draw(image)

        draw.rounded_rectangle((0, 0, width - 1, height - 1), radius=16, outline="#313131", width=2, fill="#1e1e1e")
        draw.rounded_rectangle((0, 0, width - 1, top_bar_height), radius=16, fill="#252526")
        draw.rectangle((0, top_bar_height - 16, width - 1, top_bar_height), fill="#252526")
        for offset, color in enumerate(["#ff5f56", "#ffbd2e", "#27c93f"]):
            cx = PREVIEW_PADDING_X + offset * 24
            draw.ellipse((cx, 16, cx + 12, 28), fill=color)

        tab_x = PREVIEW_PADDING_X + 96
        tab_y = 10
        tab_w = min(width - tab_x - 24, 520)
        draw.rounded_rectangle((tab_x, tab_y, tab_x + tab_w, tab_y + 30), radius=8, fill="#333337", outline="#3f3f46")
        title = f"{path.name}    {seg_idx}/{len(segments)}"
        draw.text((tab_x + 14, tab_y + 4), title, font=font, fill="#cccccc")

        gutter_right = PREVIEW_PADDING_X + PREVIEW_GUTTER - 8
        draw.rectangle((0, top_bar_height, gutter_right + 12, height), fill="#181818")
        draw.line((gutter_right + 12, top_bar_height + 4, gutter_right + 12, height - 12), fill="#2d2d30", width=2)

        y = PREVIEW_PADDING_Y + top_bar_height + 6
        for line_no, part, is_first in segment:
            number_text = str(line_no) if is_first else "↳"
            stripped = part.strip()
            line_kind = classify_line(part)
            if line_kind == "原註解":
                code_color = "#6A9955"
            elif line_kind in {"套件", "匯入"}:
                code_color = "#C586C0"
            elif line_kind == "註解標記":
                code_color = "#DCDCAA"
            elif " fun " in f" {part} " or stripped.startswith("fun ") or "override fun" in stripped:
                code_color = "#DCDCAA"
            elif stripped.startswith("class ") or stripped.startswith("data class ") or stripped.startswith("interface "):
                code_color = "#4EC9B0"
            else:
                code_color = "#D4D4D4"

            if stripped.startswith("}") or stripped == "{":
                draw.rectangle((PREVIEW_PADDING_X + PREVIEW_GUTTER - 6, y + 6, width - 24, y + PREVIEW_LINE_HEIGHT - 6), fill="#232323")

            draw.text((PREVIEW_PADDING_X - 4, y), number_text.rjust(4), font=font, fill="#858585")
            draw.text((PREVIEW_PADDING_X + PREVIEW_GUTTER + 6, y), part or " ", font=font, fill=code_color)
            y += PREVIEW_LINE_HEIGHT

        output = PREVIEW_DIR / f"{path.stem}_part{seg_idx:02d}.png"
        image.save(output)
        image_paths.append(output)
    return image_paths


def build_function_summary_block(function_info: dict[str, object]) -> list[str]:
    return [
        "",
        "┌──────────────────────────────────────────────┐",
        f"│ 函式總整理：`{function_info['name']}`",
        "├──────────────────────────────────────────────┤",
        f"│ 作用：{function_info['summary']}",
        "│ 延伸補充：",
        *[f"│ - {item}" for item in function_info["extensions"]],
        "└──────────────────────────────────────────────┘",
        "",
    ]


def build_visual_file_section(
    path: Path,
    files: list[Path],
    identifiers: dict[Path, set[str]],
    resource_index: dict[str, list[str]],
) -> str:
    text = path.read_text(encoding="utf-8")
    imports = collect_imports(text)
    role = detect_role(text, path.name)
    file_names = {item.stem for item in files}
    technologies = detect_technologies(imports, text)
    related_files = detect_related_files(path, files, identifiers, resource_index)
    steps = summarize_role_steps(role)
    previews = render_code_preview_images(path)
    functions = collect_function_ranges(text, role, files)
    functions_by_end = {int(item["end_line"]): item for item in functions}

    lines = [
        f"# {path.name}",
        "",
        f"路徑：`{path.relative_to(ROOT)}`  ",
        f"角色定位：`{role}`  ",
        f"總行數：`{len(text.splitlines())}`",
        "",
        "## 程式碼截圖總覽",
        "",
    ]
    for preview in previews:
        lines.append(f"![{preview.name}]({preview.resolve()})")
        lines.append("")

    lines.extend(
        [
            "## 檔案重點",
            "",
            "### 使用到的技術 / 工具",
            "",
            *[f"- {item}" for item in (technologies or ["此檔案主要使用 Kotlin 與 Android 基本 API。"])],
            "",
            "### 常見步驟 / 固定寫法",
            "",
            *[f"- {item}" for item in steps],
            "",
            "### 搭配檔案",
            "",
            *[f"- {item}" for item in (related_files or ["暫時沒有透過靜態分析找到明顯的互相搭配檔案。"])],
            "",
            "## 原始碼逐行說明",
            "",
        ]
    )

    for idx, line in enumerate(text.splitlines(), start=1):
        line_kind = classify_line(line)
        explanation = explain_line(line, role, path.stem, file_names, resource_index)
        relations = detect_inline_relations(line.strip(), path.stem, file_names, resource_index)
        lines.extend(
            [
                f"### L{idx:04d}｜{line_kind}",
                "",
                "```kotlin",
                line if line else " ",
                "```",
                f"💡 說明：{explanation}",
                f"🔗 搭配：{'；'.join(relations) if relations else '此行未直接辨識到明顯的跨檔案引用。'}",
                "",
            ]
        )
        if idx in functions_by_end:
            lines.extend(build_function_summary_block(functions_by_end[idx]))

    lines.extend(
        [
            "## 其他補充",
            "",
            "- 這份是 Markdown 視覺版，目的就是讓你先看得到整個檔案外觀，再往下逐行讀。",
            "- 若函式很多，總整理區塊會插在每個函式結束後，方便你在讀完一大段後先停下來消化。",
            "",
        ]
    )
    return "\n".join(lines)


def build_visual_document(
    files: list[Path],
    identifiers: dict[Path, set[str]],
    resource_index: dict[str, list[str]],
    categorized: bool,
) -> str:
    title = "CocoCoin Kotlin 40 檔功能分類整理報告（圖片版）" if categorized else "CocoCoin Kotlin 40 檔完整整理報告（圖片版）"
    sections = [
        f"# {title}",
        "",
        "這份版本新增了兩種東西：",
        "",
        "- 每個 Kotlin 檔案前面都有程式碼截圖，含行號。",
        "- 每個 function 結束後都會插入一個「函式總整理」區塊。",
        "",
    ]

    if categorized:
        grouped: dict[str, list[Path]] = defaultdict(list)
        category_order = [
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
        for path in files:
            text = path.read_text(encoding="utf-8")
            imports = collect_imports(text)
            role = detect_role(text, path.name)
            grouped[categorize_file(path, role, imports, text)].append(path)
        for category in category_order:
            paths = grouped.get(category)
            if not paths:
                continue
            sections.extend(
                [
                    "",
                    f"---\n## {category}",
                    "",
                    "本章索引：" + "、".join(f"`{path.name}`" for path in paths),
                    "",
                ]
            )
            for path in paths:
                sections.extend(["---", "", build_visual_file_section(path, files, identifiers, resource_index), ""])
    else:
        for path in files:
            sections.extend(["---", "", build_visual_file_section(path, files, identifiers, resource_index), ""])

    return "\n".join(sections)


def build_function_summary_html(function_info: dict[str, object]) -> str:
    items = "".join(f"<li>{html.escape(str(item))}</li>" for item in function_info["extensions"])
    return f"""
    <div class="function-summary">
      <div class="function-summary-title">函式總整理：<code>{html.escape(str(function_info['name']))}</code></div>
      <div class="function-summary-body">
        <p><strong>作用：</strong>{html.escape(str(function_info['summary']))}</p>
        <p><strong>延伸補充：</strong></p>
        <ul>{items}</ul>
      </div>
    </div>
    """


def build_visual_file_section_html(
    path: Path,
    files: list[Path],
    identifiers: dict[Path, set[str]],
    resource_index: dict[str, list[str]],
) -> str:
    text = path.read_text(encoding="utf-8")
    imports = collect_imports(text)
    role = detect_role(text, path.name)
    file_names = {item.stem for item in files}
    technologies = detect_technologies(imports, text)
    related_files = detect_related_files(path, files, identifiers, resource_index)
    steps = summarize_role_steps(role)
    previews = render_code_preview_images(path)
    functions = collect_function_ranges(text, role, files)
    functions_by_end = {int(item["end_line"]): item for item in functions}

    preview_html = "".join(
        f'<div class="preview-wrap"><img class="code-preview" src="{html.escape(preview.parent.name + "/" + preview.name)}" alt="{html.escape(preview.name)}"></div>'
        for preview in previews
    )
    tech_html = "".join(f"<li>{html.escape(item)}</li>" for item in (technologies or ["此檔案主要使用 Kotlin 與 Android 基本 API。"]))
    steps_html = "".join(f"<li>{html.escape(item)}</li>" for item in steps)
    related_html = "".join(
        f"<li>{html.escape(item)}</li>" for item in (related_files or ["暫時沒有透過靜態分析找到明顯的互相搭配檔案。"])
    )

    line_blocks: list[str] = []
    for idx, line in enumerate(text.splitlines(), start=1):
        line_kind = classify_line(line)
        explanation = explain_line(line, role, path.stem, file_names, resource_index)
        relations = detect_inline_relations(line.strip(), path.stem, file_names, resource_index)
        line_blocks.append(
            f"""
            <div class="line-block">
              <div class="line-head">L{idx:04d}｜{html.escape(line_kind)}</div>
              <pre class="code-line"><code>{html.escape(line if line else ' ')}</code></pre>
              <div class="line-meta"><strong>說明：</strong>{html.escape(explanation)}</div>
              <div class="line-meta"><strong>搭配：</strong>{html.escape('；'.join(relations) if relations else '此行未直接辨識到明顯的跨檔案引用。')}</div>
            </div>
            """
        )
        if idx in functions_by_end:
            line_blocks.append(build_function_summary_html(functions_by_end[idx]))

    return f"""
    <section class="file-section">
      <h1>{html.escape(path.name)}</h1>
      <div class="file-meta">
        <div><strong>路徑：</strong><code>{html.escape(str(path.relative_to(ROOT)))}</code></div>
        <div><strong>角色定位：</strong><code>{html.escape(role)}</code></div>
        <div><strong>總行數：</strong><code>{len(text.splitlines())}</code></div>
      </div>

      <h2>程式碼截圖總覽</h2>
      {preview_html}

      <h2>檔案重點</h2>
      <h3>使用到的技術 / 工具</h3>
      <ul>{tech_html}</ul>
      <h3>常見步驟 / 固定寫法</h3>
      <ul>{steps_html}</ul>
      <h3>搭配檔案</h3>
      <ul>{related_html}</ul>

      <h2>原始碼逐行說明</h2>
      {''.join(line_blocks)}
    </section>
    """


def build_visual_html_document(
    files: list[Path],
    identifiers: dict[Path, set[str]],
    resource_index: dict[str, list[str]],
    categorized: bool,
) -> str:
    title = "CocoCoin Kotlin 40 檔功能分類整理報告（圖片版 HTML）" if categorized else "CocoCoin Kotlin 40 檔完整整理報告（圖片版 HTML）"
    body_sections: list[str] = [
        f"<header><h1>{html.escape(title)}</h1><p>這份版本直接把程式碼截圖嵌進 HTML，所以打開檔案就能看到圖片，不需要 Markdown 預覽。</p></header>"
    ]

    if categorized:
        grouped: dict[str, list[Path]] = defaultdict(list)
        category_order = [
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
        for path in files:
            text = path.read_text(encoding="utf-8")
            imports = collect_imports(text)
            role = detect_role(text, path.name)
            grouped[categorize_file(path, role, imports, text)].append(path)
        for category in category_order:
            paths = grouped.get(category)
            if not paths:
                continue
            body_sections.append(
                f"<section class='category'><h1>{html.escape(category)}</h1><p>本章索引：{'、'.join(html.escape(path.name) for path in paths)}</p></section>"
            )
            for path in paths:
                body_sections.append(build_visual_file_section_html(path, files, identifiers, resource_index))
    else:
        for path in files:
            body_sections.append(build_visual_file_section_html(path, files, identifiers, resource_index))

    return f"""<!DOCTYPE html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{html.escape(title)}</title>
  <style>
    :root {{
      --bg: #f6f1e8;
      --paper: #fffdf8;
      --ink: #2f2a24;
      --muted: #756a5f;
      --accent: #8c5a3c;
      --line: #e7ddd0;
      --code-bg: #1e1f22;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      font-family: "PingFang TC", "Hiragino Sans GB", "Noto Sans CJK TC", sans-serif;
      background: linear-gradient(180deg, #efe5d6 0%, #f7f3eb 160px, #f6f1e8 100%);
      color: var(--ink);
      line-height: 1.7;
    }}
    main {{
      max-width: 1100px;
      margin: 0 auto;
      padding: 32px 20px 80px;
    }}
    header, .category, .file-section {{
      background: var(--paper);
      border: 1px solid var(--line);
      border-radius: 18px;
      padding: 24px;
      margin-bottom: 28px;
      box-shadow: 0 10px 30px rgba(95, 72, 52, 0.08);
    }}
    h1, h2, h3 {{ color: #4c3528; }}
    code, pre {{ font-family: Menlo, Monaco, "SF Mono", monospace; }}
    .file-meta {{
      display: grid;
      gap: 6px;
      padding: 14px 16px;
      background: #f9f4ec;
      border-radius: 12px;
      border: 1px solid var(--line);
    }}
    .preview-wrap {{
      margin: 16px 0 24px;
      overflow-x: auto;
      border-radius: 16px;
      border: 1px solid #383a40;
      background: var(--code-bg);
      box-shadow: 0 12px 24px rgba(0,0,0,0.14);
    }}
    .code-preview {{
      display: block;
      max-width: 100%;
      height: auto;
    }}
    .line-block {{
      border-top: 1px dashed var(--line);
      padding: 18px 0;
    }}
    .line-head {{
      font-weight: 700;
      color: var(--accent);
      margin-bottom: 10px;
    }}
    .code-line {{
      margin: 0 0 10px;
      padding: 14px 16px;
      background: #22242a;
      color: #ece7df;
      border-radius: 12px;
      overflow-x: auto;
    }}
    .line-meta {{
      margin: 6px 0;
    }}
    .function-summary {{
      margin: 18px 0 6px;
      border: 1px solid #dfd2c3;
      border-radius: 14px;
      background: #fcf7ef;
      overflow: hidden;
    }}
    .function-summary-title {{
      padding: 12px 16px;
      background: #f2e6d8;
      font-weight: 700;
      color: #6f472f;
    }}
    .function-summary-body {{
      padding: 14px 16px;
    }}
    ul {{ padding-left: 22px; }}
  </style>
</head>
<body>
  <main>
    {''.join(body_sections)}
  </main>
</body>
</html>
"""


def make_pdf_styles() -> dict[str, ParagraphStyle]:
    setup_pdf_fonts()
    base = getSampleStyleSheet()
    return {
        "title": ParagraphStyle(
            "CocoTitle",
            parent=base["Title"],
            fontName="STSong-Light",
            fontSize=22,
            leading=28,
            textColor=colors.HexColor("#4c3528"),
            alignment=TA_CENTER,
            spaceAfter=14,
        ),
        "h1": ParagraphStyle(
            "CocoH1",
            parent=base["Heading1"],
            fontName="STSong-Light",
            fontSize=18,
            leading=24,
            textColor=colors.HexColor("#5a3928"),
            spaceBefore=10,
            spaceAfter=10,
        ),
        "h2": ParagraphStyle(
            "CocoH2",
            parent=base["Heading2"],
            fontName="STSong-Light",
            fontSize=14,
            leading=20,
            textColor=colors.HexColor("#6f472f"),
            spaceBefore=8,
            spaceAfter=8,
        ),
        "body": ParagraphStyle(
            "CocoBody",
            parent=base["BodyText"],
            fontName="STSong-Light",
            fontSize=9.2,
            leading=14,
            textColor=colors.HexColor("#2f2a24"),
            spaceAfter=4,
        ),
        "bullet": ParagraphStyle(
            "CocoBullet",
            parent=base["BodyText"],
            fontName="STSong-Light",
            fontSize=9.2,
            leading=14,
            leftIndent=12,
            bulletIndent=0,
            spaceAfter=3,
        ),
        "code": ParagraphStyle(
            "CocoCode",
            parent=base["Code"],
            fontName="STSong-Light",
            fontSize=7.2,
            leading=9,
            backColor=colors.HexColor("#F7F3EC"),
            borderPadding=6,
        ),
        "summary": ParagraphStyle(
            "CocoSummary",
            parent=base["BodyText"],
            fontName="STSong-Light",
            fontSize=8.8,
            leading=13,
            textColor=colors.HexColor("#473327"),
            leftIndent=8,
            spaceAfter=3,
        ),
    }


def add_pdf_bullets(story: list[object], title: str, items: list[str], styles: dict[str, ParagraphStyle]) -> None:
    story.append(Paragraph(title, styles["h2"]))
    for item in items:
        story.append(Paragraph(html.escape(item), styles["bullet"], bulletText="•"))
    story.append(Spacer(1, 4))


def scaled_pdf_image(path: Path) -> RLImage:
    image = RLImage(str(path))
    width = image.drawWidth
    height = image.drawHeight
    ratio = min(PDF_MAX_IMAGE_WIDTH / width, PDF_MAX_IMAGE_HEIGHT / height, 1.0)
    image.drawWidth = width * ratio
    image.drawHeight = height * ratio
    return image


def build_visual_pdf_story(
    files: list[Path],
    identifiers: dict[Path, set[str]],
    resource_index: dict[str, list[str]],
    categorized: bool,
) -> list[object]:
    styles = make_pdf_styles()
    title = "CocoCoin Kotlin 40 檔功能分類整理報告（PDF）" if categorized else "CocoCoin Kotlin 40 檔完整整理報告（PDF）"
    story: list[object] = [
        Paragraph(title, styles["title"]),
        Paragraph("這份 PDF 版本可直接分享，並保留程式碼截圖、逐行說明與每個函式的總整理。", styles["body"]),
        Spacer(1, 8),
    ]

    if categorized:
        grouped: dict[str, list[Path]] = defaultdict(list)
        category_order = [
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
        for path in files:
            text = path.read_text(encoding="utf-8")
            imports = collect_imports(text)
            role = detect_role(text, path.name)
            grouped[categorize_file(path, role, imports, text)].append(path)
        grouped_items = [(category, grouped[category]) for category in category_order if grouped.get(category)]
    else:
        grouped_items = [("全部檔案", files)]

    first_file = True
    for category, paths in grouped_items:
        if categorized:
            if not first_file:
                story.append(PageBreak())
            story.append(Paragraph(category, styles["h1"]))
            story.append(Paragraph("本章索引：" + "、".join(path.name for path in paths), styles["body"]))
            story.append(Spacer(1, 6))

        for path in paths:
            if not first_file:
                story.append(PageBreak())
            first_file = False
            text = path.read_text(encoding="utf-8")
            imports = collect_imports(text)
            role = detect_role(text, path.name)
            technologies = detect_technologies(imports, text) or ["此檔案主要使用 Kotlin 與 Android 基本 API。"]
            related_files = detect_related_files(path, files, identifiers, resource_index) or ["暫時沒有透過靜態分析找到明顯的互相搭配檔案。"]
            steps = summarize_role_steps(role)
            previews = render_code_preview_images(path)
            functions = collect_function_ranges(text, role, files)

            story.append(Paragraph(path.name, styles["h1"]))
            story.append(Paragraph(f"路徑：{html.escape(str(path.relative_to(ROOT)))}", styles["body"]))
            story.append(Paragraph(f"角色定位：{role}｜總行數：{len(text.splitlines())}", styles["body"]))
            story.append(Spacer(1, 6))

            story.append(Paragraph("程式碼截圖總覽", styles["h2"]))
            for preview in previews:
                story.append(scaled_pdf_image(preview))
                story.append(Spacer(1, 8))

            add_pdf_bullets(story, "使用到的技術 / 工具", technologies, styles)
            add_pdf_bullets(story, "常見步驟 / 固定寫法", steps, styles)
            add_pdf_bullets(story, "搭配檔案", related_files, styles)
            story.append(Paragraph("函式總整理", styles["h2"]))
            if functions:
                for fn in functions:
                    story.append(Paragraph(f"{fn['name']}（L{int(fn['start_line']):04d}-L{int(fn['end_line']):04d}）", styles["body"]))
                    story.append(Paragraph(f"作用：{html.escape(str(fn['summary']))}", styles["summary"]))
                    for ext in fn["extensions"]:
                        story.append(Paragraph(html.escape(str(ext)), styles["bullet"], bulletText="•"))
                    story.append(Spacer(1, 4))
            else:
                story.append(Paragraph("這個檔案沒有辨識到一般函式區塊，通常代表它是資料模型、常數容器或介面宣告。", styles["body"]))

            story.append(Paragraph("補充", styles["h2"]))
            story.append(Paragraph("PDF 版保留可直接分享的精華結構：截圖、檔案重點、函式總整理。完整逐行說明請對照同名的 HTML 視覺版。", styles["body"]))

    return story


def build_visual_pdf_documents(
    files: list[Path],
    identifiers: dict[Path, set[str]],
    resource_index: dict[str, list[str]],
) -> None:
    for output, categorized in [
        (VISUAL_PDF_OUTPUT_PATH, False),
        (VISUAL_CATEGORIZED_PDF_OUTPUT_PATH, True),
    ]:
        doc = SimpleDocTemplate(
            str(output),
            pagesize=A4,
            leftMargin=PDF_MARGIN,
            rightMargin=PDF_MARGIN,
            topMargin=PDF_MARGIN,
            bottomMargin=PDF_MARGIN,
            title=output.stem,
            author="OpenAI Codex",
        )
        doc.build(build_visual_pdf_story(files, identifiers, resource_index, categorized=categorized))


def build_pretty_document(
    files: list[Path],
    identifiers: dict[Path, set[str]],
    resource_index: dict[str, list[str]],
) -> str:
    sections = [build_pretty_intro("CocoCoin Kotlin 40 檔完整整理報告（美化版）", files, categorized=False)]
    for path in files:
        sections.append(build_pretty_file_section(path, files, identifiers, resource_index))
    return "\n".join(sections) + "\n"


def categorize_file(path: Path, role: str, imports: list[str], text: str) -> str:
    name = path.name
    if name in {"MainActivity.kt", "CocoCoinApp.kt"}:
        return "應用入口與畫面導航篇"
    if any(item.startswith("com.google.firebase.") for item in imports) or "Firebase" in name:
        return "Firebase 與登入同步篇"
    if "Backup" in name or "Snapshot" in name or "SyncStatus" in name:
        return "備份、快照與同步狀態篇"
    if role in {"Dao", "Entity", "Repository"} or "Room" in name or "Database" in name or "TransactionCategoryStore" in name:
        return "資料庫與本機資料層篇"
    if role in {"Fragment", "DialogFragment"}:
        return "Fragment 與互動畫面篇"
    if role == "Adapter" or "RecyclerView.Adapter" in text:
        return "Adapter 與清單顯示篇"
    if "Chart" in text or "Analysis" in name:
        return "分析圖表與統計篇"
    if "Auth" in name or any(item.startswith("androidx.credentials.") for item in imports):
        return "Firebase 與登入同步篇"
    if name in {"OperationResult.kt", "AccountVisualStyle.kt", "CategoryDetailItem.kt", "DailyDetailItem.kt", "TransactionCategoryDefinition.kt", "AuthLinkStatus.kt", "AutoLocalBackupStatus.kt"}:
        return "共用模型與工具型別篇"
    return "其他共用支援篇"


def build_categorized_document(
    files: list[Path],
    identifiers: dict[Path, set[str]],
    resource_index: dict[str, list[str]],
) -> str:
    grouped: dict[str, list[Path]] = defaultdict(list)
    category_order = [
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

    file_meta: dict[Path, tuple[str, list[str], str]] = {}
    for path in files:
        text = path.read_text(encoding="utf-8")
        imports = collect_imports(text)
        role = detect_role(text, path.name)
        file_meta[path] = (role, imports, text)
        grouped[categorize_file(path, role, imports, text)].append(path)

    sections = [build_categorized_intro(files)]
    for category in category_order:
        paths = grouped.get(category)
        if not paths:
            continue
        sections.extend(
            [
                "",
                category,
                "------------------------------------",
                f"本章檔案數：{len(paths)}",
                "本章索引：" + "、".join(path.name for path in paths),
            ]
        )
        for path in paths:
            sections.append(build_file_section(path, files, identifiers, resource_index))
    return "\n".join(sections) + "\n"


def build_pretty_categorized_document(
    files: list[Path],
    identifiers: dict[Path, set[str]],
    resource_index: dict[str, list[str]],
) -> str:
    grouped: dict[str, list[Path]] = defaultdict(list)
    category_order = [
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

    for path in files:
        text = path.read_text(encoding="utf-8")
        imports = collect_imports(text)
        role = detect_role(text, path.name)
        grouped[categorize_file(path, role, imports, text)].append(path)

    sections = [build_pretty_intro("CocoCoin Kotlin 40 檔功能分類整理報告（美化版）", files, categorized=True)]
    for category in category_order:
        paths = grouped.get(category)
        if not paths:
            continue
        sections.extend(
            [
                "",
                "▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓",
                f"📚 {category}",
                f"檔案數：{len(paths)}",
                "本章索引：" + "、".join(path.name for path in paths),
                "▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓",
                "",
            ]
        )
        for path in paths:
            sections.append(build_pretty_file_section(path, files, identifiers, resource_index))
    return "\n".join(sections) + "\n"


def main() -> None:
    files = load_files()
    identifiers = collect_identifiers(files)
    resource_index = collect_resource_index()

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)

    sections = [build_intro(files)]
    for path in files:
        sections.append(build_file_section(path, files, identifiers, resource_index))

    OUTPUT_PATH.write_text("\n".join(sections) + "\n", encoding="utf-8")
    CATEGORIZED_OUTPUT_PATH.write_text(
        build_categorized_document(files, identifiers, resource_index),
        encoding="utf-8",
    )
    PRETTY_OUTPUT_PATH.write_text(
        build_pretty_document(files, identifiers, resource_index),
        encoding="utf-8",
    )
    PRETTY_CATEGORIZED_OUTPUT_PATH.write_text(
        build_pretty_categorized_document(files, identifiers, resource_index),
        encoding="utf-8",
    )
    VISUAL_OUTPUT_PATH.write_text(
        build_visual_document(files, identifiers, resource_index, categorized=False),
        encoding="utf-8",
    )
    VISUAL_CATEGORIZED_OUTPUT_PATH.write_text(
        build_visual_document(files, identifiers, resource_index, categorized=True),
        encoding="utf-8",
    )
    VISUAL_HTML_OUTPUT_PATH.write_text(
        build_visual_html_document(files, identifiers, resource_index, categorized=False),
        encoding="utf-8",
    )
    VISUAL_CATEGORIZED_HTML_OUTPUT_PATH.write_text(
        build_visual_html_document(files, identifiers, resource_index, categorized=True),
        encoding="utf-8",
    )
    build_visual_pdf_documents(files, identifiers, resource_index)
    print(f"Wrote {OUTPUT_PATH}")
    print(f"Wrote {CATEGORIZED_OUTPUT_PATH}")
    print(f"Wrote {PRETTY_OUTPUT_PATH}")
    print(f"Wrote {PRETTY_CATEGORIZED_OUTPUT_PATH}")
    print(f"Wrote {VISUAL_OUTPUT_PATH}")
    print(f"Wrote {VISUAL_CATEGORIZED_OUTPUT_PATH}")
    print(f"Wrote {VISUAL_HTML_OUTPUT_PATH}")
    print(f"Wrote {VISUAL_CATEGORIZED_HTML_OUTPUT_PATH}")
    print(f"Wrote {VISUAL_PDF_OUTPUT_PATH}")
    print(f"Wrote {VISUAL_CATEGORIZED_PDF_OUTPUT_PATH}")


if __name__ == "__main__":
    main()
