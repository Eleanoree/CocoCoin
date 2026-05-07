import fs from "node:fs/promises";
import path from "node:path";
import {
  Presentation,
  PresentationFile,
  row,
  column,
  grid,
  layers,
  panel,
  text,
  image,
  shape,
  rule,
  fill,
  hug,
  fixed,
  wrap,
  fr,
  auto,
} from "@oai/artifact-tool";

const VARIANT = process.argv.includes("--variant=windows-safe") ? "windows-safe" : "default";
const IS_WINDOWS_SAFE = VARIANT === "windows-safe";

const ROOT = "/Users/user/AndroidStudioProjects/CocoCoin/topic-arch-v5-redesign";
const OUTPUT_DIR = path.join(ROOT, "output");
const SCRATCH_DIR = path.join(ROOT, "scratch");
const PREVIEW_DIR = path.join(SCRATCH_DIR, IS_WINDOWS_SAFE ? "previews-windows-safe" : "previews");
const MEDIA_DIR = path.join(SCRATCH_DIR, "original-media");

const W = 1920;
const H = 1080;

const C = {
  bg: "#101820",
  panel: "#17232C",
  panelAlt: "#213240",
  cream: "#F5E9D7",
  sand: "#D9C3A8",
  mint: "#8FE3CF",
  cyan: "#62D6FF",
  coral: "#F28C6F",
  gold: "#F0B56A",
  red: "#F26D6D",
  green: "#8AD38F",
  line: "#38505F",
  dim: "#9EB2BC",
};

const STYLE = IS_WINDOWS_SAFE
  ? {
      headlineFont: "Arial",
      bodyFont: "Calibri",
      monoFont: "Arial",
      titleSize: 44,
      bigTitleSize: 64,
      bodySize: 22,
      smallSize: 15,
      labelSize: 16,
      metricValueSize: 36,
      panelTitleSize: 28,
      subtitleSize: 24,
      coverTitleSize: 84,
      coverTaglineSize: 34,
      coverBodySize: 22,
      shellPaddingX: 104,
      shellPaddingY: 76,
    }
  : {
      headlineFont: "Georgia",
      bodyFont: "Verdana",
      monoFont: "Courier New",
      titleSize: 50,
      bigTitleSize: 72,
      bodySize: 24,
      smallSize: 16,
      labelSize: 18,
      metricValueSize: 44,
      panelTitleSize: 30,
      subtitleSize: 26,
      coverTitleSize: 106,
      coverTaglineSize: 48,
      coverBodySize: 28,
      shellPaddingX: 92,
      shellPaddingY: 72,
    };

const MEDIA = {
  feather: path.join(MEDIA_DIR, "image1.png"),
  illustration: path.join(MEDIA_DIR, "image2.png"),
  flow: path.join(MEDIA_DIR, "image3.png"),
  settings: path.join(MEDIA_DIR, "image4.png"),
  home: path.join(MEDIA_DIR, "image5.png"),
  dataTransactions: path.join(MEDIA_DIR, "image12.png"),
  dataBudgets: path.join(MEDIA_DIR, "image13.png"),
  dataAccounts: path.join(MEDIA_DIR, "image14.png"),
};

const titleStyle = {
  fontFace: STYLE.headlineFont,
  fontSize: STYLE.titleSize,
  bold: true,
  color: C.cream,
};

const bigTitleStyle = {
  fontFace: STYLE.headlineFont,
  fontSize: STYLE.bigTitleSize,
  bold: true,
  color: C.cream,
};

const bodyStyle = {
  fontFace: STYLE.bodyFont,
  fontSize: STYLE.bodySize,
  color: C.cream,
};

const smallStyle = {
  fontFace: STYLE.monoFont,
  fontSize: STYLE.smallSize,
  color: C.dim,
};

const labelStyle = {
  fontFace: STYLE.monoFont,
  fontSize: STYLE.labelSize,
  bold: true,
  color: C.cyan,
};

function addBackground(slide, accent = C.cyan) {
  slide.compose(shape({ width: fixed(W), height: fixed(H), fill: C.bg }), {
    frame: { left: 0, top: 0, width: W, height: H },
    baseUnit: 8,
  });
  slide.compose(shape({ width: fixed(500), height: fixed(500), fill: `${accent}22` }), {
    frame: { left: 1440, top: -120, width: 500, height: 500 },
    baseUnit: 8,
  });
  slide.compose(shape({ width: fixed(360), height: fixed(360), fill: `${C.coral}18` }), {
    frame: { left: -120, top: 820, width: 360, height: 360 },
    baseUnit: 8,
  });
}

function footer(page, section) {
  return row(
    { name: `footer-${page}`, width: fill, height: hug, justify: "between", align: "center" },
    [
      text(`COCOCOIN / ${section}`, { width: hug, height: hug, style: smallStyle }),
      text(String(page).padStart(2, "0"), {
        width: fixed(IS_WINDOWS_SAFE ? 42 : 36),
        height: hug,
        style: { ...smallStyle, color: C.sand, bold: true, align: "center" },
      }),
    ],
  );
}

function sectionTag(value, color = C.cyan) {
  return panel(
    {
      width: hug,
      height: hug,
      padding: { x: 16, y: 10 },
      fill: `${color}18`,
      line: { color, weight: 1.2 },
      borderRadius: 999,
    },
    text(value, {
      width: hug,
      height: hug,
      style: { ...labelStyle, color },
    }),
  );
}

function bullet(textValue, color = C.mint) {
  return row({ width: fill, height: hug, gap: 14, align: "start" }, [
    shape({ width: fixed(10), height: fixed(10), fill: color, borderRadius: 99 }),
    text(textValue, {
      width: fill,
      height: hug,
      style: bodyStyle,
    }),
  ]);
}

function metricCard(label, value, detail, color = C.cyan) {
  return panel(
    {
      width: fill,
      height: hug,
      padding: 28,
      fill: C.panel,
      line: { color: `${color}66`, weight: 1.2 },
      borderRadius: 26,
    },
    column({ width: fill, height: hug, justify: "between", gap: 18 }, [
      text(label, { width: fill, height: hug, style: { ...labelStyle, color } }),
      text(value, {
        width: fill,
        height: hug,
        style: { fontFace: STYLE.headlineFont, fontSize: STYLE.metricValueSize, bold: true, color: C.cream },
      }),
      text(detail, { width: fill, height: hug, style: { ...bodyStyle, fontSize: IS_WINDOWS_SAFE ? 18 : 20, color: C.dim } }),
    ]),
  );
}

function textPanel(title, items, color = C.cyan) {
  return panel(
    {
      width: fill,
      height: fill,
      padding: 28,
      fill: C.panel,
      line: { color: `${color}66`, weight: 1.2 },
      borderRadius: 28,
    },
    column({ width: fill, height: fill, gap: 18 }, [
      text(title, { width: fill, height: hug, style: { ...titleStyle, fontSize: STYLE.panelTitleSize, color } }),
      ...items.map((item) => bullet(item, color)),
    ]),
  );
}

function slideShell(slide, page, section, eyebrow, title, subtitle, body) {
  addBackground(slide);
  slide.compose(
    grid(
     {
        name: `root-${page}`,
        width: fill,
        height: fill,
        columns: [fr(1)],
        rows: [auto, fr(1), auto],
        rowGap: 28,
        padding: { x: STYLE.shellPaddingX, y: STYLE.shellPaddingY },
      },
      [
        column({ width: fill, height: hug, gap: 16 }, [
          sectionTag(eyebrow),
          text(title, { name: `slide-title-${page}`, width: wrap(1220), height: hug, style: titleStyle }),
          text(subtitle, {
            name: `slide-subtitle-${page}`,
            width: wrap(IS_WINDOWS_SAFE ? 1420 : 1320),
            height: hug,
            style: { ...bodyStyle, fontSize: STYLE.subtitleSize, color: C.dim },
          }),
          rule({ width: fixed(220), stroke: C.gold, weight: 4 }),
        ]),
        body,
        footer(page, section),
      ],
    ),
    { frame: { left: 0, top: 0, width: W, height: H }, baseUnit: 8 },
  );
}

function buildPresentation() {
  const presentation = Presentation.create({ slideSize: { width: W, height: H } });

  const cover = presentation.slides.add();
  addBackground(cover, C.gold);
  cover.compose(
    layers(
      { width: fill, height: fill },
      [
        shape({ width: fixed(620), height: fixed(620), fill: `${C.gold}14` }),
        image({
          path: MEDIA.feather,
          alt: "Feather graphic",
          width: fixed(700),
          height: fixed(540),
          fit: "contain",
        }),
        grid(
          {
            width: fill,
            height: fill,
            columns: [fr(1)],
            rows: [auto, auto, fr(1), auto],
            padding: { x: STYLE.shellPaddingX, y: STYLE.shellPaddingY },
          },
          [
            row({ width: fill, height: hug, justify: "between" }, [
              sectionTag("ANDROID APP / TOPIC DECK", C.gold),
              text("2026", { width: hug, height: hug, style: { ...smallStyle, color: C.sand } }),
            ]),
            column({ width: fill, height: hug, gap: 12 }, [
              text("CocoCoin", { width: wrap(760), height: hug, style: { ...bigTitleStyle, fontSize: STYLE.coverTitleSize } }),
              text("讓記帳像羽毛一樣輕盈", {
                width: wrap(920),
                height: hug,
                style: { fontFace: STYLE.headlineFont, fontSize: STYLE.coverTaglineSize, italic: !IS_WINDOWS_SAFE, color: C.gold },
              }),
              text("把快速記錄、預算感知與雲端同步放進同一個 App", {
                width: wrap(860),
                height: hug,
                style: { ...bodyStyle, fontSize: STYLE.coverBodySize },
              }),
            ]),
            row({ width: fill, height: fill, justify: "between", align: "end" }, [
              column({ width: fixed(760), height: hug, gap: 18 }, [
                metricCard("PROJECT GOAL", "5 步內完成一筆記帳", "降低輸入成本，提升持續記帳意願", C.cyan),
              ]),
              image({
                path: MEDIA.feather,
                alt: "Feather graphic",
                width: fixed(760),
                height: fixed(540),
                fit: "contain",
              }),
            ]),
            footer(1, "Cover"),
          ],
        ),
      ],
    ),
    { frame: { left: 0, top: 0, width: W, height: H }, baseUnit: 8 },
  );

  const overview = presentation.slides.add();
  slideShell(
    overview,
    2,
    "Overview",
    "STORY MAP",
    "這份簡報聚焦 10 個主題，濃縮成 13 頁重新敘事",
    "保留原始內容主軸，刪除中段 8 頁影片操作示範，改用更精煉的產品與架構視角呈現。",
    grid(
      {
        width: fill,
        height: fill,
        columns: [fr(1), fr(1)],
        rows: [fr(1), fr(1), fr(1), fr(1), fr(1)],
        columnGap: 22,
        rowGap: 22,
      },
      [
        metricCard("01", "動機與目的", "為什麼記帳需要更低摩擦"),
        metricCard("02", "產品定位", "功能模組與核心價值", C.gold),
        metricCard("03", "系統架構", "Repository / Room / Firebase"),
        metricCard("04", "核心邏輯", "資料流、同步與預算計算", C.coral),
        metricCard("05", "技術選型", "Kotlin、Android Studio、MPAndroidChart"),
        metricCard("06", "開發流程", "需求到發表的六段式節奏", C.mint),
        metricCard("07", "UI 規劃", "首頁、記帳、報表、資產、設定"),
        metricCard("08", "使用者流程", "從啟動 App 到同步資料"),
        metricCard("09", "資料庫設計", "Transactions / Budgets / Accounts"),
        metricCard("10", "未來方向", "從 MVP 走向產品化"),
      ],
    ),
  );

  const why = presentation.slides.add();
  slideShell(
    why,
    3,
    "Why",
    "PROBLEM / GOAL",
    "記帳不是真的困難，難的是每天都願意打開它",
    "CocoCoin 追求的不是功能堆疊，而是把『快輸入、可離線、看得懂』三件事做成同一個體驗。",
    grid(
      {
        width: fill,
        height: fill,
        columns: [fr(1.15), fr(0.85)],
        columnGap: 24,
      },
      [
        textPanel("使用者痛點", [
          "輸入流程繁瑣，記沒幾次就容易放棄。",
          "資料分散在紙本、Excel 與不同 App 中，難以集中整理。",
          "記錄完缺少分析與回饋，無法形成持續使用誘因。",
        ], C.coral),
        column({ width: fill, height: fill, gap: 22 }, [
          metricCard("DESIGN RULE", "快輸入 + 可離線 + 看得懂", "以低阻力記錄行為培養財務習慣", C.mint),
          metricCard("EXPERIENCE TARGET", "即記即回饋", "先完成紀錄，再提供預算與分析視角", C.gold),
        ]),
      ],
    ),
  );

  const product = presentation.slides.add();
  slideShell(
    product,
    4,
    "Positioning",
    "PRODUCT MAP",
    "以個人財務習慣建立為核心，把『好記、好查、看得懂、用得久』做成產品骨架",
    "產品設計不是把功能平均鋪開，而是把最常用的行為聚集到高頻路徑上。",
    grid(
      {
        width: fill,
        height: fill,
        columns: [fr(1), fr(1), fr(1)],
        rows: [fr(1), fr(1)],
        columnGap: 20,
        rowGap: 20,
      },
      [
        textPanel("帳款紀錄", ["收入 / 支出切換", "日期、類別、備註", "付款錢包選擇"], C.cyan),
        textPanel("預算管理", ["按月設定預算", "追蹤剩餘額度", "顯示超支狀態"], C.gold),
        textPanel("多錢包管理", ["現金 / 銀行 / 禮券 / 悠遊卡", "帳戶餘額更新", "來源分流更清楚"], C.mint),
        textPanel("快速查詢", ["依月份搜尋", "特殊區間檢索", "最近交易回看"], C.coral),
        textPanel("數據可視化", ["月報表", "分類占比", "趨勢圖整理資訊"], C.cyan),
        textPanel("雲端同步", ["登入後跨裝置延續", "背景同步備份", "本機優先避免卡頓"], C.gold),
      ],
    ),
  );

  const arch = presentation.slides.add();
  slideShell(
    arch,
    5,
    "Architecture",
    "SYSTEM DESIGN",
    "以 Repository 分層做資料統一入口，首頁進一步導入 ViewModel 管理畫面狀態",
    "這個架構的重點不是學術上的分層完整，而是讓交易、預算、錢包與同步可以各自維護、一起運作。",
    column({ width: fill, height: fill, gap: 22 }, [
      grid(
        {
          width: fill,
          height: fixed(300),
          columns: [fr(1), fr(1), fr(1)],
          columnGap: 20,
        },
        [
          metricCard("PRESENTATION", "首頁 / 分析 / 記帳 / 預算 / 設定", "畫面分頁與互動入口", C.cyan),
          metricCard("VIEWMODEL", "狀態管理與驗證", "處理輸入、查詢、預算與畫面資料", C.gold),
          metricCard("REPOSITORY", "單一資料入口", "封裝本機、同步、備份與還原邏輯", C.mint),
        ],
      ),
      panel(
        {
          width: fill,
          height: fill,
          padding: 28,
          fill: C.panelAlt,
          line: { color: `${C.cyan}66`, weight: 1.2 },
          borderRadius: 30,
        },
        grid(
          {
            width: fill,
            height: fill,
            columns: [fr(1.2), fr(0.8)],
            columnGap: 22,
          },
          [
            column({ width: fill, height: fill, gap: 16 }, [
              bullet("離線優先：交易先寫入 Room / SQLite，再進行背景同步。", C.mint),
              bullet("模組化：預算、交易、錢包可獨立維護與測試。", C.gold),
              bullet("單一資料入口：畫面層透過 Repository 取得資料，不直接碰資料源。", C.cyan),
            ]),
            metricCard("DATA SOURCE", "Room / SQLite + Firebase", "本機提供速度，雲端負責帳號與跨裝置恢復", C.coral),
          ],
        ),
      ),
    ]),
  );

  const logic = presentation.slides.add();
  slideShell(
    logic,
    6,
    "Logic",
    "DATA FLOW",
    "一筆交易會依序完成驗證、寫入、計算與同步，兼顧畫面回饋速度與資料一致性",
    "流程設計的核心，是讓『可立即看到結果』與『可跨裝置保留資料』兩件事同時成立。",
    grid(
      {
        width: fill,
        height: fill,
        columns: [fr(1), fr(1), fr(1), fr(1), fr(1)],
        columnGap: 18,
      },
      [
        metricCard("01", "輸入交易", "金額、類別、日期、錢包、備註", C.cyan),
        metricCard("02", "表單驗證", "檢查必填欄位與數值合理性", C.gold),
        metricCard("03", "本機寫入", "先進 Room / SQLite，立即更新畫面", C.mint),
        metricCard("04", "預算計算", "重算總額、剩餘預算與超支狀態", C.coral),
        metricCard("05", "雲端同步", "背景同步 Firebase 做跨裝置備份", C.cyan),
      ],
    ),
  );

  const tech = presentation.slides.add();
  slideShell(
    tech,
    7,
    "Tech Stack",
    "TOOLS / RUNTIME",
    "技術選型不是為了堆工具，而是回應這個 App 真正需要的能力",
    "語言、資料層、身份驗證、雲端同步與圖表視覺化，各自對應產品中的一段任務。",
    grid(
      {
        width: fill,
        height: fill,
        columns: [fr(1), fr(1), fr(1)],
        rows: [fr(1), fr(1)],
        columnGap: 20,
        rowGap: 20,
      },
      [
        metricCard("K", "Kotlin", "主力開發語言", C.cyan),
        metricCard("A", "Android Studio", "開發、除錯、模擬器測試", C.gold),
        metricCard("R", "Room / SQLite", "本機主要資料來源、支援離線", C.mint),
        metricCard("F", "Firebase Auth", "登入與身份管理", C.coral),
        metricCard("C", "Cloud Firestore", "資料備份與跨裝置還原", C.cyan),
        metricCard("M", "MPAndroidChart", "圓餅圖與長條圖視覺化", C.gold),
      ],
    ),
  );

  const process = presentation.slides.add();
  slideShell(
    process,
    8,
    "Process",
    "DELIVERY FLOW",
    "從需求、架構、介面到發表，以 6 個階段推進開發節奏",
    "比起一次做完所有功能，這份專題更像是不斷縮小風險、把可用版本逐步做穩。",
    grid(
      {
        width: fill,
        height: fill,
        columns: [fr(1), fr(1), fr(1)],
        rows: [fr(1), fr(1)],
        columnGap: 20,
        rowGap: 20,
      },
      [
        metricCard("1", "需求分析", "定義使用者、功能範圍與清單", C.cyan),
        metricCard("2", "資訊架構", "規劃資料表、欄位、頁面關係", C.gold),
        metricCard("3", "UI 原型", "確認首頁、記帳頁、報表頁流程", C.mint),
        metricCard("4", "功能開發", "完成交易、預算、同步模組", C.coral),
        metricCard("5", "測試修正", "驗證流程、修復 bug、調整體驗", C.cyan),
        panel(
          {
            width: fill,
            height: fill,
            padding: 12,
            fill: C.panelAlt,
            line: { color: `${C.gold}66`, weight: 1.2 },
            borderRadius: 28,
          },
          column({ width: fill, height: fill, gap: 12, justify: "between" }, [
            metricCard("6", "成果發表", "整理輸出並規劃後續版本", C.gold),
            image({
              path: MEDIA.illustration,
              alt: "Development illustration",
              width: fill,
              height: fixed(120),
              fit: "contain",
            }),
          ]),
        ),
      ],
    ),
  );

  const ui = presentation.slides.add();
  slideShell(
    ui,
    9,
    "UI / UX",
    "INTERFACE LOGIC",
    "介面不是把功能塞滿，而是只留下高頻且重要的操作",
    "新版簡報保留原始畫面截圖作為證據，但整體呈現改為深色舞台與編輯式陳列。",
    grid(
      {
        width: fill,
        height: fill,
        columns: [fr(0.9), fr(1.1)],
        columnGap: 24,
      },
      [
        column({ width: fill, height: fill, gap: 18 }, [
          textPanel("首頁 Dashboard", ["月收支、預算使用率、最近交易一屏讀取"], C.cyan),
          textPanel("記帳 Transaction", ["金額、分類、錢包、備註集中於單頁完成"], C.gold),
          textPanel("分析 Analytics", ["分類占比、趨勢圖與區間查詢"], C.mint),
          textPanel("資產 / 設定", ["帳戶、預算、登入、同步與備份管理"], C.coral),
        ]),
        row({ width: fill, height: fill, gap: 18, align: "center" }, [
          panel(
            {
              width: fill,
              height: fill,
              padding: 10,
              fill: C.panel,
              line: { color: `${C.cyan}66`, weight: 1.2 },
              borderRadius: 28,
            },
            image({ path: MEDIA.home, alt: "Home screen", width: fill, height: fill, fit: "contain" }),
          ),
          panel(
            {
              width: fill,
              height: fill,
              padding: 10,
              fill: C.panel,
              line: { color: `${C.gold}66`, weight: 1.2 },
              borderRadius: 28,
            },
            image({ path: MEDIA.settings, alt: "Settings screen", width: fill, height: fill, fit: "contain" }),
          ),
        ]),
      ],
    ),
  );

  const flow = presentation.slides.add();
  slideShell(
    flow,
    10,
    "Flow",
    "USER JOURNEY",
    "使用者流程圖保留原本概念，但在簡報裡只保留總覽，不再展開後續 8 頁影片示範",
    "從 Firebase 判斷、登入狀態、本機載入與雲端同步，最後都回到首頁各模組入口。",
    grid(
      {
        width: fill,
        height: fill,
        columns: [fr(0.42), fr(0.58)],
        columnGap: 24,
      },
      [
        column({ width: fill, height: fill, gap: 18, justify: "center" }, [
          metricCard("入口判斷", "Firebase 是否已設定", "未設定時載入本機資料，已設定則進一步確認登入狀態", C.cyan),
          metricCard("同步策略", "本機優先、雲端補同步", "既保留速度，也支援跨裝置接續使用", C.gold),
        ]),
        panel(
          {
            width: fill,
            height: fill,
            padding: 14,
            fill: "#F9F4EC",
            borderRadius: 26,
          },
          image({ path: MEDIA.flow, alt: "User flow", width: fill, height: fill, fit: "contain" }),
        ),
      ],
    ),
  );

  const db = presentation.slides.add();
  slideShell(
    db,
    11,
    "Database",
    "DATA MODEL",
    "資料庫邏輯以交易、預算與帳戶三張核心表為主，支撐查詢、預算比對與餘額回寫",
    "原稿中的資料表結構保留為視覺證據，並重新整合到同一個深色資訊版面中。",
    grid(
      {
        width: fill,
        height: fill,
        columns: [fr(1)],
        rows: [auto, fr(1)],
        rowGap: 18,
      },
      [
        row({ width: fill, height: hug, gap: 18 }, [
          metricCard("Transactions", "交易主表", "紀錄類型、金額、備註、時間與帳戶關聯", C.cyan),
          metricCard("Budgets", "預算表", "以 year + month 為複合主鍵", C.gold),
          metricCard("Accounts", "帳戶表", "以帳戶名稱管理餘額與更新時間", C.mint),
        ]),
        row({ width: fill, height: fill, gap: 18 }, [
          panel(
            { width: fill, height: fill, padding: 10, fill: "#F9F4EC", borderRadius: 20 },
            image({ path: MEDIA.dataTransactions, alt: "Transactions table", width: fill, height: fill, fit: "contain" }),
          ),
          panel(
            { width: fill, height: fill, padding: 10, fill: "#F9F4EC", borderRadius: 20 },
            image({ path: MEDIA.dataBudgets, alt: "Budgets table", width: fill, height: fill, fit: "contain" }),
          ),
          panel(
            { width: fill, height: fill, padding: 10, fill: "#F9F4EC", borderRadius: 20 },
            image({ path: MEDIA.dataAccounts, alt: "Accounts table", width: fill, height: fill, fit: "contain" }),
          ),
        ]),
      ],
    ),
  );

  const future = presentation.slides.add();
  slideShell(
    future,
    12,
    "Future",
    "ROADMAP",
    "從 MVP 到 Release，產品重點會從『先讓記錄穩定』逐步走向『多人共享與產品化能力』",
    "這個版本規劃延續原簡報的節奏，但把階段目標改成更像產品 roadmap 的呈現方式。",
    grid(
      {
        width: fill,
        height: fill,
        columns: [fr(1), fr(1), fr(1)],
        columnGap: 22,
      },
      [
        textPanel("MVP 版", ["快速記帳", "預算管理", "本機資料儲存", "先建立穩定輸入流程"], C.cyan),
        textPanel("Beta 版", ["帳號登入", "雲端同步", "月報表與分類圖表", "補齊同步與報表能力"], C.gold),
        textPanel("Release 版", ["共享帳本", "提醒通知", "資料匯出 / Widget", "朝多人與產品化前進"], C.mint),
      ],
    ),
  );

  const recap = presentation.slides.add();
  slideShell(
    recap,
    13,
    "Recap",
    "QUICK RECAP",
    "這個專題完成了一套可離線、可同步、可分析的記帳系統",
    "重點不是單一功能，而是把資料紀錄、同步與可視化串成一個持續使用的循環。",
    grid(
      {
        width: fill,
        height: fill,
        columns: [fr(1.1), fr(0.9)],
        columnGap: 24,
      },
      [
        column({ width: fill, height: fill, gap: 18 }, [
          bullet("以 SQLite + Firebase 建立雙資料架構。", C.cyan),
          bullet("以 Repository + Room 建立穩定且可維護的資料流。", C.gold),
          bullet("以資料可視化把記錄轉成可理解的財務洞察。", C.mint),
          bullet("把快速記帳、預算感知與同步備份收斂到同一個 App。", C.coral),
        ]),
        panel(
          {
            width: fill,
            height: fill,
            padding: 36,
            fill: C.panelAlt,
            line: { color: `${C.gold}66`, weight: 1.2 },
            borderRadius: 30,
            justify: "center",
          },
          column({ width: fill, height: hug, gap: 18, justify: "center", align: "center" }, [
            text("THANK YOU", {
              width: hug,
              height: hug,
              style: { fontFace: STYLE.headlineFont, fontSize: IS_WINDOWS_SAFE ? 38 : 42, bold: true, color: C.gold },
            }),
            text("CocoCoin / 專題架構重製版", {
              width: hug,
              height: hug,
              style: { ...bodyStyle, fontSize: IS_WINDOWS_SAFE ? 22 : 24, color: C.cream },
            }),
          ]),
        ),
      ],
    ),
  );

  return presentation;
}

async function main() {
  await fs.mkdir(OUTPUT_DIR, { recursive: true });
  await fs.mkdir(PREVIEW_DIR, { recursive: true });

  const presentation = buildPresentation();
  const pendingImages = presentation.getPendingImageHydrationRequests();
  if (pendingImages.length) {
    const hydrated = [];
    for (const request of pendingImages) {
      if (!request.assetId || !request.uri) continue;
      const data = await fs.readFile(request.uri);
      hydrated.push({
        assetId: request.assetId,
        data,
        contentType: request.contentType || "image/png",
      });
    }
    presentation.hydrateImageAssets(hydrated);
  }
  const pptx = await PresentationFile.exportPptx(presentation);
  await pptx.save(
    path.join(
      OUTPUT_DIR,
      IS_WINDOWS_SAFE ? "專題架構_v5_新風格版_Windows PowerPoint 相容保守版.pptx" : "專題架構_v5_新風格版.pptx",
    ),
  );

  for (let i = 0; i < presentation.slides.count; i += 1) {
    const slide = presentation.slides.getItem(i);
    const png = await slide.export({ format: "png" });
    const bytes = Buffer.from(await png.arrayBuffer());
    await fs.writeFile(path.join(PREVIEW_DIR, `slide-${String(i + 1).padStart(2, "0")}.png`), bytes);
  }
}

await main();
