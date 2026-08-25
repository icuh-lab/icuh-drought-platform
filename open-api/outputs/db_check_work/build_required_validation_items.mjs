import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outDir = "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work";
const sourceJson = "/tmp/drought_all_spec_columns.json";
const outputPath = path.join(outDir, "실측가뭄_사례검증_필요데이터항목_v1.xlsx");

const records = JSON.parse(await fs.readFile(sourceJson, "utf8"));

const today = "2026-08-10";

function nfc(value) {
  return String(value ?? "").normalize("NFC");
}

function dataKind(fileName) {
  const f = nfc(fileName);
  if (f.includes("고랭지배추")) return "농산물 도매가격(고랭지배추)";
  if (f.includes("양파")) return "농산물 도매가격(양파)";
  if (f.includes("뉴스기반")) return "뉴스 기반 비정형 데이터";
  if (f.includes("수력발전량")) return "수력발전량/저수량";
  if (f.includes("신선물가지수")) return "신선물가지수";
  if (f.includes("산불위험지수")) return "산불위험지수";
  return "기타";
}

function caseTarget(kind, table, column) {
  if (kind.includes("양파")) return "CASE_SOUTH_22_23";
  if (kind.includes("고랭지배추")) return "CASE_GANGNEUNG_2025";
  if (kind.includes("뉴스")) return "공통";
  if (kind.includes("신선")) return "공통";
  if (kind.includes("수력")) return "보조: CASE_SOUTH_22_23 중심, 강릉 적용성 확인";
  if (kind.includes("산불")) return "보조: CASE_GANGNEUNG_2025 환경 영향 확인";
  return "공통";
}

const includeByTable = {
  impact_field: new Set(["code", "name"]),
  drought_article: new Set(["id", "published_at", "sido", "sigungu", "sigungu_code", "weather_factor", "title", "body", "link", "impact_code", "damage_detail"]),
  drought_article_region_impact: new Set(["article_id", "sido", "sigungu", "sigungu_code", "impact_code", "damage_detail", "source_order"]),
  agg_daily_region_field_count: new Set(["stat_date", "sido", "impact_code", "article_count"]),
  agg_period_grade: new Set(["granularity", "dim_type", "bucket", "dim_key", "impact_code", "grade"]),
  agg_period_grade_break: new Set(["granularity", "dim_type", "bucket", "impact_code", "grade", "lower_bound"]),
  daily_market_trends: new Set(["trend_date", "location", "item", "variety", "market_volume", "avg_wholesale_price"]),
  daily_price_predictions: new Set(["prediction_date", "location", "item", "variety", "predicted_price", "rate_of_change_from_prev_year"]),
  monthly_market_predictions: new Set([
    "location", "item", "variety", "prediction_year", "prediction_month",
    "predicted_price_lower_bound", "predicted_price_upper_bound",
    "prev_year_avg_price", "price_change_from_prev_year", "price_rate_of_change",
    "predicted_volume_lower_bound", "predicted_volume_upper_bound",
    "prev_year_avg_volume", "volume_change_from_prev_year", "volume_rate_of_change",
  ]),
  drought_impact_fresh_food_price_index: new Set(["province", "base_date", "total_index", "fresh_food_index", "fresh_vegetable_index", "fresh_fruit_index", "excluding_fresh_food_index"]),
  dam_daily_generation: new Set(["dam_name", "dam_code", "generation_date", "planned_mwh", "actual_mwh"]),
  dam_monthly_generation: new Set(["dam_name", "dam_code", "year", "month", "planned_mwh", "actual_mwh"]),
  dam_monthly_reservoir_status: new Set(["dam_name", "dam_code", "year", "month", "water_level_elm", "water_storage_mcm"]),
  dam_monthly_comparison: new Set([
    "dam_name", "dam_code", "year", "month",
    "hydro_generation_last_year_month_amount", "hydro_generation_last_year_month_rate",
    "hydro_generation_last_month_amount", "hydro_generation_last_month_rate",
    "average_water_storage_last_month_amount", "average_water_storage_last_month_rate",
  ]),
  dam_monthly_predictions: new Set(["dam_name", "dam_code", "year", "month", "predicted_power_generation_lower_bound", "predicted_power_generation_upper_bound", "predicted_water_storage_lower_bound", "predicted_water_storage_upper_bound"]),
  drought_impact_wildfire_risk_index: new Set(["analdate", "doname", "sigun", "sigucode", "maxi", "meanavg", "mini", "std"]),
};

function isSelected(rec) {
  const table = nfc(rec["테이블명"]);
  const col = nfc(rec["컬럼명"]);
  return includeByTable[table]?.has(col) ?? false;
}

function requiredLevel(kind, table, col) {
  if (kind.includes("산불")) return "보조";
  if (kind.includes("수력")) return "보조/공간적합성 확인";
  if (["id", "article_id", "code", "dam_code", "sigungu_code", "sigucode"].includes(col)) return "필수(연결키)";
  if (["published_at", "stat_date", "bucket", "trend_date", "prediction_date", "prediction_year", "prediction_month", "base_date", "generation_date", "year", "month", "analdate"].includes(col)) return "필수(시간키)";
  if (["sido", "sigungu", "province", "location", "dam_name", "doname", "sigun", "dim_key"].includes(col)) return "필수(지역키)";
  if (["impact_code", "name", "grade", "lower_bound"].includes(col)) return "필수(분야/등급)";
  return "필수";
}

function validationUse(kind, table, col) {
  if (kind.includes("뉴스")) {
    if (["published_at", "stat_date", "bucket"].includes(col)) return "기사 및 등급을 연월 기준으로 정렬";
    if (["sido", "sigungu", "sigungu_code", "dim_key"].includes(col)) return "기사/등급을 사례 지역과 연결";
    if (["impact_code", "code", "name"].includes(col)) return "뉴스 영향을 물 공급, 농업 등 분야별로 구분";
    if (col === "article_count") return "월별 기사량 원값 및 Jenks 등급 원자료";
    if (col === "grade") return "Jenks Natural Breaks 기반 뉴스 영향 등급";
    if (col === "lower_bound") return "등급 구간 재현과 보고서 근거";
    if (["title", "body", "link", "damage_detail"].includes(col)) return "대표기사와 실제 피해 근거 제시";
    return "뉴스 기반 가뭄 영향 검증";
  }
  if (kind.includes("농산물")) {
    if (["trend_date", "prediction_date", "prediction_year", "prediction_month"].includes(col)) return "가격/반입량을 일·월 단위로 집계";
    if (["location", "item", "variety"].includes(col)) return "사례 지역 및 대상 품목 필터";
    if (["avg_wholesale_price", "predicted_price", "predicted_price_lower_bound", "predicted_price_upper_bound"].includes(col)) return "실제/예측 도매가격 비교";
    if (col.includes("volume") || col === "market_volume") return "반입량 변화와 가격 변동 보조 해석";
    if (col.includes("prev_year") || col.includes("rate") || col.includes("change")) return "전년 동월/동일일 대비 변화율 검증";
    return "농산물 도매가격 기반 정형 지표 검증";
  }
  if (kind.includes("신선")) {
    if (["province", "base_date"].includes(col)) return "시도·월 기준으로 사례와 연결";
    return "신선식품·채소·과실 물가지수 변화 확인";
  }
  if (kind.includes("수력")) {
    if (["dam_name", "dam_code"].includes(col)) return "댐별 공간 적합성 및 시계열 연결";
    if (["generation_date", "year", "month"].includes(col)) return "일·월 기준 발전량/저수량 집계";
    if (col.includes("mwh") || col.includes("power_generation")) return "발전량 감소/예측 범위 보조 검증";
    if (col.includes("water") || col.includes("storage") || col.includes("level")) return "저수량·저수위 변화 보조 검증";
    if (col.includes("rate") || col.includes("amount")) return "전월/전년동월 변화량·변화율 확인";
    return "수력발전량 보조자료 검증";
  }
  if (kind.includes("산불")) {
    if (["analdate", "doname", "sigun", "sigucode"].includes(col)) return "시군구·시간 기준 산불위험지수 연결";
    return "강릉/강원 환경 분야 영향 보조 확인";
  }
  return "사례 검증 연결";
}

function rationale(kind, table, col, meaning, note) {
  if (kind.includes("뉴스")) {
    if (col === "grade") return "T3 뉴스 영향 Jenks 등급을 사례 검증의 비정형 반응지표로 직접 사용하기 위해 선택.";
    if (col === "article_count") return "기사량은 Jenks 등급 산출 원값이며, 가뭄 심화·해소 시점과 대조하는 핵심 근거다.";
    if (["title", "body", "link", "damage_detail"].includes(col)) return "등급만으로는 실제 피해 내용을 설명할 수 없으므로 대표기사와 피해상세 추적에 필요하다.";
    return "뉴스를 사례·연월·지역·영향분야 기준으로 정규화하고 등급과 원문 근거를 연결하는 데 필요하다.";
  }
  if (kind.includes("고랭지배추")) {
    return "2025년 강릉 가뭄 사례의 농산물 정형 반응지표로, 강릉·대관령 고랭지배추 가격/반입량 변화를 뉴스 등급 및 가뭄단계와 대조하기 위해 선택.";
  }
  if (kind.includes("양파")) {
    return "2022~2023년 고흥·합천 남부 사례의 농업 영향 정형 지표로, 양파 가격/반입량 및 전년 대비 변화를 뉴스 영향과 대조하기 위해 선택.";
  }
  if (kind.includes("신선")) {
    return "개별 품목 가격이 시장 단위인 한계를 보완하고, 시도 단위 신선식품·채소·과실 물가 변화를 월별로 비교하기 위해 선택.";
  }
  if (kind.includes("수력")) {
    return "댐 위치가 사례 지역과 직접 일치하지 않을 수 있어 본표 핵심 지표가 아니라 보조자료로 두고, 공간 적합성 판단 후 제한적으로 사용하기 위해 선택.";
  }
  if (kind.includes("산불")) {
    return "두 핵심 사례의 직접 검증 지표는 아니지만, 강릉/강원 환경 분야 기사와 산불위험 맥락을 보조 확인할 수 있어 보조항목으로 선택.";
  }
  return `${meaning ?? ""} ${note ?? ""}`.trim();
}

const neededHeaders = ["데이터 종류", "원천 파일", "테이블명", "컬럼명", "DB 타입", "NULL", "단위/형식", "데이터 의미", "검증 활용", "적용 사례", "필수구분", "선택 근거", "비고"];
const neededRows = records
  .filter(isSelected)
  .map((rec) => {
    const kind = dataKind(rec["파일명"]);
    const table = nfc(rec["테이블명"]);
    const col = nfc(rec["컬럼명"]);
    return [
      kind,
      nfc(rec["파일명"]),
      table,
      col,
      rec["DB 타입"] ?? "",
      rec["NULL"] ?? "",
      rec["단위/형식"] ?? "",
      rec["데이터 의미"] ?? "",
      validationUse(kind, table, col),
      caseTarget(kind, table, col),
      requiredLevel(kind, table, col),
      rationale(kind, table, col, rec["데이터 의미"], rec["비고"]),
      rec["비고"] ?? "",
    ];
  });

const selectedKey = new Set(neededRows.map((row) => `${row[1]}|${row[2]}|${row[3]}`));

function exclusionReason(kind, table, col) {
  if (["created_at", "updated_at", "collected_date"].includes(col)) return "적재/운영 관리용 시각으로 사례 검증값이나 연결키가 아님.";
  if (col === "id") return "단순 내부 식별자이며 기사-지역 bridge, 댐 코드 등 실질 연결키가 있는 경우 우선 사용.";
  if (["indicator_color", "price_indicator_color", "volume_indicator_color", "hydro_generation_last_year_month_color", "hydro_generation_last_month_color", "average_water_storage_last_month_color"].includes(col)) return "화면 표시용 색상값으로 검증 계산에는 불필요.";
  if (["change_description"].includes(col) || col.endsWith("_status")) return "표시 문구/상태값이며 원값·변화율로 재계산 가능.";
  if (["title_nouns", "body_nouns"].includes(col)) return "형태소 분석 중간산출물로 대표기사 근거에는 제목/본문/피해상세가 더 직접적.";
  if (col === "fresh_fish_index") return "이번 사례의 주요 정형 농산물 영향은 양파·고랭지배추 및 신선채소/과실 중심이므로 핵심 항목에서 제외.";
  if (["area", "regioncode", "upplocalcd", "d1", "d2", "d3", "d4"].includes(col)) return "산불위험 세부 API 원천값으로, 사례 검증에는 시군구별 최대/평균/최소 지수만 우선 사용.";
  if (table === "agg_daily_count") return "전국/전체 일별 기사수라 사례 지역·분야 검증에는 지역/분야 집계가 더 적합.";
  return "검증 본표의 사례·연월·지역·분야 연결 또는 핵심 지표 계산에 직접 사용하지 않음.";
}

const excludeHeaders = ["데이터 종류", "원천 파일", "테이블명", "컬럼명", "데이터 의미", "제외/후순위 사유", "비고"];
const excludedRows = records
  .filter((rec) => !selectedKey.has(`${nfc(rec["파일명"])}|${nfc(rec["테이블명"])}|${nfc(rec["컬럼명"])}`))
  .map((rec) => {
    const kind = dataKind(rec["파일명"]);
    const table = nfc(rec["테이블명"]);
    const col = nfc(rec["컬럼명"]);
    return [kind, nfc(rec["파일명"]), table, col, rec["데이터 의미"] ?? "", exclusionReason(kind, table, col), rec["비고"] ?? ""];
  });

const tableSummary = [
  ["데이터 종류", "테이블명", "선정 컬럼 수", "적용 사례", "선정 근거"],
];
const summaryMap = new Map();
for (const row of neededRows) {
  const key = `${row[0]}|${row[2]}`;
  if (!summaryMap.has(key)) summaryMap.set(key, { kind: row[0], table: row[2], count: 0, cases: new Set(), reasons: new Set() });
  const item = summaryMap.get(key);
  item.count += 1;
  item.cases.add(row[9]);
  item.reasons.add(row[11]);
}
for (const item of summaryMap.values()) {
  tableSummary.push([item.kind, item.table, item.count, [...item.cases].join(" / "), [...item.reasons][0]]);
}

const guideRows = [
  ["항목", "내용"],
  ["문서명", "실측가뭄 사례 검증 필요 데이터 항목 목록"],
  ["작성일", today],
  ["작성 목적", "첨부된 정형/비정형 데이터 명세서에서 2022~2023년 남부지방 가뭄과 2025년 강릉 가뭄 사례 검증에 필요한 원천 컬럼과 파생 항목을 선별한다."],
  ["검증 사례", "CASE_SOUTH_22_23: 전남 고흥군·경남 합천군 / CASE_GANGNEUNG_2025: 강원 강릉시·대관령 관련지역"],
  ["선정 원칙", "사례·연월·지역·영향분야 연결키, 실제/예측 정형값, 뉴스 등급과 원문 근거, 월별 변화율 계산에 필요한 항목을 우선 선택한다."],
  ["제외 원칙", "화면 표시 색상, 적재시각, 단순 내부 식별자, 사례 검증과 직접 연결되지 않는 중간산출물은 제외 또는 후순위로 둔다."],
  ["입력 명세서 수", "6개"],
];

const derivedHeaders = ["파생 항목", "산출 단위", "원천 테이블/컬럼", "적용 사례", "필요 사유", "산출/확인 방법"];
const derivedRows = [
  ["case_id", "사례", "사례지역마스터 또는 수동 매핑", "공통", "모든 정형/비정형 데이터를 검증 사례별로 묶는 최상위 키", "지역·기간 조건으로 CASE_SOUTH_22_23, CASE_GANGNEUNG_2025 부여"],
  ["연월", "yyyy-mm", "published_at/stat_date/bucket, trend_date/prediction_date, prediction_year+prediction_month, base_date, generation_date/year+month", "공통", "월별 통합 검증표의 기준 시간축", "일자는 월로 절삭하고 연·월 컬럼은 2자리 월로 정규화"],
  ["표준지역명", "시도+시군구 또는 산지명", "sido/sigungu, province, location, dam_name, doname/sigun", "공통", "기사·가격·물가·수력·산불 데이터를 같은 지역 체계로 연결", "T2 사례지역마스터의 표준지역명과 매핑"],
  ["영향분야명", "A1~A8 한글명", "impact_field.code/name, impact_code", "공통", "뉴스 등급과 실제 영향 내용을 분야별로 비교", "impact_code를 impact_field와 조인"],
  ["월별 뉴스 기사수", "건 또는 mention 수", "agg_daily_region_field_count.article_count, drought_article_region_impact", "공통", "Jenks 등급 산출 원값이자 비정형 반응 강도", "연월+지역+영향분야별 합산"],
  ["뉴스영향 Jenks 등급", "관심/주의/경고/위험", "agg_period_grade.grade, agg_period_grade_break.lower_bound", "공통", "T3 비정형 가뭄반응지표", "MONTH/SIDO 기준 등급을 사례 월·지역·분야에 연결"],
  ["대표기사 근거", "기사 1~N건", "drought_article.title/body/link/damage_detail", "공통", "등급이 실제 어떤 피해 보도에 기반했는지 설명", "월별·지역별·분야별 대표기사 선정"],
  ["월평균 실제 도매가격", "원/kg", "daily_market_trends.avg_wholesale_price", "양파/고랭지배추", "정형 가격 반응 시점 확인", "일별 값을 연월+location+item+variety 기준 평균"],
  ["월평균 예측 도매가격", "원/kg", "daily_price_predictions.predicted_price 또는 monthly_market_predictions 가격 하한/상한", "양파/고랭지배추", "실제 가격과 모델 예측값 비교", "일별 예측 평균 또는 월별 상·하한 중앙값/범위 사용"],
  ["가격 전년동월 변화율", "%", "monthly_market_predictions.price_rate_of_change, prev_year_avg_price", "양파/고랭지배추", "가뭄 시기 가격 상승·하락 방향 확인", "저장값 사용, 없으면 월평균 가격과 전년동월 가격으로 재계산"],
  ["월간 반입량/거래량", "톤 또는 10kg 망 수", "market_volume, predicted_volume_lower_bound/upper_bound", "양파/고랭지배추", "가격 변화가 공급 변화와 함께 나타나는지 보조 해석", "일별 합계 또는 월별 상·하한 범위 사용"],
  ["신선식품·채소·과실 물가지수 변화", "지수 및 %", "fresh_food_index, fresh_vegetable_index, fresh_fruit_index", "공통", "품목 가격 외 시도 단위 물가 반응 확인", "전월 대비 및 전년동월 대비 변화율 계산"],
  ["수력발전량/저수량 변화", "MWh, 백만㎥, %", "dam_monthly_generation, dam_monthly_reservoir_status, dam_monthly_comparison", "보조", "공간적으로 적합한 댐에 한해 수문 영향 보조 확인", "댐별 위치 적합성 검토 후 월별 변화율 사용"],
  ["산불위험지수 월별 요약", "0~100 지수", "drought_impact_wildfire_risk_index.maxi/meanavg/mini/std", "보조", "강릉/강원 환경 영향 기사와 산불위험 맥락 보조 확인", "시군구+월 기준 평균/최대값 집계"],
];

const caseHeaders = ["사례", "기간", "지역", "핵심 데이터", "필요 테이블", "검증에서의 역할", "비고"];
const caseRows = [
  ["CASE_SOUTH_22_23", "2022-01~2023-12", "전남 고흥군, 경남 합천군", "뉴스, 양파 도매가격/반입량, 신선물가지수", "drought_article*, agg_*, daily_market_trends, daily_price_predictions, monthly_market_predictions, drought_impact_fresh_food_price_index", "뉴스 영향 등급과 양파 가격/반입량·물가지수 변화의 월별 동시성 확인", "수력은 합천댐 등 공간적합성 확인 후 보조자료로만 사용"],
  ["CASE_GANGNEUNG_2025", "2025-01~2025-12", "강원 강릉시, 강원 평창군 대관령면 및 강원 관련지역", "뉴스, 고랭지배추 도매가격/반입량, 신선물가지수", "drought_article*, agg_*, daily_market_trends, daily_price_predictions, monthly_market_predictions, drought_impact_fresh_food_price_index", "뉴스 영향 등급과 고랭지배추 가격/반입량·물가지수 변화의 월별 동시성 확인", "산불위험지수와 수력발전량은 직접 검증보다 환경/수문 보조자료"],
];

function colLetter(n) {
  let s = "";
  while (n > 0) {
    const m = (n - 1) % 26;
    s = String.fromCharCode(65 + m) + s;
    n = Math.floor((n - m) / 26);
  }
  return s;
}

function writeSheet(wb, name, rows, widths) {
  const ws = wb.worksheets.add(name);
  ws.showGridLines = false;
  const rowCount = rows.length;
  const colCount = rows[0].length;
  ws.getRangeByIndexes(0, 0, rowCount, colCount).values = rows;
  const used = ws.getRangeByIndexes(0, 0, rowCount, colCount);
  used.format.font.name = "Kopub World Medium";
  used.format.font.size = 10;
  used.format.wrapText = true;
  used.format.verticalAlignment = "center";
  used.format.borders = { preset: "all", style: "thin", color: "#D9D9D9" };
  const header = ws.getRangeByIndexes(0, 0, 1, colCount);
  header.format.font.name = "Kopub World Bold";
  header.format.font.bold = true;
  header.format.fill.color = "#D9EAF7";
  header.format.horizontalAlignment = "center";
  ws.freezePanes.freezeRows(1);
  for (let i = 0; i < widths.length; i++) {
    ws.getRange(`${colLetter(i + 1)}:${colLetter(i + 1)}`).format.columnWidth = widths[i];
  }
  ws.getRangeByIndexes(1, 0, Math.max(rowCount - 1, 1), colCount).format.horizontalAlignment = "left";
  return ws;
}

const workbook = Workbook.create();
writeSheet(workbook, "작성 안내", guideRows, [24, 110]);
writeSheet(workbook, "테이블 요약", tableSummary, [28, 32, 14, 38, 95]);
writeSheet(workbook, "필요 컬럼 명세", [neededHeaders, ...neededRows], [28, 38, 36, 32, 20, 12, 20, 38, 44, 34, 18, 72, 42]);
writeSheet(workbook, "사례별 활용", [caseHeaders, ...caseRows], [24, 22, 46, 50, 72, 64, 52]);
writeSheet(workbook, "파생 항목", [derivedHeaders, ...derivedRows], [28, 22, 62, 28, 58, 64]);
writeSheet(workbook, "제외_후순위", [excludeHeaders, ...excludedRows], [28, 38, 34, 32, 42, 70, 42]);

await fs.mkdir(outDir, { recursive: true });

for (const sheetName of ["작성 안내", "테이블 요약", "필요 컬럼 명세", "파생 항목", "제외_후순위"]) {
  const preview = await workbook.render({ sheetName, range: "A1:H12", scale: 1, format: "png" });
  const bytes = new Uint8Array(await preview.arrayBuffer());
  await fs.writeFile(path.join(outDir, `preview_${sheetName}.png`), bytes);
}

const inspect = await workbook.inspect({
  kind: "table",
  sheetId: "필요 컬럼 명세",
  range: "A1:M12",
  tableMaxRows: 12,
  tableMaxCols: 13,
  maxChars: 6000,
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
