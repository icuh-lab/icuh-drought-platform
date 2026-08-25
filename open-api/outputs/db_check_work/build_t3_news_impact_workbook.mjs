import fs from "node:fs/promises";
import { Workbook, SpreadsheetFile } from "@oai/artifact-tool";

const inputJson =
  "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work/t3_news_impact_dataset.json";
const outputDir = "/Users/jeongseok/Desktop/9월실측가뭄워크샵";
const supportDir =
  "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work";
const outputPath = `${outputDir}/실측가뭄_T3_뉴스영향데이터_정리_v1.xlsx`;

const data = JSON.parse(await fs.readFile(inputJson, "utf8"));

const theme = {
  navy: "#1F4E79",
  blue: "#5B9BD5",
  lightBlue: "#D9EAF7",
  paleGreen: "#E2F0D9",
  paleYellow: "#FFF2CC",
  text: "#1F2937",
  border: "#B7C9D6",
};

function styleTitle(sheet, title, subtitle, colCount) {
  sheet.getRangeByIndexes(0, 0, 1, colCount).merge();
  sheet.getRange("A1").values = [[title]];
  sheet.getRange("A1").format = {
    fill: theme.navy,
    font: { bold: true, color: "#FFFFFF", size: 15 },
  };
  sheet.getRangeByIndexes(1, 0, 1, colCount).merge();
  sheet.getRange("A2").values = [[subtitle]];
  sheet.getRange("A2").format = {
    fill: theme.lightBlue,
    font: { color: theme.text, size: 10 },
    wrapText: true,
  };
}

function writeTable(sheet, values, options = {}) {
  const startRow = options.startRow ?? 2;
  const range = sheet.getRangeByIndexes(startRow, 0, values.length, values[0].length);
  range.values = values;
  range.format.wrapText = true;
  range.format.font = { color: theme.text, size: 10 };
  range.format.borders = { preset: "all", style: "thin", color: theme.border };
  sheet.getRangeByIndexes(startRow, 0, 1, values[0].length).format = {
    fill: theme.navy,
    font: { bold: true, color: "#FFFFFF", size: 10 },
  };
  if (options.widths) {
    options.widths.forEach((width, index) => {
      sheet.getRangeByIndexes(0, index, 1, 1).format.columnWidth = width;
    });
  }
  if (options.freezeRows) sheet.freezePanes.freezeRows(options.freezeRows);
  else sheet.freezePanes.freezeRows(startRow + 1);
  if (options.freezeCols) sheet.freezePanes.freezeColumns(options.freezeCols);
}

const workbook = Workbook.create();

const overview = workbook.worksheets.add("개요");
overview.showGridLines = false;
styleTitle(
  overview,
  "T3 뉴스 기반 가뭄 영향 데이터 정리",
  "22~23 남부(고흥·합천)와 2025 강릉 사례의 뉴스 기사를 월·지역·영향분야 기준으로 정리한 T3 산출물입니다.",
  5,
);
const overviewRows = [
  ["항목", "값", "설명"],
  ["전체 기사 행", data.qa["전체기사행"], "기사 단위 정규화 행 수. 강릉은 영향분야가 복수이면 분야별로 일부 explode"],
  ["남부 기사 행", data.qa["남부기사행"], "고흥·합천 재수집 결과"],
  ["강릉/강원 기사 행", data.qa["강릉기사행"], "기존 1990~2025 CSV에서 2025년 강릉/강원 조건 필터링"],
  ["강릉 핵심 기사 행", data.qa["강릉핵심기사행"], "표준지역명=강원 강릉시"],
  ["강원 관련 기사 행", data.qa["강원관련기사행"], "강릉 직접이 아닌 강원 관련지역"],
  ["월별 집계 행", data.qa["월별집계행"], "case_id·연월·표준지역명·영향분야 기준"],
  ["대표기사 행", data.qa["대표기사행"], "월별 집계 그룹마다 1건 선정"],
  ["T3의 역할", "뉴스 영향 정규화와 월별 집계", "등급 산출은 T5에서 수행"],
  ["입력 원천 1", "/Users/jeongseok/Desktop/9월실측가뭄워크샵/실측가뭄과제_drought_news_result_2022_2023_고흥합천_재수집_v1.xlsx", "남부 고흥·합천 재수집"],
  ["입력 원천 2", "/Users/jeongseok/Downloads/실측가뭄과제_drought_news_result_1990_2025_수정_2.csv", "2025 강릉/강원 기사"],
];
writeTable(overview, overviewRows, { widths: [24, 78, 72] });

const articleCols = [
  "article_key",
  "case_id",
  "사례명",
  "기사일자",
  "연도",
  "연월",
  "표준지역명",
  "시도",
  "시군구",
  "지역역할",
  "영향분야",
  "피해상세",
  "영향요약",
  "뉴스제목",
  "뉴스링크",
  "검증활용근거",
  "연결수준",
  "사용판정",
  "원천",
  "본문요약",
];
const articles = workbook.worksheets.add("기사정규화");
articles.showGridLines = false;
styleTitle(
  articles,
  "기사정규화",
  "사례·연월·지역·영향분야를 부여한 기사 단위 표입니다. T6 통합표의 뉴스 원천으로 사용합니다.",
  articleCols.length,
);
writeTable(
  articles,
  [articleCols, ...data.articles.map((row) => articleCols.map((col) => row[col] ?? ""))],
  {
    widths: [16, 24, 22, 12, 8, 10, 22, 16, 16, 12, 12, 44, 54, 58, 64, 58, 14, 14, 20, 80],
    freezeCols: 7,
  },
);
articles.getRange(`D4:D${data.articles.length + 3}`).format.numberFormat = "yyyy-mm-dd";
articles.getRange(`E4:E${data.articles.length + 3}`).format.numberFormat = "#,##0";

const monthlyCols = [
  "case_id",
  "사례명",
  "연월",
  "표준지역명",
  "지역역할",
  "영향분야",
  "기사수",
  "직접기사수",
  "사용기사수",
  "피해상세_통합",
  "대표기사제목",
  "대표기사URL",
  "대표영향요약",
  "T5등급산출대상",
];
const monthly = workbook.worksheets.add("월별영향집계");
monthly.showGridLines = false;
styleTitle(
  monthly,
  "월별영향집계",
  "T3의 핵심 산출물입니다. T5 등급 산출과 T6 통합 검증표는 이 시트를 기준으로 연결합니다.",
  monthlyCols.length,
);
writeTable(
  monthly,
  [monthlyCols, ...data.monthly.map((row) => monthlyCols.map((col) => row[col] ?? ""))],
  {
    widths: [24, 22, 10, 22, 12, 12, 10, 12, 12, 56, 58, 64, 58, 16],
    freezeCols: 6,
  },
);
monthly.getRange(`G4:I${data.monthly.length + 3}`).format.numberFormat = "#,##0";

const repCols = [
  "case_id",
  "사례명",
  "연월",
  "표준지역명",
  "영향분야",
  "대표기사제목",
  "대표기사URL",
  "대표선정근거",
  "영향요약",
];
const reps = workbook.worksheets.add("대표기사");
reps.showGridLines = false;
styleTitle(
  reps,
  "대표기사",
  "월·지역·영향분야별로 보고서 근거로 쓰기 좋은 기사 1건을 우선 선정했습니다.",
  repCols.length,
);
writeTable(
  reps,
  [repCols, ...data.representative.map((row) => repCols.map((col) => row[col] ?? ""))],
  {
    widths: [24, 22, 10, 22, 12, 58, 64, 44, 64],
    freezeCols: 5,
  },
);

const t5Cols = [
  "case_id",
  "사례명",
  "연월",
  "표준지역명",
  "영향분야",
  "기사수",
  "직접기사수",
  "사용기사수",
  "피해상세_통합",
  "등급산출_입력상태",
  "등급",
  "등급산출메모",
];
const t5Rows = data.monthly.map((row) => [
  row.case_id,
  row["사례명"],
  row["연월"],
  row["표준지역명"],
  row["영향분야"],
  row["기사수"],
  row["직접기사수"],
  row["사용기사수"],
  row["피해상세_통합"],
  row["T5등급산출대상"] === "예" ? "입력가능" : "검토필요",
  "",
  "T5에서 기준 확정 후 등급 입력",
]);
const t5 = workbook.worksheets.add("T5입력");
t5.showGridLines = false;
styleTitle(
  t5,
  "T5입력",
  "T5 뉴스 영향 등급 산출에 바로 넘길 입력표입니다. 등급 컬럼은 아직 비워두었습니다.",
  t5Cols.length,
);
writeTable(t5, [t5Cols, ...t5Rows], {
  widths: [24, 22, 10, 22, 12, 10, 12, 12, 56, 16, 12, 44],
  freezeCols: 5,
});
t5.getRange(`F4:H${t5Rows.length + 3}`).format.numberFormat = "#,##0";
t5.getRange(`K4:K${t5Rows.length + 3}`).dataValidation = {
  rule: { type: "list", values: ["", "낮음", "보통", "높음", "관심", "주의", "경고", "위험"] },
};

const statusCols = ["ID", "세부작업", "상태", "판단근거", "추가작업"];
const status = workbook.worksheets.add("T3상태");
status.showGridLines = false;
styleTitle(status, "T3상태", "체크리스트 T3 세부작업별 현재 상태 판정입니다.", statusCols.length);
writeTable(status, [statusCols, ...data.status.map((row) => statusCols.map((col) => row[col] ?? ""))], {
  widths: [10, 30, 12, 82, 52],
});
status.getRange(`C4:C${data.status.length + 3}`).dataValidation = {
  rule: { type: "list", values: ["완료", "진행 중", "미착수", "막힘"] },
};

const qaRows = [
  ["점검항목", "결과", "기준/해석"],
  ["기사정규화 행 수", data.articles.length, "전체 T3 기사 행"],
  ["월별영향집계 행 수", data.monthly.length, "case_id·연월·지역·분야 그룹 수"],
  ["대표기사 행 수", data.representative.length, "월별 집계 그룹 수와 같아야 함"],
  ["T5입력 행 수", t5Rows.length, "월별 집계 그룹 수와 같아야 함"],
  ["남부 기사 행", data.qa["남부기사행"], "고흥·합천 재수집"],
  ["강릉/강원 기사 행", data.qa["강릉기사행"], "2025 강릉/강원 필터"],
  ["강릉 핵심 기사 행", data.qa["강릉핵심기사행"], "강원 강릉시"],
  ["강원 관련 기사 행", data.qa["강원관련기사행"], "강릉 직접이 아닌 강원 관련"],
  ["주의", "등급 미산출", "T3는 기사 정규화/집계까지, T5에서 등급 산출"],
];
const qa = workbook.worksheets.add("품질점검");
qa.showGridLines = false;
styleTitle(qa, "품질점검", "T3 산출물의 기본 행 수와 연결 상태를 점검합니다.", 3);
writeTable(qa, qaRows, { widths: [26, 18, 72] });
qa.getRange("B4:B11").format.numberFormat = "#,##0";

const inspect = await workbook.inspect({
  kind: "table",
  sheetId: "월별영향집계",
  range: "A3:N12",
  include: "values,formulas",
  tableMaxRows: 10,
  tableMaxCols: 14,
  maxChars: 6000,
});
console.log(inspect.ndjson);

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "final formula error scan",
  maxChars: 3000,
});
console.log(errors.ndjson);

await fs.mkdir(outputDir, { recursive: true });
await fs.mkdir(supportDir, { recursive: true });
for (const sheetName of ["개요", "기사정규화", "월별영향집계", "대표기사", "T5입력", "T3상태", "품질점검"]) {
  const preview = await workbook.render({ sheetName, autoCrop: "all", scale: 1, format: "png" });
  await fs.writeFile(`${supportDir}/T3뉴스영향_${sheetName}_v1.png`, new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(outputPath);
