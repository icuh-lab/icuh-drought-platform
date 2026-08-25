import fs from "node:fs/promises";
import path from "node:path";
import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const outDir = "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/south_case_2022_2023_validation";

const db = {
  host: process.env.DB_HOST ?? "127.0.0.1",
  port: process.env.DB_PORT ?? "13307",
  user: process.env.DB_USER ?? "icuh",
  password: process.env.DB_PASSWORD,
  name: process.env.DB_NAME ?? "actual_drought_validation",
};

if (!db.password) {
  throw new Error("DB_PASSWORD environment variable is required.");
}

function parseTsv(text) {
  const lines = text.trimEnd().split(/\r?\n/);
  if (lines.length === 0 || !lines[0]) return [];
  const headers = lines[0].split("\t");
  return lines.slice(1).filter(Boolean).map((line) => {
    const values = line.split("\t");
    return Object.fromEntries(headers.map((h, i) => [h, values[i] === "\\N" ? null : values[i] ?? ""]));
  });
}

async function query(name, sql) {
  const env = { ...process.env, MYSQL_PWD: db.password };
  const args = [
    "--protocol=TCP",
    `-h${db.host}`,
    `-P${db.port}`,
    `-u${db.user}`,
    "-D",
    db.name,
    "--batch",
    "--raw",
    "-e",
    sql,
  ];
  const { stdout } = await execFileAsync("mysql", args, { env, maxBuffer: 1024 * 1024 * 80 });
  const rows = parseTsv(stdout);
  await fs.writeFile(path.join(outDir, `${name}.json`), JSON.stringify(rows, null, 2));
  return rows;
}

await fs.mkdir(outDir, { recursive: true });

const monthsSql = `
WITH RECURSIVE months AS (
  SELECT DATE('2022-01-01') AS month_start
  UNION ALL
  SELECT DATE_ADD(month_start, INTERVAL 1 MONTH)
  FROM months
  WHERE month_start < '2023-12-01'
)
SELECT DATE_FORMAT(month_start, '%Y-%m') AS ym FROM months;
`;

const tableInfoSql = `
SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema='actual_drought_validation'
ORDER BY table_name;
`;

const columnsSql = `
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema='actual_drought_validation'
ORDER BY table_name, ordinal_position;
`;

const newsMonthlySql = `
SELECT
  DATE_FORMAT(a.published_at, '%Y-%m') AS ym,
  CASE
    WHEN ri.sido='전남' AND ri.sigungu='고흥군' THEN '전남 고흥군'
    WHEN ri.sido='경남' AND ri.sigungu='합천군' THEN '경남 합천군'
  END AS region,
  ri.sido,
  ri.sigungu,
  ri.sigungu_code,
  CASE
    WHEN ri.sido='전남' AND ri.sigungu='고흥군' AND ri.sigungu_code <> '46770' THEN '지역코드 점검필요'
    WHEN ri.sido='경남' AND ri.sigungu='합천군' AND ri.sigungu_code <> '48890' THEN '지역코드 점검필요'
    ELSE '정상'
  END AS region_code_check,
  ri.impact_code,
  COALESCE(f.name, ri.impact_code) AS impact_name,
  COUNT(*) AS bridge_rows,
  COUNT(DISTINCT a.id) AS distinct_articles
FROM drought_article a
JOIN drought_article_region_impact ri ON ri.article_id=a.id
LEFT JOIN impact_field f ON f.code=ri.impact_code
WHERE a.published_at BETWEEN '2022-01-01' AND '2023-12-31'
  AND ((ri.sido='전남' AND ri.sigungu='고흥군') OR (ri.sido='경남' AND ri.sigungu='합천군'))
GROUP BY ym, region, ri.sido, ri.sigungu, ri.sigungu_code, region_code_check, ri.impact_code, impact_name
ORDER BY ym, region, ri.impact_code, ri.sigungu_code;
`;

const newsArticlesSql = `
SELECT
  a.id AS article_id,
  DATE_FORMAT(a.published_at, '%Y-%m') AS ym,
  a.published_at,
  CASE
    WHEN ri.sido='전남' AND ri.sigungu='고흥군' THEN '전남 고흥군'
    WHEN ri.sido='경남' AND ri.sigungu='합천군' THEN '경남 합천군'
  END AS region,
  ri.sido,
  ri.sigungu,
  ri.sigungu_code,
  CASE
    WHEN ri.sido='전남' AND ri.sigungu='고흥군' AND ri.sigungu_code <> '46770' THEN '지역코드 점검필요'
    WHEN ri.sido='경남' AND ri.sigungu='합천군' AND ri.sigungu_code <> '48890' THEN '지역코드 점검필요'
    ELSE '정상'
  END AS region_code_check,
  ri.impact_code,
  COALESCE(f.name, ri.impact_code) AS impact_name,
  a.title,
  LEFT(REPLACE(REPLACE(COALESCE(a.body,''), '\\n', ' '), '\\r', ' '), 260) AS body_excerpt,
  JSON_UNQUOTE(JSON_EXTRACT(ri.damage_detail, '$[0]')) AS damage_detail_first,
  ri.damage_detail,
  a.link
FROM drought_article a
JOIN drought_article_region_impact ri ON ri.article_id=a.id
LEFT JOIN impact_field f ON f.code=ri.impact_code
WHERE a.published_at BETWEEN '2022-01-01' AND '2023-12-31'
  AND ((ri.sido='전남' AND ri.sigungu='고흥군') OR (ri.sido='경남' AND ri.sigungu='합천군'))
ORDER BY a.published_at, region, ri.impact_code, a.id, ri.source_order;
`;

const jenksSql = `
SELECT
  bucket AS ym,
  CASE WHEN dim_key='전남' THEN '전남 고흥군' WHEN dim_key='경남' THEN '경남 합천군' END AS region,
  dim_key AS sido_grade_key,
  impact_code,
  COALESCE(f.name, impact_code) AS impact_name,
  grade,
  'SIDO 등급을 사례 시군구에 연결' AS connection_note
FROM agg_period_grade g
LEFT JOIN impact_field f ON f.code=g.impact_code
WHERE granularity='MONTH'
  AND dim_type='SIDO'
  AND bucket BETWEEN '2022-01' AND '2023-12'
  AND dim_key IN ('전남','경남')
ORDER BY bucket, region, impact_code;
`;

const onionActualSql = `
SELECT
  DATE_FORMAT(trend_date, '%Y-%m') AS ym,
  CASE WHEN location='고흥' THEN '전남 고흥군' WHEN location='합천' THEN '경남 합천군' END AS region,
  location,
  item,
  variety,
  COUNT(*) AS daily_rows,
  COUNT(DISTINCT trend_date) AS active_days,
  MIN(trend_date) AS first_date,
  MAX(trend_date) AS last_date,
  AVG(avg_wholesale_price) AS avg_wholesale_price,
  MIN(avg_wholesale_price) AS min_wholesale_price,
  MAX(avg_wholesale_price) AS max_wholesale_price,
  SUM(market_volume) AS total_market_volume,
  AVG(market_volume) AS avg_market_volume
FROM daily_market_trends
WHERE trend_date BETWEEN '2022-01-01' AND '2023-12-31'
  AND item='양파'
  AND location IN ('고흥','합천')
GROUP BY ym, region, location, item, variety
ORDER BY ym, region;
`;

const onionDailyPredSql = `
SELECT
  DATE_FORMAT(prediction_date, '%Y-%m') AS ym,
  CASE WHEN location='고흥' THEN '전남 고흥군' WHEN location='합천' THEN '경남 합천군' END AS region,
  location,
  item,
  variety,
  COUNT(*) AS prediction_daily_rows,
  AVG(predicted_price) AS avg_daily_predicted_price,
  MIN(predicted_price) AS min_daily_predicted_price,
  MAX(predicted_price) AS max_daily_predicted_price,
  AVG(rate_of_change_from_prev_year) AS avg_daily_pred_yoy_rate,
  GROUP_CONCAT(DISTINCT change_description ORDER BY change_description SEPARATOR ', ') AS daily_pred_change_descriptions,
  GROUP_CONCAT(DISTINCT indicator_color ORDER BY indicator_color SEPARATOR ', ') AS daily_pred_indicator_colors
FROM daily_price_predictions
WHERE prediction_date BETWEEN '2022-01-01' AND '2023-12-31'
  AND item='양파'
  AND location IN ('고흥','합천')
GROUP BY ym, region, location, item, variety
ORDER BY ym, region;
`;

const onionMonthlyPredSql = `
SELECT
  CONCAT(prediction_year, '-', LPAD(prediction_month,2,'0')) AS ym,
  CASE WHEN location='고흥' THEN '전남 고흥군' WHEN location='합천' THEN '경남 합천군' END AS region,
  location,
  item,
  variety,
  predicted_price_lower_bound,
  predicted_price_upper_bound,
  prev_year_avg_price,
  price_change_from_prev_year,
  price_rate_of_change,
  price_indicator_color,
  predicted_volume_lower_bound,
  predicted_volume_upper_bound,
  prev_year_avg_volume,
  volume_change_from_prev_year,
  volume_rate_of_change,
  volume_indicator_color
FROM monthly_market_predictions
WHERE prediction_year IN ('2022','2023')
  AND item='양파'
  AND location IN ('고흥','합천')
ORDER BY ym, region;
`;

const freshSql = `
SELECT
  DATE_FORMAT(base_date, '%Y-%m') AS ym,
  CASE WHEN province='전라남도' THEN '전남 고흥군' WHEN province='경상남도' THEN '경남 합천군' END AS region,
  province,
  total_index,
  fresh_food_index,
  fresh_vegetable_index,
  fresh_fruit_index,
  fresh_fish_index,
  excluding_fresh_food_index
FROM drought_impact_fresh_food_price_index
WHERE province IN ('전라남도','경상남도')
  AND base_date BETWEEN '2022-01-01' AND '2023-12-01'
ORDER BY base_date, province;
`;

const hydroSql = `
SELECT
  g.dam_name,
  g.dam_code,
  CONCAT(g.year, '-', LPAD(g.month,2,'0')) AS ym,
  g.planned_mwh,
  g.actual_mwh,
  r.water_level_elm,
  r.water_storage_mcm,
  c.hydro_generation_last_year_month_amount,
  c.hydro_generation_last_year_month_rate,
  c.hydro_generation_last_month_amount,
  c.hydro_generation_last_month_rate,
  c.average_water_storage_last_month_amount,
  c.average_water_storage_last_month_rate
FROM dam_monthly_generation g
LEFT JOIN dam_monthly_reservoir_status r
  ON r.dam_code=g.dam_code AND r.year=g.year AND r.month=g.month
LEFT JOIN dam_monthly_comparison c
  ON c.dam_code=g.dam_code AND c.year=g.year AND c.month=g.month
WHERE g.dam_name='합천'
  AND CONCAT(g.year, '-', LPAD(g.month,2,'0')) BETWEEN '2022-01' AND '2023-12'
ORDER BY ym;
`;

const months = await query("months", monthsSql);
const tableInfo = await query("table_info", tableInfoSql);
const columns = await query("columns", columnsSql);
const newsMonthly = await query("news_monthly", newsMonthlySql);
const newsArticles = await query("news_articles", newsArticlesSql);
const jenks = await query("jenks_grades", jenksSql);
const onionActual = await query("onion_actual_monthly", onionActualSql);
const onionDailyPred = await query("onion_daily_prediction_monthly", onionDailyPredSql);
const onionMonthlyPred = await query("onion_monthly_predictions", onionMonthlyPredSql);
const fresh = await query("fresh_price_index", freshSql);
const hydro = await query("hapcheon_hydro", hydroSql);

const gradeRank = { "관심": 1, "주의": 2, "경고": 3, "위험": 4 };
const regions = [
  { region: "전남 고흥군", sidoShort: "전남", province: "전라남도", onionLocation: "고흥" },
  { region: "경남 합천군", sidoShort: "경남", province: "경상남도", onionLocation: "합천" },
];

function byKey(rows, keyFn) {
  const m = new Map();
  for (const row of rows) {
    const key = keyFn(row);
    if (!m.has(key)) m.set(key, []);
    m.get(key).push(row);
  }
  return m;
}

function oneByKey(rows, keyFn) {
  return new Map(rows.map((r) => [keyFn(r), r]));
}

function toNumber(v) {
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function rate(v, base) {
  const n = toNumber(v);
  const b = toNumber(base);
  if (n == null || b == null || b === 0) return null;
  return ((n - b) / b * 100).toFixed(2);
}

const newsMap = byKey(newsMonthly, (r) => `${r.ym}|${r.region}`);
const jenksMap = byKey(jenks, (r) => `${r.ym}|${r.region}`);
const onionActualMap = oneByKey(onionActual, (r) => `${r.ym}|${r.region}`);
const onionDailyPredMap = oneByKey(onionDailyPred, (r) => `${r.ym}|${r.region}`);
const onionMonthlyPredMap = oneByKey(onionMonthlyPred, (r) => `${r.ym}|${r.region}`);
const freshMap = oneByKey(fresh, (r) => `${r.ym}|${r.region}`);
const hydroMap = oneByKey(hydro, (r) => r.ym);

const monthly = [];
for (const m of months) {
  for (const region of regions) {
    const key = `${m.ym}|${region.region}`;
    const nrows = newsMap.get(key) ?? [];
    const grows = jenksMap.get(key) ?? [];
    const actual = onionActualMap.get(key) ?? {};
    const dailyPred = onionDailyPredMap.get(key) ?? {};
    const monthPred = onionMonthlyPredMap.get(key) ?? {};
    const f = freshMap.get(key) ?? {};
    const h = region.region === "경남 합천군" ? (hydroMap.get(m.ym) ?? {}) : {};

    const newsTotal = nrows.reduce((acc, r) => acc + Number(r.bridge_rows ?? 0), 0);
    const agricultureNews = nrows.filter((r) => r.impact_code === "A2").reduce((acc, r) => acc + Number(r.bridge_rows ?? 0), 0);
    const waterNews = nrows.filter((r) => r.impact_code === "A1").reduce((acc, r) => acc + Number(r.bridge_rows ?? 0), 0);
    const codeAnomaly = nrows.filter((r) => r.region_code_check !== "정상").reduce((acc, r) => acc + Number(r.bridge_rows ?? 0), 0);
    const maxGradeRow = grows.reduce((best, r) => !best || (gradeRank[r.grade] ?? 0) > (gradeRank[best.grade] ?? 0) ? r : best, null);

    monthly.push({
      case_id: "CASE_SOUTH_22_23",
      ym: m.ym,
      region: region.region,
      sido: region.sidoShort,
      news_bridge_rows: newsTotal,
      agriculture_news_rows: agricultureNews,
      water_supply_news_rows: waterNews,
      news_region_code_anomaly_rows: codeAnomaly,
      max_jenks_grade: maxGradeRow?.grade ?? null,
      max_jenks_impact: maxGradeRow?.impact_name ?? null,
      agriculture_jenks_grade: grows.find((r) => r.impact_code === "A2")?.grade ?? null,
      water_supply_jenks_grade: grows.find((r) => r.impact_code === "A1")?.grade ?? null,
      onion_daily_rows: actual.daily_rows ?? null,
      onion_active_days: actual.active_days ?? null,
      onion_avg_wholesale_price: actual.avg_wholesale_price ?? null,
      onion_min_wholesale_price: actual.min_wholesale_price ?? null,
      onion_max_wholesale_price: actual.max_wholesale_price ?? null,
      onion_total_market_volume: actual.total_market_volume ?? null,
      onion_avg_market_volume: actual.avg_market_volume ?? null,
      onion_avg_daily_predicted_price: dailyPred.avg_daily_predicted_price ?? null,
      onion_daily_pred_yoy_rate: dailyPred.avg_daily_pred_yoy_rate ?? null,
      onion_predicted_price_lower_bound: monthPred.predicted_price_lower_bound ?? null,
      onion_predicted_price_upper_bound: monthPred.predicted_price_upper_bound ?? null,
      onion_prev_year_avg_price: monthPred.prev_year_avg_price ?? null,
      onion_price_rate_of_change: monthPred.price_rate_of_change ?? null,
      onion_price_indicator_color: monthPred.price_indicator_color ?? null,
      onion_predicted_volume_lower_bound: monthPred.predicted_volume_lower_bound ?? null,
      onion_predicted_volume_upper_bound: monthPred.predicted_volume_upper_bound ?? null,
      onion_prev_year_avg_volume: monthPred.prev_year_avg_volume ?? null,
      onion_volume_rate_of_change: monthPred.volume_rate_of_change ?? null,
      onion_volume_indicator_color: monthPred.volume_indicator_color ?? null,
      fresh_food_index: f.fresh_food_index ?? null,
      fresh_vegetable_index: f.fresh_vegetable_index ?? null,
      fresh_fruit_index: f.fresh_fruit_index ?? null,
      total_index: f.total_index ?? null,
      hapcheon_dam_actual_mwh: h.actual_mwh ?? null,
      hapcheon_dam_water_storage_mcm: h.water_storage_mcm ?? null,
      hydro_generation_yoy_rate: h.hydro_generation_last_year_month_rate ?? null,
      water_storage_mom_rate: h.average_water_storage_last_month_rate ?? null,
    });
  }
}

for (const rows of byKey(monthly, (r) => r.region).values()) {
  rows.sort((a, b) => a.ym.localeCompare(b.ym));
  const byYm = new Map(rows.map((r) => [r.ym, r]));
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const prev = rows[i - 1];
    const prevYear = byYm.get(`${Number(r.ym.slice(0, 4)) - 1}-${r.ym.slice(5, 7)}`);
    for (const col of ["onion_avg_wholesale_price", "onion_total_market_volume", "fresh_food_index", "fresh_vegetable_index", "fresh_fruit_index"]) {
      r[`${col}_mom_rate`] = prev ? rate(r[col], prev[col]) : null;
      r[`${col}_yoy_rate`] = prevYear ? rate(r[col], prevYear[col]) : null;
    }
  }
}

await fs.writeFile(path.join(outDir, "monthly_panel.json"), JSON.stringify(monthly, null, 2));

const summary = {
  database: "actual_drought_validation",
  case_id: "CASE_SOUTH_22_23",
  period: "2022-01~2023-12",
  regions: regions.map((r) => r.region),
  table_count: tableInfo.length,
  news_bridge_rows: newsArticles.length,
  news_distinct_articles: new Set(newsArticles.map((r) => r.article_id)).size,
  goheung_news_rows: newsArticles.filter((r) => r.region === "전남 고흥군").length,
  hapcheon_news_rows: newsArticles.filter((r) => r.region === "경남 합천군").length,
  news_region_code_anomaly_rows: newsArticles.filter((r) => r.region_code_check !== "정상").length,
  jenks_grade_rows: jenks.length,
  onion_actual_month_rows: onionActual.length,
  onion_daily_prediction_month_rows: onionDailyPred.length,
  onion_monthly_prediction_rows: onionMonthlyPred.length,
  fresh_index_rows: fresh.length,
  hapcheon_hydro_rows: hydro.length,
  monthly_panel_rows: monthly.length,
  data_limitations: [
    "Jenks 등급은 시군구가 아니라 SIDO/MONTH 기준으로 저장되어 전남 등급을 고흥군에, 경남 등급을 합천군에 연결했다.",
    "농산물 DB에는 양파 가격/반입량과 예측값이 있으나 생산량·재배면적 컬럼은 확인되지 않았다.",
    "신선물가지수는 시도 단위이며 시군구 단위 지표가 아니다.",
    "합천댐 수력자료는 합천군 보조자료로 사용할 수 있으나 고흥군과 직접 공간 연결하지 않는다.",
    "고흥군 뉴스 bridge 중 일부는 sigungu_code가 기대 코드(46770)와 달라 지역코드 점검필요로 표시했다."
  ],
};
await fs.writeFile(path.join(outDir, "summary.json"), JSON.stringify(summary, null, 2));

console.log(JSON.stringify(summary, null, 2));
