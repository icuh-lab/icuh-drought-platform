import fs from "node:fs/promises";
import path from "node:path";
import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const outDir = "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/south_case_2022_2023";

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
  const env = { ...process.env, MYSQL_PWD: "dasom123" };
  const args = ["--protocol=TCP", "-h127.0.0.1", "-P13306", "-udasom", "-D", "ACTUAL_DRGHT", "--batch", "--raw", "-e", sql];
  const { stdout } = await execFileAsync("mysql", args, { env, maxBuffer: 1024 * 1024 * 20 });
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

const newsMonthlySql = `
SELECT
  DATE_FORMAT(a.published_at, '%Y-%m') AS ym,
  CASE
    WHEN b.sido='전남' AND b.sigungu LIKE '%고흥%' THEN '전남 고흥군'
    WHEN b.sido='경남' AND b.sigungu LIKE '%합천%' THEN '경남 합천군'
  END AS region,
  b.sido,
  b.sigungu,
  b.impact_code,
  COALESCE(f.name, b.impact_code) AS impact_name,
  COUNT(*) AS article_count
FROM drought_article a
JOIN drought_article_region_impact b ON a.id = b.article_id
LEFT JOIN impact_field f ON b.impact_code = f.code
WHERE a.published_at BETWEEN '2022-01-01' AND '2023-12-31'
  AND ((b.sido='전남' AND b.sigungu LIKE '%고흥%') OR (b.sido='경남' AND b.sigungu LIKE '%합천%'))
GROUP BY ym, region, b.sido, b.sigungu, b.impact_code, impact_name
ORDER BY ym, region, b.impact_code;
`;

const newsArticlesSql = `
SELECT
  DATE_FORMAT(a.published_at, '%Y-%m') AS ym,
  a.published_at,
  CASE
    WHEN b.sido='전남' AND b.sigungu LIKE '%고흥%' THEN '전남 고흥군'
    WHEN b.sido='경남' AND b.sigungu LIKE '%합천%' THEN '경남 합천군'
  END AS region,
  b.sido,
  b.sigungu,
  b.impact_code,
  COALESCE(f.name, b.impact_code) AS impact_name,
  a.title,
  a.link,
  LEFT(REPLACE(REPLACE(COALESCE(a.body,''), '\\n', ' '), '\\r', ' '), 220) AS body_excerpt,
  JSON_UNQUOTE(JSON_EXTRACT(b.damage_detail, '$[0]')) AS damage_detail_first,
  b.damage_detail
FROM drought_article a
JOIN drought_article_region_impact b ON a.id = b.article_id
LEFT JOIN impact_field f ON b.impact_code = f.code
WHERE a.published_at BETWEEN '2022-01-01' AND '2023-12-31'
  AND ((b.sido='전남' AND b.sigungu LIKE '%고흥%') OR (b.sido='경남' AND b.sigungu LIKE '%합천%'))
ORDER BY a.published_at, region, b.impact_code, a.id;
`;

const jenksSql = `
SELECT
  bucket AS ym,
  CASE WHEN dim_key='전남' THEN '전남 고흥군' WHEN dim_key='경남' THEN '경남 합천군' END AS region,
  dim_key AS sido,
  impact_code,
  COALESCE(f.name, impact_code) AS impact_name,
  grade
FROM agg_period_grade g
LEFT JOIN impact_field f ON g.impact_code = f.code
WHERE granularity='MONTH'
  AND dim_type='SIDO'
  AND bucket BETWEEN '2022-01' AND '2023-12'
  AND dim_key IN ('전남','경남')
ORDER BY bucket, region, impact_code;
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
  c.hydro_generation_last_year_month_rate,
  c.hydro_generation_last_month_rate,
  c.average_water_storage_last_month_rate
FROM (
  SELECT dam_name, dam_code, year, month, AVG(planned_mwh) AS planned_mwh, AVG(actual_mwh) AS actual_mwh
  FROM dam_monthly_generation
  WHERE dam_name='합천'
  GROUP BY dam_name, dam_code, year, month
) g
LEFT JOIN (
  SELECT dam_name, dam_code, year, month, AVG(water_level_elm) AS water_level_elm, AVG(water_storage_mcm) AS water_storage_mcm
  FROM dam_monthly_reservoir_status
  WHERE dam_name='합천'
  GROUP BY dam_name, dam_code, year, month
) r
  ON g.dam_code = r.dam_code AND g.year = r.year AND g.month = r.month
LEFT JOIN (
  SELECT dam_name, dam_code, year, month,
         AVG(hydro_generation_last_year_month_rate) AS hydro_generation_last_year_month_rate,
         AVG(hydro_generation_last_month_rate) AS hydro_generation_last_month_rate,
         AVG(average_water_storage_last_month_rate) AS average_water_storage_last_month_rate
  FROM dam_monthly_comparison
  WHERE dam_name='합천'
  GROUP BY dam_name, dam_code, year, month
) c
  ON g.dam_code = c.dam_code AND g.year = c.year AND g.month = c.month
WHERE CONCAT(g.year, '-', LPAD(g.month,2,'0')) BETWEEN '2022-01' AND '2023-12'
ORDER BY ym;
`;

const onionCheckSql = `
SELECT 'daily_market_trends' AS table_name, COUNT(*) AS rows_cnt FROM daily_market_trends WHERE item LIKE '%양파%' OR variety LIKE '%양파%'
UNION ALL
SELECT 'daily_price_predictions' AS table_name, COUNT(*) AS rows_cnt FROM daily_price_predictions WHERE item LIKE '%양파%' OR variety LIKE '%양파%'
UNION ALL
SELECT 'monthly_market_predictions' AS table_name, COUNT(*) AS rows_cnt FROM monthly_market_predictions WHERE item LIKE '%양파%' OR variety LIKE '%양파%';
`;

const tableInfoSql = `
SELECT 'drought_article' AS table_name, COUNT(*) AS rows_cnt, CAST(MIN(published_at) AS CHAR) AS min_date, CAST(MAX(published_at) AS CHAR) AS max_date FROM drought_article
UNION ALL
SELECT 'drought_article_region_impact' AS table_name, COUNT(*) AS rows_cnt, CAST(NULL AS CHAR), CAST(NULL AS CHAR) FROM drought_article_region_impact
UNION ALL
SELECT 'agg_period_grade' AS table_name, COUNT(*) AS rows_cnt, CAST(MIN(bucket) AS CHAR), CAST(MAX(bucket) AS CHAR) FROM agg_period_grade
UNION ALL
SELECT 'fresh_food_price_index' AS table_name, COUNT(*) AS rows_cnt, CAST(MIN(base_date) AS CHAR), CAST(MAX(base_date) AS CHAR) FROM drought_impact_fresh_food_price_index
UNION ALL
SELECT 'dam_monthly_generation' AS table_name, COUNT(*) AS rows_cnt, CAST(MIN(CONCAT(year,'-',LPAD(month,2,'0'))) AS CHAR), CAST(MAX(CONCAT(year,'-',LPAD(month,2,'0'))) AS CHAR) FROM dam_monthly_generation;
`;

const months = await query("months", monthsSql);
const newsMonthly = await query("news_monthly", newsMonthlySql);
const newsArticles = await query("news_articles", newsArticlesSql);
const jenks = await query("jenks_grades", jenksSql);
const fresh = await query("fresh_price_index", freshSql);
const hydro = await query("hapcheon_hydro", hydroSql);
const onionCheck = await query("onion_db_check", onionCheckSql);
const tableInfo = await query("table_info", tableInfoSql);

const gradeRank = { "관심": 1, "주의": 2, "경고": 3, "위험": 4 };
const regions = [
  { region: "전남 고흥군", sidoShort: "전남", province: "전라남도" },
  { region: "경남 합천군", sidoShort: "경남", province: "경상남도" },
];

const monthly = [];
const byKey = (rows, keyFn) => {
  const m = new Map();
  for (const row of rows) {
    const key = keyFn(row);
    if (!m.has(key)) m.set(key, []);
    m.get(key).push(row);
  }
  return m;
};
const newsMap = byKey(newsMonthly, (r) => `${r.ym}|${r.region}`);
const jenksMap = byKey(jenks, (r) => `${r.ym}|${r.region}`);
const freshMap = new Map(fresh.map((r) => [`${r.ym}|${r.region}`, r]));
const hydroMap = new Map(hydro.map((r) => [r.ym, r]));

for (const m of months) {
  for (const region of regions) {
    const key = `${m.ym}|${region.region}`;
    const nrows = newsMap.get(key) ?? [];
    const grows = jenksMap.get(key) ?? [];
    const newsTotal = nrows.reduce((acc, r) => acc + Number(r.article_count ?? 0), 0);
    const agricultureNews = nrows.filter((r) => r.impact_code === "A2").reduce((acc, r) => acc + Number(r.article_count ?? 0), 0);
    const waterNews = nrows.filter((r) => r.impact_code === "A1").reduce((acc, r) => acc + Number(r.article_count ?? 0), 0);
    const maxGradeRow = grows.reduce((best, r) => !best || (gradeRank[r.grade] ?? 0) > (gradeRank[best.grade] ?? 0) ? r : best, null);
    const agGrade = grows.find((r) => r.impact_code === "A2")?.grade ?? null;
    const waterGrade = grows.find((r) => r.impact_code === "A1")?.grade ?? null;
    const f = freshMap.get(key) ?? {};
    const h = region.region === "경남 합천군" ? (hydroMap.get(m.ym) ?? {}) : {};
    monthly.push({
      case_id: "CASE_SOUTH_22_23",
      ym: m.ym,
      region: region.region,
      sido: region.sidoShort,
      news_article_count: newsTotal,
      agriculture_article_count: agricultureNews,
      water_article_count: waterNews,
      max_jenks_grade: maxGradeRow?.grade ?? null,
      max_jenks_impact: maxGradeRow?.impact_name ?? null,
      agriculture_jenks_grade: agGrade,
      water_supply_jenks_grade: waterGrade,
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

// Add month-over-month and YoY rates for fresh indices by region.
const monthlyByRegion = byKey(monthly, (r) => r.region);
for (const rows of monthlyByRegion.values()) {
  rows.sort((a, b) => a.ym.localeCompare(b.ym));
  const byYm = new Map(rows.map((r) => [r.ym, r]));
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const prev = rows[i - 1];
    const prevYear = byYm.get(`${Number(r.ym.slice(0, 4)) - 1}-${r.ym.slice(5, 7)}`);
    for (const col of ["fresh_food_index", "fresh_vegetable_index", "fresh_fruit_index"]) {
      const v = Number(r[col]);
      const p = prev ? Number(prev[col]) : NaN;
      const y = prevYear ? Number(prevYear[col]) : NaN;
      r[`${col}_mom_rate`] = Number.isFinite(v) && Number.isFinite(p) && p !== 0 ? ((v - p) / p * 100).toFixed(2) : null;
      r[`${col}_yoy_rate`] = Number.isFinite(v) && Number.isFinite(y) && y !== 0 ? ((v - y) / y * 100).toFixed(2) : null;
    }
  }
}

const summary = {
  generated_at: new Date().toISOString(),
  case_id: "CASE_SOUTH_22_23",
  period: "2022-01~2023-12",
  regions: regions.map((r) => r.region),
  table_info: tableInfo,
  counts: {
    news_articles_bridge_rows: newsArticles.length,
    news_monthly_rows: newsMonthly.length,
    jenks_grade_rows: jenks.length,
    fresh_index_rows: fresh.length,
    hapcheon_hydro_rows: hydro.length,
    monthly_panel_rows: monthly.length,
  },
  db_limitations: {
    onion_rows_in_standard_market_tables: onionCheck,
    note: "RDS 표준 가격 테이블(daily_market_trends/daily_price_predictions/monthly_market_predictions)에서 양파 행은 0건으로 확인됨.",
  },
};

await fs.writeFile(path.join(outDir, "monthly_panel.json"), JSON.stringify(monthly, null, 2));
await fs.writeFile(path.join(outDir, "summary.json"), JSON.stringify(summary, null, 2));
console.log(JSON.stringify(summary, null, 2));
