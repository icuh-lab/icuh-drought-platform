import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath =
  "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work/실측가뭄_과제_세부작업_체크리스트_DB확인_v2_뉴스CSV반영.xlsx";
const outputDir =
  "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work";
const outputPath = `${outputDir}/실측가뭄_과제_세부작업_체크리스트_DB확인_v3_뉴스CSV_양파모델반영.xlsx`;

const onionSummary = [
  ["구분", "확인 결과", "판정"],
  [
    "프로젝트 위치",
    "/Users/jeongseok/Desktop/업무/31_2026 수자원학회/양파모델",
    "확인 완료",
  ],
  [
    "모델 범위",
    "양파 / 서울 가락시장 / 1키로 / 상 / 원/kg 도매가격 예측. v0.1은 일별 full-environment, v0.2는 주 단위 실험 경로",
    "남부 양파 검증 보강자료",
  ],
  [
    "실제 가격",
    "owppm_v0.1/interim/price_target_1kg_grade_a_2013_2025.csv: 3,967건, 2013-01-03~2025-12-31, 가격 결측 0건. 2022년 306건/12개월, 2023년 306건/12개월, 2025년 301건/12개월",
    "월 집계 가능",
  ],
  [
    "반입량",
    "owppm_v0.1/interim/arrival_daily_2013_2025.csv: 3,950건, 2013-01-03~2025-12-31. 2022년 304건/12개월, 2023년 303건/12개월, 2025년 301건/12개월. arrival_ton 최솟값 -1 존재",
    "정리 필요",
  ],
  [
    "일별 시장 feature",
    "owppm_v0.1/processed/04_market_feature/market_feature_daily.csv: 실제가격, arrival_ton, arrival_ton_ma7/ma30, price_lag1~3 포함. 2022/2023/2025 모두 12개월 존재",
    "월 집계 가능",
  ],
  [
    "생산량·재배면적",
    "owppm_v0.1/interim/kosis_supply_2000_2025.csv: 전남·경남 2000~2025 각 26년, cultivation_area_ha/yield_kg_per_10a/production_ton 결측 0건",
    "양파는 가능",
  ],
  [
    "생산량 변화율",
    "owppm_v0.1/processed/05_kosis_supply_feature/kosis_supply_feature.csv: 전남·경남 crop_year 2013~2025, 전년도 재배면적/단수/생산량 및 yoy 컬럼 포함",
    "양파는 가능",
  ],
  [
    "일별 학습 가능 데이터",
    "owppm_v0.1/processed/06_training_dataset/training_dataset_v1_full_environment_trainable.csv: 2,540건, 2016-06-01~2025-12-31. 2022년 247건/11개월, 2023년 227건/11개월, 2025년 271건/11개월",
    "일부 월 공백",
  ],
  [
    "예측값",
    "owppm_v0.1/model_output/actual_vs_predicted.csv: 10,575건, 2017-11-28~2025-12-31, y_true/y_pred 결측 0건. 2022년 11개월, 2023년 11개월, 2025년 11개월",
    "정리 필요",
  ],
  [
    "2025 holdout",
    "owppm_v0.1/model_output/holdout_2025/holdout_2025_predictions.csv: 271건, 2025-02-10~2025-12-31, 11개월. 실제/예측 가격 및 오차 포함",
    "2025 보조 가능",
  ],
  [
    "주 단위 가격",
    "owppm_v0.2/interim/weekly_price_feature.csv: 310주, 2020-01-13~2025-12-15. 2022년 52주/12개월, 2023년 52주/12개월, 2025년 50주/12개월",
    "월 집계 가능",
  ],
  [
    "주 단위 공급 proxy",
    "owppm_v0.2/interim/weekly_supply_proxy_feature.csv: cultivation_area/production은 310주 결측 0건이나 previous_year_arrival_total은 133건만 존재. proxy_available 81건",
    "공급 proxy 제한",
  ],
  [
    "주의사항",
    "현재 산출물은 로컬 양파모델 프로젝트 파일이며 RDS 적재 여부는 확인되지 않음. 가격은 가락시장 가격으로, 광주·전남 지역 소비자가격이 아니라 남부 양파 영향 검증용 시장지표로 해석해야 함",
    "통합 전 명시 필요",
  ],
];

const updates = new Map([
  [
    "T4-5",
    [
      "진행 중",
      "양파모델에서 가락시장 양파 실제가격 2013-01-03~2025-12-31 확인. 2022·2023·2025 모두 12개월 존재하나 RDS 통합/월집계 및 지역 해석은 아직 미완",
      "양파 일별 가격을 월별로 집계하고 가락시장 지표 사용 근거 명시",
    ],
  ],
  [
    "T4-6",
    [
      "진행 중",
      "양파모델 actual_vs_predicted는 2017-11-28~2025-12-31 예측값을 포함하나 2022·2023·2025 각각 11개월만 확인되고 모델 variant 선택/월집계 필요",
      "검증용 예측 variant 확정, 누락월 처리, 월별 예측값 생성",
    ],
  ],
  [
    "T4-8",
    [
      "진행 중",
      "양파모델 KOSIS 파일에서 전남·경남 양파 재배면적/단수/생산량 2000~2025 확인. 고랭지배추 생산량·재배면적은 아직 미확인",
      "전남 양파 생산량 월/연 기준 반영, 강릉 고랭지배추 생산통계 추가 확인",
    ],
  ],
  [
    "T4-9",
    [
      "진행 중",
      "양파모델 processed KOSIS feature에 전남·경남 전년도 생산량 yoy가 존재하나 검증 통합표에는 아직 반영되지 않음",
      "양파 생산량 변화율을 사례 월표에 연결하고 고랭지배추 변화율 확보",
    ],
  ],
  [
    "T4-11",
    [
      "진행 중",
      "신선물가지수, 뉴스 CSV, 양파모델 가격/예측/생산량은 연결 가능하나 아직 하나의 사례·연월·지역 통합 산출물이 없음",
      "case_id·연월 기준 정형 통합표 생성",
    ],
  ],
  [
    "T6-1",
    [
      "진행 중",
      "남부는 뉴스 CSV 103건, 신선물가 24개월, 양파모델 실제가격/예측/전남 생산량이 확인됨. 단 뉴스 등급 산출과 월별 통합표는 미완",
      "양파 산출물 월집계, 뉴스 등급 산출, 사례 월표 통합",
    ],
  ],
]);

const remainingRows = [
  ["우선순위", "ID", "남은 작업", "예상 작업내용", "선행 작업"],
  [
    "P0",
    "T2-1~T2-6",
    "사례-연월-지역 기준 마스터 생성",
    "두 사례 월 목록, case_id, 표준 지역명, 포함/제외 플래그 작성",
    "없음",
  ],
  [
    "P0",
    "T3-2~T3-10",
    "뉴스 데이터 표준화 및 월별 집계",
    "뉴스 CSV의 리스트형 지역·분야를 정규화하고 대표기사·영향내용·등급을 산출",
    "T2 지역 기준",
  ],
  [
    "P0",
    "T4-5~T4-6",
    "양파모델 가격·예측 산출물 월집계",
    "가락시장 양파 실제/예측 가격을 월별 지표로 만들고 누락월·variant 선택 기준 확정",
    "양파모델 산출물",
  ],
  [
    "P0",
    "T4-8~T4-9",
    "생산량·재배면적 보강",
    "전남 양파 KOSIS 통계는 통합하고, 강릉 고랭지배추 생산량·재배면적은 추가 확인",
    "품목별 검증 지표 결정",
  ],
  [
    "P0",
    "T4-7",
    "정형 변화율 계산",
    "물가지수·가격·생산량의 전월/전년동월 또는 전년 대비 변화율 생성",
    "T4-2~T4-9",
  ],
  [
    "P0",
    "T5-1~T5-8",
    "사례 기간 가뭄영향 등급 재산출",
    "2022~2023 남부, 2025 강릉 월×지역×분야 등급과 원값/구간 저장",
    "뉴스 표준화",
  ],
  [
    "P0",
    "T6-1~T6-12",
    "월별 통합 검증표 및 판정",
    "사례·연월·지역 기준으로 기술등급, 뉴스 영향, 정형값을 연결해 일치/부분/불일치 판정",
    "T2~T5",
  ],
  [
    "P1",
    "T7-1~T7-12",
    "기술보고서·워크숍 자료 작성",
    "사례 개요, 방법, 결과표/그래프, 한계, 결론, 발표자료 반영",
    "T6 통합검증 결과",
  ],
  [
    "P2",
    "T8-1~T8-10",
    "플랫폼 시범운영 및 화면 검수",
    "플랫폼 항목 대응표, 업체 전달, 연/월 조회, 등급·뉴스·정형값 화면 대조",
    "T6 확정 데이터",
  ],
];

function getOrClearSheet(workbook, name) {
  let sheet;
  try {
    sheet = workbook.worksheets.getItem(name);
    sheet.getUsedRange()?.clear({ applyTo: "all" });
  } catch {
    sheet = workbook.worksheets.add(name);
  }
  return sheet;
}

function writeTable(sheet, values, widths = []) {
  const range = sheet.getRangeByIndexes(0, 0, values.length, values[0].length);
  range.values = values;
  range.format.wrapText = true;
  range.format.borders = { preset: "all", style: "thin", color: "#D9EAF7" };
  sheet.getRangeByIndexes(0, 0, 1, values[0].length).format = {
    fill: "#1F4E79",
    font: { bold: true, color: "#FFFFFF" },
  };
  widths.forEach((width, index) => {
    sheet.getRangeByIndexes(0, index, 1, 1).format.columnWidth = width;
  });
  sheet.freezePanes.freezeRows(1);
}

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

const onionSheet = getOrClearSheet(workbook, "양파모델 확인");
writeTable(onionSheet, onionSummary, [22, 116, 24]);

const remainingSheet = getOrClearSheet(workbook, "남은 작업");
writeTable(remainingSheet, remainingRows, [14, 18, 34, 72, 30]);

const taskStats = new Map();
for (let i = 0; i < ids.length; i++) {
  const task = String(checklist.getRange(`B${i + 5}`).values[0][0] ?? "");
  const status = String(checklist.getRange(`E${i + 5}`).values[0][0] ?? "");
  if (!taskStats.has(task)) {
    taskStats.set(task, { total: 0, done: 0, doing: 0, todo: 0, blocked: 0 });
  }
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
progress.getRange("A1:G1").format = {
  fill: "#5B9BD5",
  font: { bold: true, color: "#FFFFFF" },
};
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
await fs.writeFile(`${outputPath}.inspect.ndjson`, errors.ndjson);
console.log(errors.ndjson);

for (const sheetName of ["양파모델 확인", "진행현황", "남은 작업", "실측가뭄_체크리스트"]) {
  const preview = await workbook.render({
    sheetName,
    autoCrop: "all",
    scale: 1,
    format: "png",
  });
  await fs.writeFile(`${outputDir}/${sheetName}_v3.png`, new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(outputPath);
for (const [task, stat] of taskStats) {
  console.log(
    `${task}\t전체 ${stat.total}\t완료 ${stat.done}\t진행중 ${stat.doing}\t미착수 ${stat.todo}\t막힘 ${stat.blocked}\t완료율 ${(stat.done / stat.total * 100).toFixed(1)}%`,
  );
}
