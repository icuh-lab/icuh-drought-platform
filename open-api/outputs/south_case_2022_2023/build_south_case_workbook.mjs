import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outDir = "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/south_case_2022_2023";
const outputPath = path.join(outDir, "실측가뭄_남부사례_데이터추출_시각화_v1.xlsx");

const readJson = async (name) => JSON.parse(await fs.readFile(path.join(outDir, name), "utf8"));
const summary = await readJson("summary.json");
const monthly = await readJson("monthly_panel.json");
const newsMonthly = await readJson("news_monthly.json");
const newsArticles = await readJson("news_articles.json");
const jenks = await readJson("jenks_grades.json");
const fresh = await readJson("fresh_price_index.json");
const hydro = await readJson("hapcheon_hydro.json");
const onionCheck = await readJson("onion_db_check.json");

const months = [...new Set(monthly.map((r) => r.ym))].sort();
const regions = ["전남 고흥군", "경남 합천군"];
const gradeRank = { "관심": 1, "주의": 2, "경고": 3, "위험": 4 };
const gradeColor = { "관심": "#D9EAD3", "주의": "#FFF2CC", "경고": "#FCE4D6", "위험": "#F4CCCC" };

function num(v) {
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function pct(v) {
  const n = num(v);
  return n == null ? null : n / 100;
}

function colLetter(n) {
  let s = "";
  while (n > 0) {
    const m = (n - 1) % 26;
    s = String.fromCharCode(65 + m) + s;
    n = Math.floor((n - m) / 26);
  }
  return s;
}

function writeTable(ws, rows, widths = []) {
  ws.showGridLines = false;
  if (rows.length === 0) return;
  const range = ws.getRangeByIndexes(0, 0, rows.length, rows[0].length);
  range.values = rows;
  range.format.font.name = "Kopub World Medium";
  range.format.font.size = 10;
  range.format.wrapText = true;
  range.format.verticalAlignment = "center";
  range.format.borders = { preset: "all", style: "thin", color: "#D9D9D9" };
  const header = ws.getRangeByIndexes(0, 0, 1, rows[0].length);
  header.format.font.name = "Kopub World Bold";
  header.format.font.bold = true;
  header.format.fill.color = "#D9EAF7";
  header.format.horizontalAlignment = "center";
  ws.freezePanes.freezeRows(1);
  for (let i = 0; i < widths.length; i++) {
    ws.getRange(`${colLetter(i + 1)}:${colLetter(i + 1)}`).format.columnWidth = widths[i];
  }
}

function makeSheet(wb, name, rows, widths) {
  const ws = wb.worksheets.add(name);
  writeTable(ws, rows, widths);
  return ws;
}

function monthRowsByRegion(region) {
  return monthly.filter((r) => r.region === region).sort((a, b) => a.ym.localeCompare(b.ym));
}

const totalNews = newsArticles.length;
const goheungNews = newsArticles.filter((r) => r.region === "전남 고흥군").length;
const hapcheonNews = newsArticles.filter((r) => r.region === "경남 합천군").length;
const topMonth = months.map((ym) => ({
  ym,
  cnt: monthly.filter((r) => r.ym === ym).reduce((a, r) => a + Number(r.news_article_count || 0), 0),
})).sort((a, b) => b.cnt - a.cnt)[0];
const gradeRows = monthly.filter((r) => r.max_jenks_grade);
const highGrade = gradeRows.filter((r) => (gradeRank[r.max_jenks_grade] ?? 0) >= 3).length;
const onionRows = onionCheck.reduce((a, r) => a + Number(r.rows_cnt || 0), 0);

const workbook = Workbook.create();

const guideRows = [
  ["항목", "내용"],
  ["문서명", "2022~2023년 남부지방 가뭄 사례 검증용 DB 추출자료"],
  ["작성일", "2026-08-18"],
  ["대상 사례", "CASE_SOUTH_22_23"],
  ["대상 기간", "2022-01~2023-12"],
  ["대상 지역", "전남 고흥군, 경남 합천군"],
  ["조회 DB", "AWS RDS / ACTUAL_DRGHT / 읽기 전용 조회"],
  ["포함 데이터", "뉴스 원문 및 지역·영향분야 bridge, 월별 Jenks 등급, 신선물가지수, 합천댐 보조자료"],
  ["중요 제한", "표준 가격 테이블(daily_market_trends, daily_price_predictions, monthly_market_predictions)에서 양파 행은 0건으로 확인되어 가격·예측가격은 이번 DB 추출본에 포함하지 못함"],
  ["해석 원칙", "본 자료는 동일 시기·지역에서 뉴스 영향, 정형 지표 변화, 보조 수문자료가 일관되게 나타나는지 확인하기 위한 검증용이며 인과관계를 단정하지 않음"],
];
makeSheet(workbook, "작성안내", guideRows, [24, 120]);

const summaryRows = [
  ["구분", "값", "해석"],
  ["뉴스 bridge 행 수", totalNews, "고흥·합천 2022~2023 기간의 사례 관련 기사-지역-분야 연결 행"],
  ["고흥 뉴스 행 수", goheungNews, "전남 고흥군 mention 기준"],
  ["합천 뉴스 행 수", hapcheonNews, "경남 합천군 mention 기준"],
  ["최다 기사 월", `${topMonth.ym} (${topMonth.cnt}건)`, "뉴스 기반 반응이 가장 강한 월"],
  ["Jenks 등급 행 수", jenks.length, "전남·경남 SIDO/MONTH 기준 등급"],
  ["경고 이상 월-지역 수", highGrade, "월별 통합패널에서 최대 등급이 경고 이상인 행"],
  ["신선물가지수 행 수", fresh.length, "전남/경남 각 24개월"],
  ["합천댐 보조 월자료", hydro.length, "중복 적재를 월별 평균으로 접어 사용"],
  ["DB 내 양파 가격 행 수", onionRows, "현재 RDS 표준 가격 테이블에는 양파가 없어 별도 적재 또는 로컬 모델 산출물 연결 필요"],
];
makeSheet(workbook, "요약", summaryRows, [26, 28, 90]);

const panelHeaders = [
  "case_id", "연월", "지역", "시도", "뉴스건수", "농업기사수", "물공급기사수",
  "최대Jenks등급", "최대등급분야", "농업등급", "물공급등급",
  "신선식품지수", "신선식품MoM", "신선식품YoY",
  "신선채소지수", "신선채소MoM", "신선채소YoY",
  "신선과실지수", "신선과실MoM", "신선과실YoY",
  "합천댐실적발전량_MWh", "합천댐저수량_백만㎥", "발전량YoY", "저수량MoM"
];
const panelRows = monthly.map((r) => [
  r.case_id, r.ym, r.region, r.sido,
  num(r.news_article_count), num(r.agriculture_article_count), num(r.water_article_count),
  r.max_jenks_grade, r.max_jenks_impact, r.agriculture_jenks_grade, r.water_supply_jenks_grade,
  num(r.fresh_food_index), pct(r.fresh_food_index_mom_rate), pct(r.fresh_food_index_yoy_rate),
  num(r.fresh_vegetable_index), pct(r.fresh_vegetable_index_mom_rate), pct(r.fresh_vegetable_index_yoy_rate),
  num(r.fresh_fruit_index), pct(r.fresh_fruit_index_mom_rate), pct(r.fresh_fruit_index_yoy_rate),
  num(r.hapcheon_dam_actual_mwh), num(r.hapcheon_dam_water_storage_mcm), pct(r.hydro_generation_yoy_rate), pct(r.water_storage_mom_rate),
]);
const panelWs = makeSheet(workbook, "월별통합패널", [panelHeaders, ...panelRows], [20, 12, 16, 10, 10, 12, 12, 14, 16, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 18, 18, 12, 12]);
panelWs.getRange("M:N").format.numberFormat = "0.0%";
panelWs.getRange("P:Q").format.numberFormat = "0.0%";
panelWs.getRange("S:T").format.numberFormat = "0.0%";
panelWs.getRange("W:X").format.numberFormat = "0.0%";

const newsRows = [
  ["연월", "지역", "시도", "시군구", "영향코드", "영향분야", "기사수"],
  ...newsMonthly.map((r) => [r.ym, r.region, r.sido, r.sigungu, r.impact_code, r.impact_name, num(r.article_count)]),
];
makeSheet(workbook, "뉴스월별집계", newsRows, [12, 16, 10, 12, 10, 14, 10]);

const articleRows = [
  ["연월", "기사일자", "지역", "영향코드", "영향분야", "제목", "피해상세", "본문요약", "URL"],
  ...newsArticles.map((r) => [r.ym, r.published_at, r.region, r.impact_code, r.impact_name, r.title, r.damage_detail_first ?? r.damage_detail, r.body_excerpt, r.link]),
];
makeSheet(workbook, "기사목록", articleRows, [12, 13, 16, 10, 14, 48, 34, 70, 48]);

const jenksRows = [
  ["연월", "지역", "시도", "영향코드", "영향분야", "등급"],
  ...jenks.map((r) => [r.ym, r.region, r.sido, r.impact_code, r.impact_name, r.grade]),
];
const jenksWs = makeSheet(workbook, "Jenks등급", jenksRows, [12, 16, 10, 10, 14, 10]);
for (let i = 2; i <= jenksRows.length; i++) {
  const grade = jenksRows[i - 1][5];
  if (gradeColor[grade]) jenksWs.getRange(`F${i}`).format.fill.color = gradeColor[grade];
}

const freshRows = [
  ["연월", "지역", "시도", "총지수", "신선식품", "신선채소", "신선과실", "신선식품제외"],
  ...fresh.map((r) => [r.ym, r.region, r.province, num(r.total_index), num(r.fresh_food_index), num(r.fresh_vegetable_index), num(r.fresh_fruit_index), num(r.excluding_fresh_food_index)]),
];
makeSheet(workbook, "신선물가지수", freshRows, [12, 16, 16, 12, 12, 12, 12, 14]);

const hydroRows = [
  ["댐명", "댐코드", "연월", "계획발전량_MWh", "실적발전량_MWh", "저수위_ELm", "저수량_백만㎥", "발전량YoY", "발전량MoM", "저수량MoM"],
  ...hydro.map((r) => [r.dam_name, r.dam_code, r.ym, num(r.planned_mwh), num(r.actual_mwh), num(r.water_level_elm), num(r.water_storage_mcm), pct(r.hydro_generation_last_year_month_rate), pct(r.hydro_generation_last_month_rate), pct(r.average_water_storage_last_month_rate)]),
];
const hydroWs = makeSheet(workbook, "합천댐보조", hydroRows, [10, 14, 12, 18, 18, 14, 16, 12, 12, 12]);
hydroWs.getRange("H:J").format.numberFormat = "0.0%";

const limitationRows = [
  ["점검 항목", "결과", "조치 필요"],
  ["양파 가격 DB 적재", "표준 가격 테이블 3종에서 양파 행 0건", "로컬 양파모델 산출물 RDS 적재 여부 확인 또는 별도 파일 연결"],
  ["수력 자료", "합천댐 월자료는 존재하나 RDS 중복 적재가 있어 월별 평균으로 중복 제거", "본 검증에서는 보조자료로만 사용"],
  ["공간 단위 차이", "뉴스는 시군구, 신선물가는 시도, 양파 가격은 DB 미확인, 수력은 댐 단위", "해석 시 같은 공간 단위가 아님을 명시"],
  ["인과 해석", "동시성 확인 가능, 직접 인과 단정 불가", "보고서에는 '대체로 부합/동시 관측' 표현 사용"],
];
makeSheet(workbook, "제약사항", limitationRows, [24, 70, 70]);

// Chart data and PPT-oriented sheets.
const newsChartRows = [["연월", "고흥 뉴스건수", "합천 뉴스건수", "고흥 농업등급", "합천 농업등급"]];
for (const ym of months) {
  const go = monthly.find((r) => r.ym === ym && r.region === "전남 고흥군");
  const ha = monthly.find((r) => r.ym === ym && r.region === "경남 합천군");
  newsChartRows.push([ym, num(go?.news_article_count), num(ha?.news_article_count), gradeRank[go?.agriculture_jenks_grade] ?? null, gradeRank[ha?.agriculture_jenks_grade] ?? null]);
}
const pptNews = workbook.worksheets.add("PPT_뉴스추이");
pptNews.showGridLines = false;
pptNews.getRange("A1:H1").merge();
pptNews.getRange("A1").values = [["남부 사례 뉴스 영향은 2022년 5~6월과 11월에 집중"]];
pptNews.getRange("A1").format.font.name = "Kopub World Bold";
pptNews.getRange("A1").format.font.size = 16;
pptNews.getRange("A1").format.font.bold = true;
pptNews.getRange("A3:E27").values = newsChartRows;
pptNews.getRange("A3:E3").format.fill.color = "#D9EAF7";
pptNews.getRange("A3:E27").format.borders = { preset: "all", style: "thin", color: "#D9D9D9" };
pptNews.getRange("A3:E27").format.font.name = "Kopub World Medium";
for (let i = 0; i < 5; i++) pptNews.getRange(`${colLetter(i + 1)}:${colLetter(i + 1)}`).format.columnWidth = [12, 14, 14, 14, 14][i];
const chart1 = pptNews.charts.add("line", {
  title: "월별 뉴스 기사수(고흥·합천)",
  categories: months,
  series: [
    { name: "전남 고흥군", values: newsChartRows.slice(1).map((r) => r[1]) },
    { name: "경남 합천군", values: newsChartRows.slice(1).map((r) => r[2]) },
  ],
  hasLegend: true,
  legend: { position: "bottom" },
  dataLabels: { showValue: false },
  from: { row: 2, col: 6 },
  extent: { widthPx: 760, heightPx: 360 },
});
chart1.yAxis = { title: "기사수", majorGridlines: { visible: true } };
pptNews.getRange("G23:N27").values = [
  ["핵심 읽기"],
  [`전체 bridge 행 ${totalNews}건: 고흥 ${goheungNews}건, 합천 ${hapcheonNews}건`],
  [`최다 월: ${topMonth.ym} ${topMonth.cnt}건`],
  ["남부 사례의 DB 뉴스는 물공급보다 농업 영향(A2)이 중심"],
  ["Jenks 등급과 실제 기사 제목/피해상세를 월별로 대조 가능"],
];
for (let r = 23; r <= 27; r++) pptNews.getRange(`G${r}:N${r}`).merge();
pptNews.getRange("A23:N27").format.rowHeight = 28;
pptNews.getRange("G23:N27").format.fill.color = "#F3F6FA";
pptNews.getRange("G23:N27").format.borders = { preset: "outside", style: "thin", color: "#B7C9D6" };
pptNews.getRange("G23:N27").format.wrapText = true;

const indexRows = [["연월", "전남 신선채소", "경남 신선채소", "전남 신선과실", "경남 신선과실"]];
for (const ym of months) {
  const go = monthly.find((r) => r.ym === ym && r.region === "전남 고흥군");
  const ha = monthly.find((r) => r.ym === ym && r.region === "경남 합천군");
  indexRows.push([ym, num(go?.fresh_vegetable_index), num(ha?.fresh_vegetable_index), num(go?.fresh_fruit_index), num(ha?.fresh_fruit_index)]);
}
const pptIndex = workbook.worksheets.add("PPT_정형지표");
pptIndex.showGridLines = false;
pptIndex.getRange("A1:H1").merge();
pptIndex.getRange("A1").values = [["시도 단위 신선물가지수는 농산물 가격 지표의 보조축"]];
pptIndex.getRange("A1").format.font.name = "Kopub World Bold";
pptIndex.getRange("A1").format.font.size = 16;
pptIndex.getRange("A1").format.font.bold = true;
pptIndex.getRange("A3:E27").values = indexRows;
pptIndex.getRange("A3:E3").format.fill.color = "#D9EAF7";
pptIndex.getRange("A3:E27").format.borders = { preset: "all", style: "thin", color: "#D9D9D9" };
const chart2 = pptIndex.charts.add("line", {
  title: "신선채소 물가지수 추이(전남·경남)",
  categories: months,
  series: [
    { name: "전남", values: indexRows.slice(1).map((r) => r[1]) },
    { name: "경남", values: indexRows.slice(1).map((r) => r[2]) },
  ],
  hasLegend: true,
  legend: { position: "bottom" },
  from: { row: 2, col: 6 },
  extent: { widthPx: 760, heightPx: 360 },
});
chart2.yAxis = { title: "지수" };
pptIndex.getRange("G23:N27").values = [
  ["핵심 읽기"],
  ["신선물가지수는 시군구가 아닌 시도 단위 지표"],
  ["고흥은 전라남도, 합천은 경상남도 값을 연결"],
  ["전월 대비와 전년동월 대비 변화율은 월별통합패널에서 확인"],
  ["양파 가격은 현재 RDS 표준 가격 테이블에 없어 별도 보강 필요"],
];
for (let r = 23; r <= 27; r++) pptIndex.getRange(`G${r}:N${r}`).merge();
pptIndex.getRange("A23:N27").format.rowHeight = 28;
pptIndex.getRange("G23:N27").format.fill.color = "#F3F6FA";
pptIndex.getRange("G23:N27").format.borders = { preset: "outside", style: "thin", color: "#B7C9D6" };
pptIndex.getRange("G23:N27").format.wrapText = true;

const heatRows = [["연월", "고흥 농업", "고흥 물공급", "합천 농업", "합천 물공급", "뉴스합계"]];
for (const ym of months) {
  const go = monthly.find((r) => r.ym === ym && r.region === "전남 고흥군");
  const ha = monthly.find((r) => r.ym === ym && r.region === "경남 합천군");
  heatRows.push([ym, go?.agriculture_jenks_grade ?? "", go?.water_supply_jenks_grade ?? "", ha?.agriculture_jenks_grade ?? "", ha?.water_supply_jenks_grade ?? "", (num(go?.news_article_count) ?? 0) + (num(ha?.news_article_count) ?? 0)]);
}
const pptHeat = workbook.worksheets.add("PPT_등급요약");
pptHeat.showGridLines = false;
pptHeat.getRange("A1:F1").merge();
pptHeat.getRange("A1").values = [["월별 Jenks 등급 요약: 농업 영향 중심, 2022년 5~6월·11월 확인"]];
pptHeat.getRange("A1").format.font.name = "Kopub World Bold";
pptHeat.getRange("A1").format.font.size = 16;
pptHeat.getRange("A1").format.font.bold = true;
pptHeat.getRange("A3:F27").values = heatRows;
pptHeat.getRange("A3:F3").format.fill.color = "#D9EAF7";
pptHeat.getRange("A3:F27").format.borders = { preset: "all", style: "thin", color: "#D9D9D9" };
pptHeat.getRange("A3:F27").format.font.name = "Kopub World Medium";
for (let i = 4; i <= 27; i++) {
  for (const c of ["B", "C", "D", "E"]) {
    const val = heatRows[i - 3]?.[c.charCodeAt(0) - 65];
    if (gradeColor[val]) pptHeat.getRange(`${c}${i}`).format.fill.color = gradeColor[val];
  }
}
for (let i = 0; i < 6; i++) pptHeat.getRange(`${colLetter(i + 1)}:${colLetter(i + 1)}`).format.columnWidth = [12, 14, 14, 14, 14, 12][i];
pptHeat.getRange("H3:N12").values = [
  ["PPT용 요약문"],
  ["1. DB 기준 남부 사례 뉴스는 총 25건이며 농업 분야가 대부분이다."],
  ["2. 고흥군은 2022년 11월 농업 6건, 물공급 1건으로 집중된다."],
  ["3. 합천군은 2022년 3~6월 농업 기사와 등급 상승이 관측된다."],
  ["4. 신선물가지수·합천댐 자료는 월별 보조축으로 연결 가능하다."],
  ["5. 양파 가격/예측은 RDS 표준 가격 테이블에 없어 별도 적재 확인이 필요하다."],
];
for (let r = 3; r <= 8; r++) pptHeat.getRange(`H${r}:N${r}`).merge();
pptHeat.getRange("A3:N8").format.rowHeight = 34;
pptHeat.getRange("H3:N12").format.fill.color = "#F3F6FA";
pptHeat.getRange("H3:N12").format.borders = { preset: "outside", style: "thin", color: "#B7C9D6" };
pptHeat.getRange("H3:N12").format.wrapText = true;

await fs.mkdir(outDir, { recursive: true });

for (const [sheetName, range] of [
  ["PPT_뉴스추이", "A1:S28"],
  ["PPT_정형지표", "A1:S28"],
  ["PPT_등급요약", "A1:S28"],
  ["요약", "A1:C10"],
]) {
  const png = await workbook.render({ sheetName, range, scale: 1, format: "png" });
  await fs.writeFile(path.join(outDir, `${sheetName}.png`), new Uint8Array(await png.arrayBuffer()));
}

const inspect = await workbook.inspect({
  kind: "table",
  sheetId: "요약",
  range: "A1:C10",
  tableMaxRows: 10,
  tableMaxCols: 3,
  maxChars: 4000,
});
console.log(inspect.ndjson);

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  maxChars: 2000,
});
console.log(errors.ndjson);

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(outputPath);
