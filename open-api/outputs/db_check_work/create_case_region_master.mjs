import fs from "node:fs/promises";
import { Workbook, SpreadsheetFile } from "@oai/artifact-tool";

const outputDir = "/Users/jeongseok/Desktop/9월실측가뭄워크샵";
const supportDir =
  "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work";
const outputPath = `${outputDir}/실측가뭄_사례지역마스터_v1.xlsx`;

const theme = {
  navy: "#1F4E79",
  blue: "#5B9BD5",
  lightBlue: "#D9EAF7",
  green: "#70AD47",
  yellow: "#FFC000",
  gray: "#F3F6F8",
  text: "#1F2937",
  border: "#B7C9D6",
};

const regionRows = [
  [
    "CASE_SOUTH_22_23",
    "2022~2023 남부 가뭄",
    "2022-01",
    "2023-12",
    "GOHEUNG",
    "전남 고흥군",
    "전라남도",
    "고흥군",
    "",
    "핵심",
    "포함",
    1,
    "고흥|고흥군",
    "goheung",
    "jeonnam",
    "전라남도",
    "전남",
    "양파모델 생육환경 feature(goheung_*)와 전남 KOSIS 공급 feature 연결",
  ],
  [
    "CASE_SOUTH_22_23",
    "2022~2023 남부 가뭄",
    "2022-01",
    "2023-12",
    "HAPCHEON",
    "경남 합천군",
    "경상남도",
    "합천군",
    "",
    "핵심",
    "포함",
    1,
    "합천|합천군",
    "hapcheon",
    "gyeongnam",
    "경상남도",
    "경남",
    "양파모델 생육환경 feature(hapcheon_*)와 경남 KOSIS 공급 feature 연결. 뉴스 직접 매칭 추가 확인 필요",
  ],
  [
    "CASE_GANGNEUNG_2025",
    "2025 강릉 가뭄",
    "2025-01",
    "2025-12",
    "GANGNEUNG",
    "강원 강릉시",
    "강원특별자치도",
    "강릉시",
    "",
    "핵심",
    "포함",
    1,
    "강릉|강릉시",
    "강릉",
    "확인 필요",
    "강원특별자치도",
    "강원",
    "강릉 사례 핵심지역. 뉴스 CSV와 고랭지배추 DB 실제가격 연결",
  ],
  [
    "CASE_GANGNEUNG_2025",
    "2025 강릉 가뭄",
    "2025-01",
    "2025-12",
    "DAEGWALLYEONG",
    "강원 평창군 대관령면",
    "강원특별자치도",
    "평창군",
    "대관령면",
    "보조",
    "포함",
    2,
    "대관령|평창|대관령면",
    "대관령",
    "확인 필요",
    "강원특별자치도",
    "강원",
    "강릉 고랭지배추 관련 관측/시장 보조지역. 핵심지역과 별도 표시",
  ],
  [
    "CASE_GANGNEUNG_2025",
    "2025 강릉 가뭄",
    "2025-01",
    "2025-12",
    "GANGWON_PROXY",
    "강원특별자치도",
    "강원특별자치도",
    "",
    "",
    "시도보조",
    "보조",
    3,
    "강원|강원특별자치도",
    "강원",
    "확인 필요",
    "강원특별자치도",
    "강원",
    "신선물가지수 등 시도 단위 지표 연결용. 강릉 직접값처럼 해석하지 않음",
  ],
  [
    "CASE_SOUTH_22_23",
    "2022~2023 남부 가뭄",
    "2022-01",
    "2023-12",
    "JEONNAM_PROXY",
    "전라남도",
    "전라남도",
    "",
    "",
    "시도보조",
    "보조",
    3,
    "전남|전라남도",
    "jeonnam",
    "jeonnam",
    "전라남도",
    "전남",
    "고흥군에 붙일 시도 단위 신선물가·생산량 보조지표",
  ],
  [
    "CASE_SOUTH_22_23",
    "2022~2023 남부 가뭄",
    "2022-01",
    "2023-12",
    "GYEONGNAM_PROXY",
    "경상남도",
    "경상남도",
    "",
    "",
    "시도보조",
    "보조",
    3,
    "경남|경상남도",
    "gyeongnam",
    "gyeongnam",
    "경상남도",
    "경남",
    "합천군에 붙일 시도 단위 신선물가·생산량 보조지표",
  ],
];

const mappingRows = [
  [
    "뉴스 CSV",
    "실측가뭄과제_drought_news_result_1990_2025_수정_2.csv",
    "기사",
    "광역시도, 시군구",
    "고흥군/합천군/강릉시/대관령 문자열 포함",
    "시군구",
    "직접",
    "사용",
    "리스트 문자열 파싱 필요. 고흥·합천 2022~2023 직접 매칭은 현재 3건",
  ],
  [
    "뉴스 DB",
    "DRGHT_MONITORING_DAMAGE, drought_article",
    "기사",
    "SIDO_NM, SGG_NM 또는 sido, sigungu",
    "표준지역명 또는 시군구명",
    "시군구",
    "직접",
    "사용 후보",
    "CSV와 DB 중복/누락 관계 확인 필요",
  ],
  [
    "양파모델 가격",
    "owppm_v0.1/interim/price_target_1kg_grade_a_2013_2025.csv",
    "거래일",
    "지역 없음",
    "가락시장 양파 가격",
    "시장지표",
    "시장 보조",
    "사용",
    "고흥·합천 소비지가격이 아니라 서울 가락시장 도매가격 지표로 명시",
  ],
  [
    "양파모델 생육환경",
    "training_dataset_v1_full_environment_trainable.csv",
    "거래일/작기",
    "goheung_*, hapcheon_*",
    "GOHEUNG, HAPCHEON",
    "시군구/관측지",
    "직접",
    "사용",
    "남부 사례 지역 기준과 부합",
  ],
  [
    "양파 KOSIS",
    "kosis_supply_2000_2025.csv, kosis_supply_feature.csv",
    "연/작기",
    "province",
    "jeonnam, gyeongnam",
    "시도",
    "시도보조",
    "사용",
    "고흥=전남, 합천=경남 보조지표로 연결",
  ],
  [
    "신선물가지수 DB",
    "drought_impact_fresh_food_price_index",
    "월",
    "province, base_date",
    "전라남도, 경상남도, 강원특별자치도",
    "시도",
    "시도보조",
    "사용",
    "시군구 직접값 아님. 전월/전년동월 변화율 계산 필요",
  ],
  [
    "강릉 고랭지배추 가격 DB",
    "daily_market_trends, daily_price_predictions, monthly_market_predictions",
    "일/월",
    "location",
    "강릉, 대관령",
    "지역명",
    "직접/보조",
    "사용",
    "2025년 5월 월간 예측 공백 처리 필요",
  ],
  [
    "수력발전량 DB",
    "dam_daily_generation, dam_monthly_generation, WT_POWER",
    "일/월",
    "dam_name, STN_NAME, ADDRESS",
    "소양강/충주/합천 등",
    "댐/발전소",
    "공간 보조",
    "제한",
    "사례 직접 검증자료가 아니라 보조자료 또는 제외로 표기",
  ],
];

const todayRows = [
  [
    "P0",
    "R1",
    "지역 기준 확정",
    "남부 사례를 전남 고흥군·경남 합천군으로 고정하고, 강릉 사례 핵심/보조지역을 구분",
    "지역마스터 확정본",
    "진행 중",
  ],
  [
    "P0",
    "R2",
    "뉴스 지역 재조회",
    "CSV/DB에서 고흥군, 합천군, 강릉시, 대관령 직접 기사를 다시 조회",
    "지역별 뉴스 건수표",
    "미착수",
  ],
  [
    "P0",
    "R3",
    "월별 skeleton 생성",
    "사례·연월·지역 기준 행을 만든 뒤 뉴스/정형/기술결과를 붙일 기준키 확정",
    "연월마스터",
    "완료",
  ],
  [
    "P0",
    "R4",
    "양파모델 월집계",
    "가락시장 양파 실제가격, 예측가격, 반입량, 전남·경남 생산량을 월별로 집계",
    "양파 월별 지표표",
    "미착수",
  ],
  [
    "P0",
    "R5",
    "시도 보조지표 연결",
    "고흥=전남, 합천=경남, 강릉/대관령=강원 기준 신선물가지수 연결",
    "물가지수 월별 연결표",
    "미착수",
  ],
  [
    "P0",
    "R6",
    "뉴스 등급 산출 기준 결정",
    "기사 수 또는 영향유형 기반 월별 등급 기준을 정하고 근거를 남김",
    "등급 산출 기준표",
    "미착수",
  ],
];

function monthsBetween(startYm, endYm) {
  const [sy, sm] = startYm.split("-").map(Number);
  const [ey, em] = endYm.split("-").map(Number);
  const out = [];
  let y = sy;
  let m = sm;
  while (y < ey || (y === ey && m <= em)) {
    out.push(`${y}-${String(m).padStart(2, "0")}`);
    m += 1;
    if (m === 13) {
      y += 1;
      m = 1;
    }
  }
  return out;
}

const monthRows = [];
for (const region of regionRows.filter((r) => ["핵심", "보조"].includes(r[9]))) {
  for (const ym of monthsBetween(region[2], region[3])) {
    monthRows.push([
      region[0],
      region[1],
      ym,
      region[4],
      region[5],
      region[9],
      region[10],
      region[12],
      region[13],
      region[14],
      region[16],
      "",
    ]);
  }
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
  if (options.freezeRows) sheet.freezePanes.freezeRows(options.freezeRows);
  if (options.freezeCols) sheet.freezePanes.freezeColumns(options.freezeCols);
  if (options.widths) {
    options.widths.forEach((width, index) => {
      sheet.getRangeByIndexes(0, index, 1, 1).format.columnWidth = width;
    });
  }
}

function styleTitle(sheet, title, subtitle, lastCol) {
  sheet.getRangeByIndexes(0, 0, 1, lastCol).merge();
  sheet.getRange("A1").values = [[title]];
  sheet.getRange("A1").format = {
    fill: theme.navy,
    font: { bold: true, color: "#FFFFFF", size: 15 },
  };
  sheet.getRangeByIndexes(1, 0, 1, lastCol).merge();
  sheet.getRange("A2").values = [[subtitle]];
  sheet.getRange("A2").format = {
    fill: theme.lightBlue,
    font: { color: theme.text, size: 10 },
    wrapText: true,
  };
}

const workbook = Workbook.create();

const overview = workbook.worksheets.add("개요");
overview.showGridLines = false;
styleTitle(
  overview,
  "실측가뭄 사례 지역 마스터",
  "DB 원본은 수정하지 않고, 보고용 결과 조합을 위한 지역·연월·연결키 기준만 xlsx로 관리합니다.",
  6,
);
const overviewRows = [
  ["항목", "내용", "비고"],
  ["작성 목적", "사례 검증용 데이터를 조합하기 위한 공통 지역 기준표 작성", "DB화 전 관리 파일"],
  ["남부 사례", "전남 고흥군, 경남 합천군", "2022-01~2023-12"],
  ["강릉 사례", "강원 강릉시, 강원 평창군 대관령면(보조)", "2025-01~2025-12"],
  ["핵심 원칙", "시군구 직접자료와 시도 보조자료를 구분해서 연결", "공간 단위 혼동 방지"],
  ["관리 방식", "원본 DB/CSV/모델 산출물은 수정하지 않고 이 파일의 키로 결과를 조합", "보고용 중간 산출물"],
  ["다음 작업", "뉴스 재조회, 양파모델 월집계, 신선물가지수 연결, 등급 산출", "P0"],
];
writeTable(overview, overviewRows, { widths: [22, 78, 32] });
overview.getRange("A1:C2").format.rowHeight = 28;

const master = workbook.worksheets.add("지역마스터");
master.showGridLines = false;
styleTitle(
  master,
  "지역마스터",
  "사례별 핵심/보조 지역과 데이터별 연결키를 관리합니다. 이 시트가 이후 결과 조합의 기준입니다.",
  18,
);
const masterHeader = [
  "case_id",
  "사례명",
  "시작연월",
  "종료연월",
  "region_id",
  "표준지역명",
  "시도_표준",
  "시군구_표준",
  "읍면동/관측지",
  "지역역할",
  "포함여부",
  "우선순위",
  "뉴스지역키",
  "모델지역키",
  "생산량지역키",
  "물가지수지역키",
  "시도약칭",
  "비고",
];
writeTable(master, [masterHeader, ...regionRows], {
  freezeRows: 3,
  freezeCols: 5,
  widths: [24, 24, 12, 12, 18, 24, 18, 16, 18, 12, 12, 10, 24, 16, 18, 20, 12, 64],
});
master.getRange("A1:R1").format = {
  fill: theme.navy,
  font: { bold: true, color: "#FFFFFF", size: 15 },
};
master.getRange("J4:J60").dataValidation = { rule: { type: "list", values: ["핵심", "보조", "시도보조", "전국보조", "제외"] } };
master.getRange("K4:K60").dataValidation = { rule: { type: "list", values: ["포함", "보조", "제외", "확인 필요"] } };

const monthly = workbook.worksheets.add("연월마스터");
monthly.showGridLines = false;
styleTitle(
  monthly,
  "연월마스터",
  "사례·연월·지역 단위의 결과 조합 skeleton입니다. 이후 뉴스, 가격, 생산량, 물가지수, 등급 결과를 이 행에 붙입니다.",
  15,
);
const monthHeader = [
  "case_id",
  "사례명",
  "연월",
  "연도",
  "월",
  "region_id",
  "표준지역명",
  "지역역할",
  "포함여부",
  "뉴스지역키",
  "가격/모델지역키",
  "생산량지역키",
  "물가지수지역키",
  "조합상태",
  "비고",
];
writeTable(monthly, [monthHeader, ...monthRows.map((r) => [r[0], r[1], r[2], null, null, ...r.slice(3)])], {
  freezeRows: 3,
  freezeCols: 7,
  widths: [24, 24, 10, 8, 6, 18, 24, 12, 12, 24, 18, 18, 18, 16, 40],
});
const rowCount = monthRows.length + 3;
monthly.getRange("D4").formulas = [["=VALUE(LEFT(C4,4))"]];
monthly.getRange(`D4:D${rowCount}`).fillDown();
monthly.getRange("E4").formulas = [["=VALUE(RIGHT(C4,2))"]];
monthly.getRange(`E4:E${rowCount}`).fillDown();
monthly.getRange("N4").formulas = [["=IF(OR(H4=\"핵심\",H4=\"보조\"),\"대기\",\"확인\")"]];
monthly.getRange(`N4:N${rowCount}`).fillDown();
monthly.getRange(`D4:E${rowCount}`).format.numberFormat = "#,##0";

const mapping = workbook.worksheets.add("연결키_매핑");
mapping.showGridLines = false;
styleTitle(
  mapping,
  "연결키 매핑",
  "DB/CSV/모델 산출물을 지역마스터에 붙일 때 사용할 연결 수준과 주의사항입니다.",
  9,
);
const mappingHeader = [
  "데이터 종류",
  "원본/테이블",
  "시간단위",
  "원본 지역 컬럼",
  "조회값/패턴",
  "표준 연결키",
  "연결수준",
  "사용판정",
  "주의사항",
];
writeTable(mapping, [mappingHeader, ...mappingRows], {
  freezeRows: 3,
  widths: [22, 46, 14, 28, 34, 18, 16, 14, 68],
});
mapping.getRange("H4:H40").dataValidation = { rule: { type: "list", values: ["사용", "사용 후보", "제한", "제외", "확인 필요"] } };

const today = workbook.worksheets.add("오늘작업");
today.showGridLines = false;
styleTitle(
  today,
  "오늘 작업",
  "지역마스터 생성 이후 바로 이어갈 P0 작업입니다.",
  6,
);
const todayHeader = ["우선순위", "ID", "작업", "상세", "산출물", "상태"];
writeTable(today, [todayHeader, ...todayRows], {
  freezeRows: 3,
  widths: [12, 10, 26, 78, 30, 14],
});
today.getRange("F4:F40").dataValidation = { rule: { type: "list", values: ["완료", "진행 중", "미착수", "막힘"] } };

const qa = workbook.worksheets.add("품질점검");
qa.showGridLines = false;
styleTitle(
  qa,
  "품질점검",
  "마스터 파일 자체의 간단한 완결성 점검입니다.",
  4,
);
const qaRows = [
  ["점검항목", "공식/기준", "결과", "비고"],
  ["지역마스터 행 수", "지역마스터 데이터 행 수", null, "핵심/보조/시도보조 포함"],
  ["연월마스터 행 수", "연월마스터 데이터 행 수", null, "핵심/보조지역 월별 행"],
  ["남부 핵심지역 수", "CASE_SOUTH_22_23 + 핵심", null, "고흥군, 합천군이어야 함"],
  ["강릉 핵심지역 수", "CASE_GANGNEUNG_2025 + 핵심", null, "강릉시 1개"],
  ["보조지역 수", "지역역할=보조", null, "대관령 등"],
  ["시도보조 수", "지역역할=시도보조", null, "전남/경남/강원"],
  ["조합 대기 행 수", "연월마스터 조합상태=대기", null, "이후 결과 결합 대상"],
];
writeTable(qa, qaRows, { widths: [24, 40, 18, 46] });
qa.getRange("C4").formulas = [[`=COUNTA('지역마스터'!A4:A${regionRows.length + 3})`]];
qa.getRange("C5").formulas = [[`=COUNTA('연월마스터'!A4:A${rowCount})`]];
qa.getRange("C6").formulas = [[`=COUNTIFS('지역마스터'!A4:A${regionRows.length + 3},"CASE_SOUTH_22_23",'지역마스터'!J4:J${regionRows.length + 3},"핵심")`]];
qa.getRange("C7").formulas = [[`=COUNTIFS('지역마스터'!A4:A${regionRows.length + 3},"CASE_GANGNEUNG_2025",'지역마스터'!J4:J${regionRows.length + 3},"핵심")`]];
qa.getRange("C8").formulas = [[`=COUNTIF('지역마스터'!J4:J${regionRows.length + 3},"보조")`]];
qa.getRange("C9").formulas = [[`=COUNTIF('지역마스터'!J4:J${regionRows.length + 3},"시도보조")`]];
qa.getRange("C10").formulas = [[`=COUNTIF('연월마스터'!N4:N${rowCount},"대기")`]];
qa.getRange("C4:C10").format.numberFormat = "#,##0";

for (const sheet of workbook.worksheets) {
  const used = sheet.getUsedRange();
  if (used) {
    used.format.wrapText = true;
    used.format.font = { color: theme.text, size: 10 };
  }
}

const inspect = await workbook.inspect({
  kind: "table",
  sheetId: "지역마스터",
  range: "A3:R10",
  include: "values,formulas",
  tableMaxRows: 10,
  tableMaxCols: 18,
  maxChars: 5000,
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
for (const sheetName of ["개요", "지역마스터", "연월마스터", "연결키_매핑", "오늘작업", "품질점검"]) {
  const preview = await workbook.render({
    sheetName,
    autoCrop: "all",
    scale: 1,
    format: "png",
  });
  await fs.writeFile(`${supportDir}/사례지역마스터_${sheetName}_v1.png`, new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(outputPath);
