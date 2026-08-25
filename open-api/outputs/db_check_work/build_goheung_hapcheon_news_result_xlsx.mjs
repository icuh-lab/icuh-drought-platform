import fs from "node:fs/promises";
import { Workbook, SpreadsheetFile } from "@oai/artifact-tool";

const inputJson =
  "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work/goheung_hapcheon_news_result_rows.json";
const outputDir = "/Users/jeongseok/Desktop/9월실측가뭄워크샵";
const supportDir =
  "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work";
const outputPath = `${outputDir}/실측가뭄과제_drought_news_result_2022_2023_고흥합천_재수집_v1.xlsx`;

const theme = {
  navy: "#1F4E79",
  lightBlue: "#D9EAF7",
  border: "#B7C9D6",
  text: "#1F2937",
};

const payload = JSON.parse(await fs.readFile(inputJson, "utf8"));
const rows = payload.rows;
const statusRows = payload.status;

const csvColumns = [
  "일자",
  "광역시도",
  "시군구",
  "뉴스링크",
  "뉴스제목",
  "뉴스제목_명사",
  "기사본문",
  "기사본문_명사",
  "기상요소",
  "시군구코드",
  "영향구분",
  "피해상세",
];

function writeTable(sheet, values, options = {}) {
  const startRow = options.startRow ?? 0;
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
  sheet.freezePanes.freezeRows(startRow + 1);
}

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

const workbook = Workbook.create();

const result = workbook.worksheets.add("drought_news_result");
result.showGridLines = false;
writeTable(
  result,
  [csvColumns, ...rows.map((row) => csvColumns.map((col) => row[col] ?? ""))],
  {
    widths: [12, 18, 16, 62, 54, 42, 96, 72, 12, 14, 12, 34],
  },
);
result.getRange(`A2:A${rows.length + 1}`).format.numberFormat = "yyyy-mm-dd";

const status = workbook.worksheets.add("URL_수집상태");
status.showGridLines = false;
styleTitle(
  status,
  "URL 수집상태",
  "후보기사 URL에서 제목/본문을 가져온 결과입니다. 본문 수집이 막히거나 깨진 경우 후보 목록의 영향요약을 fallback으로 사용했습니다.",
  11,
);
const statusColumns = [
  "article_id",
  "URL",
  "수집성공",
  "HTTP상태",
  "본문fallback사용",
  "본문길이",
  "오류",
  "원래제목",
  "최종제목",
  "사용판정",
  "연결수준",
];
writeTable(
  status,
  [statusColumns, ...statusRows.map((row) => statusColumns.map((col) => row[col] ?? ""))],
  {
    startRow: 2,
    widths: [24, 66, 12, 12, 16, 12, 32, 54, 54, 14, 12],
  },
);

const summary = workbook.worksheets.add("월별집계");
summary.showGridLines = false;
styleTitle(
  summary,
  "월별집계",
  "실측가뭄 CSV 형식으로 만든 행을 연월·지역·영향구분 기준으로 요약했습니다.",
  8,
);
const grouped = new Map();
for (const row of rows) {
  const ym = String(row["일자"] ?? "").slice(0, 7);
  const region = String(row["시군구"] ?? "");
  const impact = String(row["영향구분"] ?? "");
  const key = `${ym}\t${region}\t${impact}`;
  grouped.set(key, (grouped.get(key) ?? 0) + 1);
}
const summaryRows = [...grouped.entries()]
  .map(([key, count]) => {
    const [ym, region, impact] = key.split("\t");
    return [ym, region, impact, count, "", ""];
  })
  .sort((a, b) => String(a[0]).localeCompare(String(b[0])) || String(a[1]).localeCompare(String(b[1])));
writeTable(summary, [["연월", "시군구", "영향구분", "기사수", "대표 영향", "비고"], ...summaryRows], {
  startRow: 2,
  widths: [12, 18, 12, 10, 72, 44],
});
summary.getRange(`D4:D${summaryRows.length + 3}`).format.numberFormat = "#,##0";

const guide = workbook.worksheets.add("컬럼설명");
guide.showGridLines = false;
styleTitle(
  guide,
  "컬럼설명",
  "기존 실측가뭄과제 drought_news_result CSV 형식에 맞춘 컬럼 정의입니다.",
  4,
);
const guideRows = [
  ["컬럼", "작성 방식", "비고"],
  ["일자", "기사일자 또는 페이지 메타 published_time", "yyyy-mm-dd"],
  ["광역시도", "표준지역명 기준 리스트 문자열", "예: ['전라남도']"],
  ["시군구", "표준지역명 기준 리스트 문자열", "예: ['고흥군']"],
  ["뉴스링크", "후보기사 URL에서 추출", "원문 링크"],
  ["뉴스제목", "페이지 title/og:title 수집, 실패 시 후보 제목 사용", "일부 포털 제목 접미사는 남아 있을 수 있음"],
  ["뉴스제목_명사", "첨부 코드의 Okt 명사추출을 참조하되, 현재 환경에서는 정규식 기반 후보명사로 생성", "리스트 문자열"],
  ["기사본문", "기사 본문 후보 텍스트 수집, 실패/깨짐 시 후보 영향요약+근거 사용", "본문 전체 보증 아님"],
  ["기사본문_명사", "정규식 기반 후보명사", "리스트 문자열"],
  ["기상요소", "가뭄 고정", "기존 CSV와 동일 목적"],
  ["시군구코드", "고흥군 46770, 합천군 48890", "리스트 문자열"],
  ["영향구분", "후보기사 영향분야", "농업/물공급 등"],
  ["피해상세", "후보기사 영향요약에서 핵심어 추출", "리스트 문자열"],
];
writeTable(guide, guideRows, { startRow: 2, widths: [18, 80, 42] });

const qa = workbook.worksheets.add("품질점검");
qa.showGridLines = false;
styleTitle(qa, "품질점검", "생성된 실측가뭄 뉴스 형식 파일의 간단한 구조 점검입니다.", 4);
const successCount = statusRows.filter((row) => row["수집성공"] === true).length;
const fallbackCount = statusRows.filter((row) => row["본문fallback사용"] === true).length;
const goheungCount = rows.filter((row) => String(row["시군구"]).includes("고흥군")).length;
const hapcheonCount = rows.filter((row) => String(row["시군구"]).includes("합천군")).length;
const qaRows = [
  ["점검항목", "결과", "기준", "비고"],
  ["결과 행 수", rows.length, "23", "후보기사 URL 수와 동일"],
  ["컬럼 수", csvColumns.length, "12", "기존 CSV와 동일"],
  ["URL 수집 성공", successCount, "20 이상", "HTTP/접속 기준"],
  ["본문 fallback 사용", fallbackCount, "낮을수록 좋음", "차단/깨짐/본문부족 시 사용"],
  ["고흥군 행", goheungCount, "11", "시군구=['고흥군']"],
  ["합천군 행", hapcheonCount, "12", "경남 광역 보조 1건도 합천 관련으로 코딩"],
  ["수식 오류", "별도 inspect 검사", "0", "생성 과정에서 검사"],
];
writeTable(qa, qaRows, { startRow: 2, widths: [24, 18, 18, 58] });
qa.getRange("B4:B8").format.numberFormat = "#,##0";

const resultInspect = await workbook.inspect({
  kind: "table",
  sheetId: "drought_news_result",
  range: "A1:L6",
  include: "values,formulas",
  tableMaxRows: 6,
  tableMaxCols: 12,
  maxChars: 5000,
});
console.log(resultInspect.ndjson);

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
for (const sheetName of ["drought_news_result", "URL_수집상태", "월별집계", "컬럼설명", "품질점검"]) {
  const preview = await workbook.render({ sheetName, autoCrop: "all", scale: 1, format: "png" });
  await fs.writeFile(`${supportDir}/고흥합천뉴스_원본형식_${sheetName}_v1.png`, new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(outputPath);
