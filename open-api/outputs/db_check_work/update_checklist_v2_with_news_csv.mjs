import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work/실측가뭄_과제_세부작업_체크리스트_DB확인_v1.xlsx";
const outputDir = "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work";
const outputPath = `${outputDir}/실측가뭄_과제_세부작업_체크리스트_DB확인_v2_뉴스CSV반영.xlsx`;

const csvSummary = [
  ["구분", "확인 결과", "판정"],
  ["파일", "실측가뭄과제_drought_news_result_1990_2025_수정_2.csv, UTF-8, 1,451건, 12개 원본 컬럼", "확인 완료"],
  ["컬럼", "일자, 광역시도, 시군구, 뉴스링크, 뉴스제목, 뉴스제목_명사, 기사본문, 기사본문_명사, 기상요소, 시군구코드, 영향구분, 피해상세", "기사 단위 구조 있음"],
  ["날짜 범위", "1990-08-23~2025-11-16, 날짜 결측 0건", "분석 가능"],
  ["2022~2025 전체", "2022년 139건, 2023년 75건, 2024년 25건, 2025년 73건", "대상 연도 포함"],
  ["22~23 남부", "광역시도에 광주광역시 또는 전라남도 포함 조건: 103건, 13개월, 2022-03-09~2023-05-23", "뉴스 검증 가능"],
  ["22~23 남부 월", "2022-03 7건, 2022-05 1건, 2022-06 2건, 2022-07 11건, 2022-08 4건, 2022-09 2건, 2022-10 2건, 2022-11 16건, 2022-12 5건, 2023-01 3건, 2023-02 13건, 2023-03 36건, 2023-05 1건", "월별 집계 가능"],
  ["22~23 남부 분야", "물공급 54, 기타 23, 농업 15, 산업 5, 환경 5, 수산업 1", "분야 분류 있음"],
  ["2025 강원", "광역시도 강원 또는 시군구 강릉 조건: 62건, 4개월, 2025-03-24~2025-10-31", "뉴스 검증 가능"],
  ["2025 강릉시", "시군구에 강릉 포함 조건: 44건, 3개월, 2025-08-12~2025-10-31", "강릉 사례 핵심기간 가능"],
  ["2025 강릉시 월", "2025-08 21건, 2025-09 22건, 2025-10 1건", "월별 집계 가능"],
  ["2025 강릉시 분야", "물공급 35, 기타 9, 농업 2", "분야 분류 있음"],
  ["등급 컬럼", "등급/grade/위험도/재해등급 컬럼 없음. 영향구분과 피해상세는 완료되어 있으나 월별 낮음/보통/높음 또는 관심/주의/경고/위험 등급은 파일에 저장되어 있지 않음", "등급은 추가 산출 필요"],
  ["주의사항", "광역시도·시군구·영향구분·피해상세 일부 값은 리스트 문자열 형태이므로 통합 전 파싱/정규화 필요", "가공 필요"],
];

const updates = new Map([
  ["T3-1", ["완료", "DB 외 CSV 파일 확인: 1990-08-23~2025-11-16 뉴스 1,451건, 22~25 대상 연도 포함", "없음"]],
  ["T3-2", ["완료", "CSV 일자 컬럼 전 행 파싱 가능, 날짜 결측 0건, 연도·월 생성 가능", "월별 집계 산출물 생성"]],
  ["T3-3", ["진행 중", "CSV 광역시도/시군구로 남부 103건, 강릉시 44건 연결 가능하나 리스트 문자열 파싱 필요", "지역 리스트 파싱 및 표준 지역명 매핑"]],
  ["T3-4", ["완료", "CSV 영향구분 전 행 존재. 남부 분야: 물공급/농업/산업/환경 등, 강릉 분야: 물공급/농업/기타", "리스트형 영향구분 정규화"]],
  ["T3-5", ["완료", "CSV 피해상세 전 행 존재. 강릉 오봉저수지·식수·농업 등 피해상세 확인", "기타 값은 검토"]],
  ["T3-6", ["진행 중", "뉴스제목·기사본문은 전 행 존재하나 월별 대표 근거 문장 선별은 아직 안 됨", "대표 근거 문장 추출"]],
  ["T3-7", ["진행 중", "CSV에는 영향구분/피해상세는 있으나 등급 컬럼은 없음", "월×지역×분야 기사 수 기반 등급 산출"]],
  ["T3-8", ["미착수", "CSV에 기사 원자료는 있으나 월별 대표기사 선정 컬럼/산출물 없음", "주요 월별 대표기사 1~3건 선정"]],
  ["T3-9", ["완료", "CSV가 기사 한 건당 한 행 구조이며 링크·제목·본문·지역·분야·피해상세가 전 행 존재", "리스트 문자열 정규화"]],
  ["T3-10", ["진행 중", "CSV로 월별 집계 가능: 남부 13개월, 강릉시 3개월 확인. 집계표 자체는 별도 생성 필요", "사례별 월×지역×분야 집계 생성"]],
  ["T5-1", ["진행 중", "CSV에 사례 기간 뉴스 영향 데이터는 있으나 등급 컬럼은 없음", "뉴스 기반 월별 등급 산출"]],
  ["T5-2", ["진행 중", "CSV 일자 기준 월 정리는 가능하나 기술 결과 월별 등급 산출물은 없음", "월별 결과표 생성"]],
  ["T5-3", ["진행 중", "영향구분은 있으나 등급 또는 점수 결과와 연결되지 않음", "영향구분 정규화 후 등급 산출"]],
  ["T5-4", ["진행 중", "기사 수 원값은 CSV에서 계산 가능하지만 원값 보존 테이블/시트는 아직 없음", "월별 기사 수 원값 표 생성"]],
  ["T5-5", ["진행 중", "CSV에는 등급 컬럼이 없어 월별·분야별 가뭄 등급은 추가 계산 필요", "등급 산출 기준 적용"]],
  ["T5-6", ["진행 중", "등급 기준 컬럼/구간 없음", "Jenks 또는 별도 등급 기준 기록"]],
  ["T5-8", ["진행 중", "CSV로 사례·월·지역·분야 조회 기반은 마련되었으나 등급 포함 최종 결과표는 없음", "기술결과 최종 테이블 생성"]],
  ["T6-1", ["진행 중", "남부 뉴스 CSV 103건과 신선물가는 연결 가능하나 양파 가격·생산량·등급 산출은 남음", "양파/생산량 보강 및 등급 계산 후 통합"]],
  ["T6-2", ["진행 중", "강릉시 2025 뉴스 44건, 고랭지배추/신선물가 연결 가능. 단 등급 산출과 월간 예측 5월 처리 필요", "뉴스 등급 산출 및 5월 예측 공백 처리"]],
]);

const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);
const checklist = workbook.worksheets.getItem("실측가뭄_체크리스트");

const ids = checklist.getRange("A5:A80").values.map((row) => String(row[0] ?? ""));
for (const [id, [status, reason, todo]] of updates) {
  const idx = ids.indexOf(id);
  if (idx === -1) throw new Error(`ID not found: ${id}`);
  const row = idx + 5;
  checklist.getRange(`E${row}:G${row}`).values = [[status, reason, todo]];
}

let csvSheet;
try {
  csvSheet = workbook.worksheets.getItem("뉴스CSV 확인");
  csvSheet.getUsedRange()?.clear({ applyTo: "all" });
} catch {
  csvSheet = workbook.worksheets.add("뉴스CSV 확인");
}
const csvRange = csvSheet.getRangeByIndexes(0, 0, csvSummary.length, csvSummary[0].length);
csvRange.values = csvSummary;
csvRange.format.wrapText = true;
csvRange.format.borders = { preset: "all", style: "thin", color: "#D9EAF7" };
csvSheet.getRange("A1:C1").format = { fill: "#1F4E79", font: { bold: true, color: "#FFFFFF" } };
csvSheet.getRange("A:A").format.columnWidth = 22;
csvSheet.getRange("B:B").format.columnWidth = 110;
csvSheet.getRange("C:C").format.columnWidth = 24;
csvSheet.freezePanes.freezeRows(1);

const taskStats = new Map();
for (let i = 0; i < ids.length; i++) {
  const task = String(checklist.getRange(`B${i + 5}`).values[0][0] ?? "");
  const status = String(checklist.getRange(`E${i + 5}`).values[0][0] ?? "");
  if (!taskStats.has(task)) taskStats.set(task, { total: 0, done: 0, doing: 0, todo: 0, blocked: 0 });
  const stat = taskStats.get(task);
  stat.total += 1;
  if (status === "완료") stat.done += 1;
  if (status === "진행 중") stat.doing += 1;
  if (status === "미착수") stat.todo += 1;
  if (status === "막힘") stat.blocked += 1;
}

const progress = workbook.worksheets.getItem("진행현황");
const progressRows = [["업무", "전체 세부작업", "완료", "진행 중", "미착수", "막힘", "완료율"]];
for (const [task, stat] of taskStats) {
  progressRows.push([task, stat.total, stat.done, stat.doing, stat.todo, stat.blocked, stat.done / stat.total]);
}
progress.getRange("A1:G9").values = progressRows;
progress.getRange("A1:G1").format = { fill: "#5B9BD5", font: { bold: true, color: "#FFFFFF" } };
progress.getRange("A2:G9").format.fill = "#FFFFFF";
progress.getRange("A1:G9").format.borders = { preset: "all", style: "thin", color: "#9CC2E5" };
progress.getRange("B2:F9").format.numberFormat = "#,##0";
progress.getRange("G2:G9").format.numberFormat = "0.0%";

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "formula error scan",
  maxChars: 3000,
});
console.log(errors.ndjson);

for (const sheetName of ["뉴스CSV 확인", "진행현황", "실측가뭄_체크리스트"]) {
  const preview = await workbook.render({
    sheetName,
    autoCrop: "all",
    scale: 1,
    format: "png",
  });
  await fs.writeFile(`${outputDir}/${sheetName}_v2.png`, new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(outputPath);
