import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath =
  "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work/실측가뭄_과제_세부작업_체크리스트_DB확인_v3_뉴스CSV_양파모델반영.xlsx";
const outputDir =
  "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work";
const outputPath = `${outputDir}/실측가뭄_과제_세부작업_체크리스트_DB확인_v4_지역정정반영.xlsx`;

const correctionSummary = [
  ["구분", "확인 결과", "판정"],
  [
    "지역 기준 정정",
    "남부 사례 대상 지역을 기존 광주·전남이 아니라 전남 고흥군, 경남 합천군으로 정정",
    "반영 필요",
  ],
  [
    "양파모델 적합성",
    "양파모델 학습셋은 goheung_* 및 hapcheon_* 생육환경 feature, 전남·경남 KOSIS 공급 feature를 포함",
    "지역 기준과 부합",
  ],
  [
    "뉴스 CSV 재확인",
    "2022~2023년 시군구에 고흥 또는 합천이 포함된 기사 3건 확인. 기간은 2022-08-05~2022-11-23",
    "뉴스 근거 제한적",
  ],
  [
    "뉴스 월별 분포",
    "2022-08 1건, 2022-11 2건. 2023년 고흥·합천 직접 매칭 기사는 현재 CSV에서 0건",
    "공백월 많음",
  ],
  [
    "뉴스 지역 분포",
    "고흥군 직접 1건, 강진군·고흥군·신안군·해남군 묶음 기사 2건. 합천군 직접 매칭 0건",
    "합천 뉴스 추가 확인 필요",
  ],
  [
    "뉴스 영향 분야",
    "확인된 3건 모두 농업 분야. 피해상세에는 모내기·논·농작물·농가·양파 포함",
    "농업 영향 근거 가능",
  ],
  [
    "검증상 영향",
    "남부 사례는 신선물가지수의 시도 단위 연결보다 고흥·합천 작물/생육환경/뉴스 영향 중심으로 재구성해야 함",
    "통합키 재설계 필요",
  ],
];

const updates = new Map([
  [
    "T1-3",
    [
      "완료",
      "분석 지역이 전남 고흥군·경남 합천군 / 강릉 및 강원 관련지역으로 정정 확정됨",
      "시도 단위 보조지표 사용 시 고흥=전남, 합천=경남 매핑 명시",
    ],
  ],
  [
    "T1-5",
    [
      "진행 중",
      "고흥·합천 기준으로 양파모델 생육환경/생산량은 부합하나 뉴스 CSV는 3건만 확인되어 사용 데이터 재확정 필요",
      "고흥·합천 직접 뉴스 추가 수집 여부 결정",
    ],
  ],
  [
    "T2-4",
    [
      "진행 중",
      "남부 기준 지역을 전남 고흥군·경남 합천군으로 정정. 시도 보조지표는 전남/경남으로 연결 가능",
      "고흥군·합천군 표준 지역코드와 시도 매핑 작성",
    ],
  ],
  [
    "T2-6",
    [
      "진행 중",
      "고흥·합천 기준 연결키는 양파모델과 부합하나 뉴스 CSV 직접 기사가 3건뿐이라 월별 공백 처리 필요",
      "공백월 처리 규칙과 추가 뉴스 수집 범위 확정",
    ],
  ],
  [
    "T3-3",
    [
      "진행 중",
      "뉴스 CSV에서 2022~2023 고흥/합천 직접 매칭은 3건(고흥 포함 3건, 합천 0건)으로 확인됨",
      "합천군 뉴스 추가 확인 및 고흥 묶음기사 지역 배분 규칙 작성",
    ],
  ],
  [
    "T6-1",
    [
      "진행 중",
      "남부 기준을 고흥·합천으로 정정. 양파모델 가격/예측/전남·경남 생산량은 연결 가능하나 뉴스 직접 근거는 3건뿐이고 등급/월별 통합표는 미완",
      "고흥·합천 월별 통합표 재작성, 뉴스 추가 확인, 등급 산출",
    ],
  ],
]);

const remainingRows = [
  ["우선순위", "ID", "남은 작업", "예상 작업내용", "선행 작업"],
  [
    "P0",
    "T2-1~T2-6",
    "고흥·합천 기준 사례-연월-지역 마스터 생성",
    "남부 사례 지역을 전남 고흥군·경남 합천군으로 고정하고 case_id, 연월, 시도 보조지역, 포함/제외 플래그 작성",
    "지역 기준 정정",
  ],
  [
    "P0",
    "T3-2~T3-10",
    "고흥·합천 뉴스 재정리",
    "현재 CSV의 고흥/합천 직접 매칭 3건을 정리하고 합천군 및 공백월 추가 뉴스 확인 여부 결정",
    "T2 지역 기준",
  ],
  [
    "P0",
    "T4-5~T4-6",
    "양파모델 가격·예측 산출물 월집계",
    "가락시장 양파 실제/예측 가격을 월별 지표로 만들고 고흥·합천 생육환경 feature와 함께 해석",
    "양파모델 산출물",
  ],
  [
    "P0",
    "T4-8~T4-9",
    "전남·경남 생산량·재배면적 연결",
    "양파모델 KOSIS 전남·경남 생산량/재배면적/yoy를 고흥·합천 사례 보조지표로 연결",
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
    "고흥·합천, 2025 강릉 월×지역×분야 등급과 원값/구간 저장",
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

function replaceValues(sheet, replacements) {
  const range = sheet.getUsedRange();
  if (!range) return;
  const values = range.values;
  const updated = values.map((row) =>
    row.map((value) => {
      if (typeof value !== "string") return value;
      let next = value;
      for (const [from, to] of replacements) {
        next = next.split(from).join(to);
      }
      return next;
    }),
  );
  range.values = updated;
}

const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);
const checklist = workbook.worksheets.getItem("실측가뭄_체크리스트");

const replacements = [
  ["광주·전남", "고흥·합천"],
  ["광주광역시·전라남도", "전남 고흥군·경남 합천군"],
  ["광주광역시/전라남도", "전남 고흥군/경남 합천군"],
  ["광주광역시 또는 전라남도", "전남 고흥군 또는 경남 합천군"],
  ["광주/전남", "고흥/합천"],
  ["광주·전남 지역", "고흥·합천 지역"],
];

for (const sheetName of [
  "실측가뭄_체크리스트",
  "DB 현황",
  "사례별 확인",
  "뉴스CSV 확인",
  "양파모델 확인",
]) {
  try {
    replaceValues(workbook.worksheets.getItem(sheetName), replacements);
  } catch {
    // Sheet may not exist in older versions.
  }
}

const ids = checklist.getRange("A5:A80").values.map((row) => String(row[0] ?? ""));
for (const [id, [status, reason, todo]] of updates) {
  const idx = ids.indexOf(id);
  if (idx === -1) throw new Error(`ID not found: ${id}`);
  const row = idx + 5;
  checklist.getRange(`E${row}:G${row}`).values = [[status, reason, todo]];
}

const correctionSheet = getOrClearSheet(workbook, "지역정정 확인");
writeTable(correctionSheet, correctionSummary, [22, 116, 24]);

const remainingSheet = getOrClearSheet(workbook, "남은 작업");
writeTable(remainingSheet, remainingRows, [14, 18, 34, 72, 30]);

const newsSheet = workbook.worksheets.getItem("뉴스CSV 확인");
newsSheet.getRange("A6:C8").values = [
  [
    "22~23 고흥·합천",
    "시군구에 고흥 또는 합천 포함 조건: 3건, 2개월, 2022-08-05~2022-11-23. 합천군 직접 매칭 0건",
    "뉴스 근거 제한적",
  ],
  [
    "22~23 고흥·합천 월",
    "2022-08 1건, 2022-11 2건. 2023년 직접 매칭 기사 0건",
    "공백월 많음",
  ],
  ["22~23 고흥·합천 분야", "농업 3건", "분야 분류 있음"],
];

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "formula error scan",
  maxChars: 3000,
});
await fs.writeFile(`${outputPath}.inspect.ndjson`, errors.ndjson);
console.log(errors.ndjson);

for (const sheetName of ["지역정정 확인", "뉴스CSV 확인", "남은 작업", "실측가뭄_체크리스트"]) {
  const preview = await workbook.render({
    sheetName,
    autoCrop: "all",
    scale: 1,
    format: "png",
  });
  await fs.writeFile(`${outputDir}/${sheetName}_v4.png`, new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(outputPath);
