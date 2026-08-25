import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outDir = "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/south_case_2022_2023_validation";
const outputPath = path.join(outDir, "실측가뭄_남부사례_actual_drought_validation_추출_시각화_v2.xlsx");

const readJson = async (name) => JSON.parse(await fs.readFile(path.join(outDir, name), "utf8"));

const summary = await readJson("summary.json");
const monthly = await readJson("monthly_panel.json");
const newsMonthly = await readJson("news_monthly.json");
const newsArticles = await readJson("news_articles.json");
const jenks = await readJson("jenks_grades.json");
const onionActual = await readJson("onion_actual_monthly.json");
const onionDailyPred = await readJson("onion_daily_prediction_monthly.json");
const onionMonthlyPred = await readJson("onion_monthly_predictions.json");
const fresh = await readJson("fresh_price_index.json");
const hydro = await readJson("hapcheon_hydro.json");
const tableInfo = await readJson("table_info.json");
const columns = await readJson("columns.json");

const months = [...new Set(monthly.map((r) => r.ym))].sort();
const gradeRank = { "관심": 1, "주의": 2, "경고": 3, "위험": 4 };
const gradeColor = { "관심": "#D9EAD3", "주의": "#FFF2CC", "경고": "#FCE4D6", "위험": "#F4CCCC" };

function num(v) {
  if (v === null || v === undefined || v === "") return null;
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

function getPanel(region, ym) {
  return monthly.find((r) => r.region === region && r.ym === ym) ?? {};
}

function writeTable(ws, rows, widths = []) {
  ws.showGridLines = false;
  if (!rows.length) return;
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

function setTitle(ws, range, title) {
  ws.getRange(range).merge();
  ws.getRange(range.split(":")[0]).values = [[title]];
  ws.getRange(range.split(":")[0]).format.font.name = "Kopub World Bold";
  ws.getRange(range.split(":")[0]).format.font.size = 16;
  ws.getRange(range.split(":")[0]).format.font.bold = true;
}

function writeMemo(ws, startRow, startCol, widthCols, lines) {
  const start = `${colLetter(startCol)}${startRow}`;
  const endCol = colLetter(startCol + widthCols - 1);
  const end = `${endCol}${startRow + lines.length - 1}`;
  ws.getRange(`${start}:${end}`).values = lines.map((line) => [line]);
  for (let r = startRow; r < startRow + lines.length; r++) {
    ws.getRange(`${colLetter(startCol)}${r}:${endCol}${r}`).merge();
  }
  ws.getRange(`${start}:${end}`).format.fill.color = "#F3F6FA";
  ws.getRange(`${start}:${end}`).format.borders = { preset: "outside", style: "thin", color: "#B7C9D6" };
  ws.getRange(`${start}:${end}`).format.wrapText = true;
  ws.getRange(`${start}:${end}`).format.font.name = "Kopub World Medium";
}

const totalNews = summary.news_bridge_rows;
const topMonth = months.map((ym) => ({
  ym,
  cnt: monthly.filter((r) => r.ym === ym).reduce((acc, r) => acc + Number(r.news_bridge_rows || 0), 0),
})).sort((a, b) => b.cnt - a.cnt)[0];
const highGradeRows = monthly.filter((r) => (gradeRank[r.max_jenks_grade] ?? 0) >= 3).length;
const maxOnion = monthly
  .filter((r) => r.onion_avg_wholesale_price != null)
  .map((r) => ({ ym: r.ym, region: r.region, price: Number(r.onion_avg_wholesale_price) }))
  .sort((a, b) => b.price - a.price)[0];

const workbook = Workbook.create();

makeSheet(workbook, "작성안내", [
  ["항목", "내용"],
  ["문서명", "2022~2023년 남부지방 가뭄 사례 검증용 DB 추출자료"],
  ["작성일", "2026-08-18"],
  ["대상 사례", "CASE_SOUTH_22_23"],
  ["대상 기간", "2022-01~2023-12"],
  ["대상 지역", "전남 고흥군, 경남 합천군"],
  ["조회 DB", "AWS RDS / actual_drought_validation / 읽기 전용 조회"],
  ["포함 데이터", "뉴스 원문 및 지역·영향분야 bridge, 월별 Jenks 등급, 양파 도매가격·반입량·예측, 신선물가지수, 합천댐 보조자료"],
  ["연결 기준", "월 + 사례지역. Jenks는 SIDO 등급을 사례 시군구에 연결, 신선물가지수는 시도 단위를 연결"],
  ["주의사항", "지역·시점의 동시성 검증용 자료이며, 가뭄이 가격·발전량 변화를 직접 유발했다는 인과를 단정하지 않음"],
], [24, 120]);

makeSheet(workbook, "요약", [
  ["구분", "값", "해석"],
  ["조회 DB", summary.database, "실제 검증용 DB 기준"],
  ["테이블 수", summary.table_count, "검증 스키마 내 테이블"],
  ["뉴스 bridge 행 수", summary.news_bridge_rows, "고흥·합천 2022~2023 기간 기사-지역-분야 연결 행"],
  ["뉴스 고유 기사 수", summary.news_distinct_articles, "중복 bridge를 제외한 기사 수"],
  ["고흥/합천 뉴스", `${summary.goheung_news_rows} / ${summary.hapcheon_news_rows}`, "고흥군 13건, 합천군 12건"],
  ["지역코드 점검필요 뉴스", summary.news_region_code_anomaly_rows, "고흥군 표기이나 sigungu_code가 기대 코드와 다른 행"],
  ["최다 뉴스 월", `${topMonth.ym} (${topMonth.cnt}건)`, "월별 뉴스 반응이 가장 강한 시점"],
  ["Jenks 등급 행 수", summary.jenks_grade_rows, "전남·경남 SIDO/MONTH 기준"],
  ["경고 이상 월-지역", highGradeRows, "통합패널 최대 등급 기준"],
  ["양파 실제 월자료", summary.onion_actual_month_rows, "실제 도매가격·반입량 월 집계"],
  ["양파 예측 월자료", summary.onion_monthly_prediction_rows, "월 예측 하한·상한 및 전년대비 지표"],
  ["최고 평균 양파가격", `${maxOnion.region} ${maxOnion.ym} (${Math.round(maxOnion.price).toLocaleString()}원)`, "월평균 도매가격 기준"],
  ["신선물가지수 행 수", summary.fresh_index_rows, "전남/경남 각 24개월"],
  ["합천댐 보조 월자료", summary.hapcheon_hydro_rows, "합천군 보조자료로만 사용"],
], [28, 32, 86]);

const panelHeaders = [
  "case_id", "연월", "지역", "시도", "뉴스건수", "농업뉴스", "물공급뉴스", "지역코드점검",
  "최대Jenks등급", "최대등급분야", "농업등급", "물공급등급",
  "양파실제일수", "양파평균가격", "양파가격MoM", "양파가격YoY", "양파월반입량", "양파반입량MoM", "양파반입량YoY",
  "일예측평균가격", "일예측YoY", "월예측가격하한", "월예측가격상한", "전년평균가격", "월예측가격YoY", "가격색상",
  "월예측물량하한", "월예측물량상한", "전년평균물량", "월예측물량YoY", "물량색상",
  "신선식품지수", "신선식품MoM", "신선식품YoY", "신선채소지수", "신선채소MoM", "신선채소YoY", "신선과실지수", "신선과실MoM", "신선과실YoY",
  "합천댐실적발전량_MWh", "합천댐저수량_백만㎥", "발전량YoY", "저수량MoM"
];
const panelRows = monthly.map((r) => [
  r.case_id, r.ym, r.region, r.sido, num(r.news_bridge_rows), num(r.agriculture_news_rows), num(r.water_supply_news_rows), num(r.news_region_code_anomaly_rows),
  r.max_jenks_grade, r.max_jenks_impact, r.agriculture_jenks_grade, r.water_supply_jenks_grade,
  num(r.onion_active_days), num(r.onion_avg_wholesale_price), pct(r.onion_avg_wholesale_price_mom_rate), pct(r.onion_avg_wholesale_price_yoy_rate),
  num(r.onion_total_market_volume), pct(r.onion_total_market_volume_mom_rate), pct(r.onion_total_market_volume_yoy_rate),
  num(r.onion_avg_daily_predicted_price), pct(r.onion_daily_pred_yoy_rate), num(r.onion_predicted_price_lower_bound), num(r.onion_predicted_price_upper_bound),
  num(r.onion_prev_year_avg_price), pct(r.onion_price_rate_of_change), r.onion_price_indicator_color,
  num(r.onion_predicted_volume_lower_bound), num(r.onion_predicted_volume_upper_bound), num(r.onion_prev_year_avg_volume), pct(r.onion_volume_rate_of_change), r.onion_volume_indicator_color,
  num(r.fresh_food_index), pct(r.fresh_food_index_mom_rate), pct(r.fresh_food_index_yoy_rate), num(r.fresh_vegetable_index), pct(r.fresh_vegetable_index_mom_rate), pct(r.fresh_vegetable_index_yoy_rate),
  num(r.fresh_fruit_index), pct(r.fresh_fruit_index_mom_rate), pct(r.fresh_fruit_index_yoy_rate),
  num(r.hapcheon_dam_actual_mwh), num(r.hapcheon_dam_water_storage_mcm), pct(r.hydro_generation_yoy_rate), pct(r.water_storage_mom_rate),
]);
const panelWs = makeSheet(workbook, "월별통합패널", [panelHeaders, ...panelRows], [
  18, 11, 15, 8, 9, 9, 10, 12, 13, 13, 10, 11,
  11, 13, 11, 11, 13, 11, 11, 13, 11, 13, 13, 13, 11, 10,
  13, 13, 13, 11, 10, 12, 11, 11, 12, 11, 11, 12, 11, 11, 18, 18, 11, 11
]);
panelWs.getRange("O:P").format.numberFormat = "0.0%";
panelWs.getRange("R:S").format.numberFormat = "0.0%";
panelWs.getRange("U:U").format.numberFormat = "0.0%";
panelWs.getRange("Y:Y").format.numberFormat = "0.0%";
panelWs.getRange("AD:AD").format.numberFormat = "0.0%";
panelWs.getRange("AG:AH").format.numberFormat = "0.0%";
panelWs.getRange("AJ:AK").format.numberFormat = "0.0%";
panelWs.getRange("AM:AN").format.numberFormat = "0.0%";
panelWs.getRange("AQ:AR").format.numberFormat = "0.0%";

makeSheet(workbook, "뉴스월별집계", [
  ["연월", "지역", "시도", "시군구", "시군구코드", "지역코드점검", "영향코드", "영향분야", "bridge행", "고유기사수"],
  ...newsMonthly.map((r) => [r.ym, r.region, r.sido, r.sigungu, r.sigungu_code, r.region_code_check, r.impact_code, r.impact_name, num(r.bridge_rows), num(r.distinct_articles)]),
], [11, 15, 8, 11, 12, 16, 10, 14, 10, 11]);

makeSheet(workbook, "기사목록", [
  ["기사ID", "연월", "기사일자", "지역", "시도", "시군구", "시군구코드", "지역코드점검", "영향코드", "영향분야", "제목", "피해상세", "본문요약", "URL"],
  ...newsArticles.map((r) => [r.article_id, r.ym, r.published_at, r.region, r.sido, r.sigungu, r.sigungu_code, r.region_code_check, r.impact_code, r.impact_name, r.title, r.damage_detail_first ?? r.damage_detail, r.body_excerpt, r.link]),
], [10, 11, 12, 15, 8, 11, 12, 16, 10, 14, 48, 34, 70, 48]);

const jenksWs = makeSheet(workbook, "Jenks등급", [
  ["연월", "지역", "시도등급키", "영향코드", "영향분야", "등급", "연결메모"],
  ...jenks.map((r) => [r.ym, r.region, r.sido_grade_key, r.impact_code, r.impact_name, r.grade, r.connection_note]),
], [11, 15, 12, 10, 14, 10, 36]);
for (let i = 2; i <= jenks.length + 1; i++) {
  const grade = jenksWs.getRange(`F${i}`).values?.[0]?.[0];
  if (gradeColor[grade]) jenksWs.getRange(`F${i}`).format.fill.color = gradeColor[grade];
}

makeSheet(workbook, "양파월별실제", [
  ["연월", "지역", "위치", "품목", "품종", "일자료수", "거래일수", "시작일", "종료일", "평균도매가격", "최저가격", "최고가격", "월반입량", "평균반입량"],
  ...onionActual.map((r) => [r.ym, r.region, r.location, r.item, r.variety, num(r.daily_rows), num(r.active_days), r.first_date, r.last_date, num(r.avg_wholesale_price), num(r.min_wholesale_price), num(r.max_wholesale_price), num(r.total_market_volume), num(r.avg_market_volume)]),
], [11, 15, 9, 9, 12, 10, 10, 12, 12, 14, 12, 12, 14, 12]);

makeSheet(workbook, "양파일예측월집계", [
  ["연월", "지역", "위치", "품목", "품종", "일예측수", "예측평균가격", "예측최저가격", "예측최고가격", "전년대비율평균", "변화설명", "색상"],
  ...onionDailyPred.map((r) => [r.ym, r.region, r.location, r.item, r.variety, num(r.prediction_daily_rows), num(r.avg_daily_predicted_price), num(r.min_daily_predicted_price), num(r.max_daily_predicted_price), pct(r.avg_daily_pred_yoy_rate), r.daily_pred_change_descriptions, r.daily_pred_indicator_colors]),
], [11, 15, 9, 9, 12, 10, 14, 14, 14, 14, 28, 12]);

makeSheet(workbook, "양파월예측", [
  ["연월", "지역", "위치", "품목", "품종", "가격하한", "가격상한", "전년평균가격", "가격증감", "가격증감률", "가격색상", "물량하한", "물량상한", "전년평균물량", "물량증감", "물량증감률", "물량색상"],
  ...onionMonthlyPred.map((r) => [r.ym, r.region, r.location, r.item, r.variety, num(r.predicted_price_lower_bound), num(r.predicted_price_upper_bound), num(r.prev_year_avg_price), num(r.price_change_from_prev_year), pct(r.price_rate_of_change), r.price_indicator_color, num(r.predicted_volume_lower_bound), num(r.predicted_volume_upper_bound), num(r.prev_year_avg_volume), num(r.volume_change_from_prev_year), pct(r.volume_rate_of_change), r.volume_indicator_color]),
], [11, 15, 9, 9, 12, 12, 12, 14, 12, 12, 10, 12, 12, 14, 12, 12, 10]);

makeSheet(workbook, "신선물가지수", [
  ["연월", "지역", "시도", "총지수", "신선식품", "신선채소", "신선과실", "신선어개", "신선식품제외"],
  ...fresh.map((r) => [r.ym, r.region, r.province, num(r.total_index), num(r.fresh_food_index), num(r.fresh_vegetable_index), num(r.fresh_fruit_index), num(r.fresh_fish_index), num(r.excluding_fresh_food_index)]),
], [11, 15, 18, 11, 12, 12, 12, 12, 14]);

makeSheet(workbook, "합천댐보조", [
  ["댐명", "댐코드", "연월", "계획발전량_MWh", "실적발전량_MWh", "저수위_ELm", "저수량_백만㎥", "전년동월발전량", "발전량YoY", "전월발전량", "발전량MoM", "전월저수량", "저수량MoM"],
  ...hydro.map((r) => [r.dam_name, r.dam_code, r.ym, num(r.planned_mwh), num(r.actual_mwh), num(r.water_level_elm), num(r.water_storage_mcm), num(r.hydro_generation_last_year_month_amount), pct(r.hydro_generation_last_year_month_rate), num(r.hydro_generation_last_month_amount), pct(r.hydro_generation_last_month_rate), num(r.average_water_storage_last_month_amount), pct(r.average_water_storage_last_month_rate)]),
], [10, 13, 11, 17, 17, 12, 15, 16, 11, 14, 11, 14, 11]);

makeSheet(workbook, "DB구조", [
  ["테이블", "추정행수", "비고"],
  ...tableInfo.map((r) => [r.table_name, num(r.table_rows), ""]),
  ["", "", ""],
  ["테이블", "컬럼", "타입"],
  ...columns.map((r) => [r.table_name, r.column_name, r.data_type]),
], [34, 18, 18]);

makeSheet(workbook, "제약사항", [
  ["점검 항목", "결과", "조치 필요"],
  ["Jenks 공간단위", "시군구가 아니라 SIDO/MONTH 기준", "고흥은 전남, 합천은 경남 등급을 연결했음을 보고서에 명시"],
  ["뉴스 지역코드", "고흥군 2행은 sigungu_code가 기대 코드와 다름", "기사 자체는 보존하되 지역코드 점검필요로 표시"],
  ["양파 생산량·재배면적", "검증용 DB 농산물 테이블에서 해당 컬럼 미확인", "생산량/재배면적까지 검증하려면 별도 테이블 또는 외부 파일 확인"],
  ["신선물가지수", "시도 단위 지표", "시군구 단위 영향으로 과해석하지 않기"],
  ["합천댐 수력자료", "합천군 보조자료로 공간 적합성이 있음", "고흥군 검증에는 직접 적용하지 않기"],
  ["인과 해석", "동시성·일관성 확인 가능", "가뭄 때문에 가격이 상승했다는 표현은 사용하지 않기"],
], [26, 70, 78]);

const newsChartRows = [["연월", "고흥 뉴스", "합천 뉴스", "고흥 농업등급값", "합천 농업등급값"]];
for (const ym of months) {
  const go = getPanel("전남 고흥군", ym);
  const ha = getPanel("경남 합천군", ym);
  newsChartRows.push([ym, num(go.news_bridge_rows), num(ha.news_bridge_rows), gradeRank[go.agriculture_jenks_grade] ?? null, gradeRank[ha.agriculture_jenks_grade] ?? null]);
}
const pptNews = workbook.worksheets.add("PPT_뉴스영향");
pptNews.showGridLines = false;
setTitle(pptNews, "A1:H1", "남부 사례 뉴스 영향은 2022년 5~6월과 11월에 집중");
pptNews.getRange("A3:E27").values = newsChartRows;
pptNews.getRange("A3:E3").format.fill.color = "#D9EAF7";
pptNews.getRange("A3:E27").format.borders = { preset: "all", style: "thin", color: "#D9D9D9" };
for (let i = 0; i < 5; i++) pptNews.getRange(`${colLetter(i + 1)}:${colLetter(i + 1)}`).format.columnWidth = [11, 12, 12, 14, 14][i];
const chart1 = pptNews.charts.add("line", {
  title: "월별 뉴스 기사수(고흥·합천)",
  categories: months,
  series: [
    { name: "전남 고흥군", values: newsChartRows.slice(1).map((r) => r[1]) },
    { name: "경남 합천군", values: newsChartRows.slice(1).map((r) => r[2]) },
  ],
  hasLegend: true,
  legend: { position: "bottom" },
  from: { row: 2, col: 6 },
  extent: { widthPx: 760, heightPx: 350 },
});
chart1.yAxis = { title: "기사수", majorGridlines: { visible: true } };
writeMemo(pptNews, 23, 7, 8, [
  "핵심 읽기",
  `전체 bridge ${summary.news_bridge_rows}건: 고흥 ${summary.goheung_news_rows}건, 합천 ${summary.hapcheon_news_rows}건`,
  `최다 월: ${topMonth.ym} ${topMonth.cnt}건`,
  "고흥은 2022년 11월, 합천은 2022년 3~6월 농업 영향이 두드러짐",
  "고흥 2건은 지역코드 점검필요로 별도 표시",
]);

const onionChartRows = [["연월", "고흥 실제가격", "합천 실제가격", "고흥 예측평균", "합천 예측평균", "고흥 신선채소", "합천 신선채소"]];
for (const ym of months) {
  const go = getPanel("전남 고흥군", ym);
  const ha = getPanel("경남 합천군", ym);
  onionChartRows.push([
    ym,
    num(go.onion_avg_wholesale_price),
    num(ha.onion_avg_wholesale_price),
    num(go.onion_avg_daily_predicted_price),
    num(ha.onion_avg_daily_predicted_price),
    num(go.fresh_vegetable_index),
    num(ha.fresh_vegetable_index),
  ]);
}
const pptStructured = workbook.worksheets.add("PPT_정형데이터");
pptStructured.showGridLines = false;
setTitle(pptStructured, "A1:H1", "양파 도매가격·예측자료는 고흥·합천 월별 검증축으로 사용 가능");
pptStructured.getRange("A3:G27").values = onionChartRows;
pptStructured.getRange("A3:G3").format.fill.color = "#D9EAF7";
pptStructured.getRange("A3:G27").format.borders = { preset: "all", style: "thin", color: "#D9D9D9" };
for (let i = 0; i < 7; i++) pptStructured.getRange(`${colLetter(i + 1)}:${colLetter(i + 1)}`).format.columnWidth = [11, 13, 13, 13, 13, 12, 12][i];
const chart2 = pptStructured.charts.add("line", {
  title: "월평균 양파 도매가격(고흥·합천)",
  categories: months,
  series: [
    { name: "고흥 실제", values: onionChartRows.slice(1).map((r) => r[1]) },
    { name: "합천 실제", values: onionChartRows.slice(1).map((r) => r[2]) },
    { name: "고흥 예측", values: onionChartRows.slice(1).map((r) => r[3]) },
    { name: "합천 예측", values: onionChartRows.slice(1).map((r) => r[4]) },
  ],
  hasLegend: true,
  legend: { position: "bottom" },
  from: { row: 2, col: 8 },
  extent: { widthPx: 760, heightPx: 350 },
});
chart2.yAxis = { title: "원/kg 기준 원천 단위 확인 필요" };
writeMemo(pptStructured, 23, 9, 8, [
  "핵심 읽기",
  `양파 실제 월자료 ${summary.onion_actual_month_rows}행, 월 예측 ${summary.onion_monthly_prediction_rows}행`,
  `최고 평균가격: ${maxOnion.region} ${maxOnion.ym}`,
  "월별통합패널에서 가격/반입량의 전월 대비와 전년동월 대비를 같이 확인",
  "생산량·재배면적은 현재 검증용 DB 컬럼에서 확인되지 않음",
]);

const heatRows = [["연월", "고흥 농업", "고흥 물공급", "합천 농업", "합천 물공급", "뉴스합계", "고흥가격", "합천가격"]];
for (const ym of months) {
  const go = getPanel("전남 고흥군", ym);
  const ha = getPanel("경남 합천군", ym);
  heatRows.push([
    ym,
    go.agriculture_jenks_grade ?? "",
    go.water_supply_jenks_grade ?? "",
    ha.agriculture_jenks_grade ?? "",
    ha.water_supply_jenks_grade ?? "",
    (num(go.news_bridge_rows) ?? 0) + (num(ha.news_bridge_rows) ?? 0),
    num(go.onion_avg_wholesale_price),
    num(ha.onion_avg_wholesale_price),
  ]);
}
const pptIntegrated = workbook.worksheets.add("PPT_통합요약");
pptIntegrated.showGridLines = false;
setTitle(pptIntegrated, "A1:H1", "월별 통합 검증: 뉴스·Jenks·양파 가격을 같은 연월로 대조");
pptIntegrated.getRange("A3:H27").values = heatRows;
pptIntegrated.getRange("A3:H3").format.fill.color = "#D9EAF7";
pptIntegrated.getRange("A3:H27").format.borders = { preset: "all", style: "thin", color: "#D9D9D9" };
for (let i = 4; i <= 27; i++) {
  for (const c of ["B", "C", "D", "E"]) {
    const val = heatRows[i - 3]?.[c.charCodeAt(0) - 65];
    if (gradeColor[val]) pptIntegrated.getRange(`${c}${i}`).format.fill.color = gradeColor[val];
  }
}
for (let i = 0; i < 8; i++) pptIntegrated.getRange(`${colLetter(i + 1)}:${colLetter(i + 1)}`).format.columnWidth = [11, 12, 12, 12, 12, 10, 12, 12][i];
writeMemo(pptIntegrated, 3, 10, 8, [
  "PPT용 요약문",
  "1. 남부 사례는 고흥군·합천군 기준 뉴스, Jenks, 양파 가격/예측을 월 단위로 연결할 수 있다.",
  "2. 뉴스 영향은 농업(A2) 중심이며, 고흥은 2022년 11월, 합천은 2022년 3~6월 집중된다.",
  "3. 양파 가격·반입량은 2022년 1월~2023년 11월까지 월별 집계 가능하다.",
  "4. 신선물가지수는 전남·경남 시도 단위 보조지표, 합천댐은 합천군 보조자료로 사용한다.",
  "5. 생산량·재배면적은 현재 검증용 DB에서 미확인이다.",
]);

for (const [sheetName, range, fileName] of [
  ["PPT_뉴스영향", "A1:S28", "PPT_뉴스영향_actual_validation.png"],
  ["PPT_정형데이터", "A1:T28", "PPT_정형데이터_actual_validation.png"],
  ["PPT_통합요약", "A1:R28", "PPT_통합요약_actual_validation.png"],
  ["요약", "A1:C15", "요약_actual_validation.png"],
]) {
  const png = await workbook.render({ sheetName, range, scale: 1, format: "png" });
  await fs.writeFile(path.join(outDir, fileName), new Uint8Array(await png.arrayBuffer()));
}

const inspect = await workbook.inspect({
  kind: "table",
  sheetId: "요약",
  range: "A1:C15",
  tableMaxRows: 15,
  tableMaxCols: 3,
  maxChars: 5000,
});
console.log(inspect.ndjson);

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 200 },
  maxChars: 2000,
});
console.log(errors.ndjson);

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(outputPath);
