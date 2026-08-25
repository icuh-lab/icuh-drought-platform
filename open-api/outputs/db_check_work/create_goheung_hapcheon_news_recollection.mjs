import fs from "node:fs/promises";
import { Workbook, SpreadsheetFile } from "@oai/artifact-tool";

const outputDir = "/Users/jeongseok/Desktop/9월실측가뭄워크샵";
const supportDir =
  "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work";
const outputPath = `${outputDir}/실측가뭄_고흥합천_가뭄뉴스_재수집_v1.xlsx`;

const theme = {
  navy: "#1F4E79",
  blue: "#5B9BD5",
  lightBlue: "#D9EAF7",
  paleYellow: "#FFF2CC",
  paleGreen: "#E2F0D9",
  paleRed: "#FCE4D6",
  gray: "#F3F6F8",
  text: "#1F2937",
  border: "#B7C9D6",
};

const articles = [
  [
    "NEWS-GH-20220530-01",
    new Date("2022-05-30"),
    "고흥군, '봄 가뭄' 대책 추진…관정 개발·노후 저수지 정비",
    "연합뉴스",
    "전남 고흥군",
    "고흥군",
    "농업",
    "농업용수 공급체계 개선, 관정 개발, 양수장 설치, 노후 저수지 정비",
    "고흥 지역 봄 가뭄 일상화와 농업용수 부족 대응을 직접 다룸",
    "직접",
    "사용",
    "고흥 봄가뭄 대책",
    "https://www.yna.co.kr/view/AKR20220530071100054",
    "웹 재검색",
  ],
  [
    "NEWS-GH-20220530-02",
    new Date("2022-05-30"),
    "고흥군, 봄 가뭄 지속에 따른 용수 관리 나서",
    "아시아경제",
    "전남 고흥군",
    "고흥군",
    "농업",
    "농업용수 공급체계 개선, 물부족 경지면적, 사업비 확보",
    "연합뉴스 기사와 같은 고흥군 봄가뭄 대책 보도 성격",
    "직접",
    "중복검토",
    "고흥 봄가뭄 대책",
    "https://www.asiae.co.kr/article/2022053015315390056",
    "웹 재검색",
  ],
  [
    "NEWS-GH-20220713-01",
    new Date("2022-07-13"),
    "고흥군, 가뭄 극복 예비비 긴급투입",
    "CBS/다음",
    "전남 고흥군",
    "고흥군",
    "농업",
    "상반기 강우량 부족, 농업용수 부족 우려, 예비비 투입, 관정·양수장비 지원",
    "고흥군 단위의 가뭄 대응 예산 투입 기사",
    "직접",
    "사용",
    "고흥 예비비",
    "https://v.daum.net/v/20220713170906527?f=p",
    "웹 재검색",
  ],
  [
    "NEWS-GH-20220805-01",
    new Date("2022-08-05"),
    "고흥 간척지 ‘가뭄 염해’ 심각…농업재해 인정 서둘러야",
    "한국농어민신문",
    "전남 고흥군",
    "고흥군",
    "농업",
    "간척지 염해, 모내기 실패, 벼 피해, 저수율 저하, 피해면적 약 80ha",
    "기존 CSV에도 있는 핵심 직접 피해 기사",
    "직접",
    "사용",
    "고흥 간척지 염해",
    "https://www.agrinet.co.kr/news/articleView.html?idxno=311514",
    "기존 CSV + 웹 재검색",
  ],
  [
    "NEWS-GH-20221025-01",
    new Date("2022-10-25"),
    "고흥군, 가을 가뭄에 농업용수 확보·농작물 피해예방 '총력'",
    "뉴스핌",
    "전남 고흥군",
    "고흥군",
    "농업",
    "유자, 조생양파, 마늘 생육 적신호, 금산면 조생양파 단지 용수공급",
    "고흥 조생양파 피해와 대응이 직접 확인됨",
    "직접",
    "사용",
    "고흥 가을가뭄 양파",
    "https://www.newspim.com/news/view/20221025000327",
    "웹 재검색",
  ],
  [
    "NEWS-GH-20221102-01",
    new Date("2022-11-02"),
    "남부지역 ‘가을가뭄’…노지채소 목탄다",
    "농민신문",
    "전남 고흥군",
    "고흥군",
    "농업",
    "고흥 금산면 조생양파 생육 저하, 저수율 하락, 유자 수확량 감소",
    "남부 광역 기사지만 고흥군·양파 피해를 구체적으로 다룸",
    "직접",
    "사용",
    "고흥 가을가뭄 양파",
    "https://www.nongmin.com/article/20221031365750",
    "웹 재검색",
  ],
  [
    "NEWS-GH-20221107-01",
    new Date("2022-11-07"),
    "'최근 10년 평균 강우량 대비 65%' 고흥군, 가뭄 극복 행정력 집중",
    "데일리한국/네이트",
    "전남 고흥군",
    "고흥군",
    "농업",
    "9~10월 강우량 부족, 유자 생산량 감소 우려, 양파·마늘 생육 지장 우려",
    "고흥군 대책회의와 농작물 피해 우려를 직접 다룸",
    "직접",
    "사용",
    "고흥 가뭄 점검",
    "https://news.nate.com/view/20221107n30501",
    "웹 재검색",
  ],
  [
    "NEWS-GH-20221114-01",
    new Date("2022-11-14"),
    "[현장 카메라]남부는 최악 가뭄…‘물 부족’ 재난 수준",
    "채널A",
    "전남 고흥군",
    "고흥군",
    "농업",
    "양파밭 농업용수 웅덩이 고갈, 양파잎 고사, 물 사용 갈등",
    "고흥 양파밭 현장 피해를 직접 보여주는 핵심 기사",
    "직접",
    "사용",
    "고흥 양파 현장",
    "https://ichannela.com/news/detail/000000322484.do",
    "웹 재검색",
  ],
  [
    "NEWS-GH-20221125-01",
    new Date("2022-11-25"),
    "반세기 최악 가뭄…\"무릎높이 양파 줄기, 아직 손바닥만 해\"",
    "한겨레/네이트",
    "전남 고흥군",
    "고흥군",
    "농업",
    "고흥 금산면 양파 모종 생육부진, 전남 일대 최악 가뭄 르포",
    "고흥 양파 생육 피해를 직접 다루는 핵심 기사",
    "직접",
    "사용",
    "고흥 양파 현장",
    "https://news.nate.com/view/20221125n04590",
    "기존 CSV + 웹 재검색",
  ],
  [
    "NEWS-GH-20221129-01",
    new Date("2022-11-29"),
    "최악의 가뭄…섬지역은 초비상",
    "JJC 지방자치TV",
    "전남 고흥군",
    "고흥군 거금도",
    "물공급",
    "거금도 가뭄, 하천 고갈, 과수 생육 악영향, 상수원 저수율 우려",
    "고흥군 도서지역 물부족을 직접 다룸",
    "직접",
    "사용 후보",
    "고흥 도서 물공급",
    "https://www.jjctv.co.kr/article/view/jjc202211290001?d=pc",
    "웹 재검색",
  ],
  [
    "NEWS-GH-20230213-01",
    new Date("2023-02-13"),
    "고흥군, 봄 가뭄 대비 용수공급 대책 추진…69억원 투입",
    "뉴스핌",
    "전남 고흥군",
    "고흥군",
    "농업",
    "저수지 준설, 관정 개발, 양수장 설치, 둠벙 준설, 저수율 낮은 저수지 양수",
    "2023년 봄 영농 전 고흥군 가뭄 대응 기사",
    "직접",
    "사용",
    "고흥 2023 봄가뭄",
    "https://www.newspim.com/news/view/20230213000420",
    "웹 재검색",
  ],
  [
    "NEWS-HC-20220302-01",
    new Date("2022-03-02"),
    "합천군, 봄철 양파·마늘 관수·비배 관리 지도 강화",
    "브릿지경제",
    "경남 합천군",
    "합천군",
    "농업",
    "장기간 가뭄, 양파·마늘 생육재생기 관수·비배 관리 지도",
    "합천 양파·마늘 가뭄 영향 직접 기사",
    "직접",
    "사용",
    "합천 봄가뭄 양파마늘",
    "https://www.viva100.com/20220302010000503?site_preference=normal",
    "웹 재검색",
  ],
  [
    "NEWS-HC-20220303-01",
    new Date("2022-03-03"),
    "\"농사 어쩌나\"···역대급 가뭄에 지자체 비상",
    "서울경제/네이트",
    "경남 합천군",
    "합천군",
    "농업",
    "겨울가뭄, 경남 마늘·양파 생육부진 우려, 합천·달성 산불 언급",
    "합천 농업 피해 직접성은 낮으나 경남 월동작물 가뭄 맥락 보조 가능",
    "보조",
    "사용 후보",
    "경남 겨울가뭄",
    "https://news.nate.com/view/20220303n35760",
    "웹 재검색",
  ],
  [
    "NEWS-HC-20220523-01",
    new Date("2022-05-23"),
    "합천군, 5~6월 모내기철 가뭄대비 선제적 적극 대응",
    "쿠키뉴스/네이트",
    "경남 합천군",
    "합천군",
    "농업",
    "강수량 평년 대비 53% 미만, 하천굴착, 물덤벙 설치, 임시 양수기 지원",
    "합천군 영농기 가뭄대응 직접 기사",
    "직접",
    "사용",
    "합천 영농기 가뭄",
    "https://news.nate.com/view/20220523n39346",
    "웹 재검색",
  ],
  [
    "NEWS-HC-20220525-01",
    new Date("2022-05-25"),
    "5개월간 강수량 21㎜… 합천군, 가뭄 대책 마련 분주",
    "국제신문",
    "경남 합천군",
    "합천군",
    "농업",
    "5개월 강수량 21.6mm, 저수율 저하, 농업용수 공급 종합대책",
    "합천군 강수량·저수율·농업용수 대응이 직접 확인됨",
    "직접",
    "사용",
    "합천 영농기 가뭄",
    "https://www.kookje.co.kr/news2011/asp/newsbody.asp?code=0300&key=20220525.99099007309",
    "웹 재검색",
  ],
  [
    "NEWS-HC-20220530-01",
    new Date("2022-05-30"),
    "경남 합천군, 가뭄대책 ‘예비비 10억원’ 긴급 투입",
    "매일신문",
    "경남 합천군",
    "합천군",
    "농업",
    "합천댐 저수율 35.6%, 337개 저수지 평균 60% 이하, 관정 개발·하천굴착·살수차",
    "합천군 단위 예비비 투입 및 농업용수 확보 기사",
    "직접",
    "사용",
    "합천 예비비",
    "https://www.imaeil.com/page/view/2022053013393025142",
    "웹 재검색",
  ],
  [
    "NEWS-HC-20220603-01",
    new Date("2022-06-03"),
    "지난달 강수량 '평년의 6%'…정부 가뭄대책 긴급 점검",
    "연합뉴스",
    "경남 합천군",
    "합천군",
    "농업",
    "정부 가뭄 대책 점검 대상 4개 시군 중 합천군 포함, 마늘·양파 등 밭작물 피해 우려",
    "전국 기사지만 합천군이 점검 대상에 포함되어 보조 근거로 사용 가능",
    "보조",
    "사용 후보",
    "정부 가뭄점검",
    "https://www.yna.co.kr/view/AKR20220603113400530",
    "웹 재검색",
  ],
  [
    "NEWS-HC-20220605-01",
    new Date("2022-06-05"),
    "밥상물가에 불 놓은 가뭄…5월 강수량 평년 대비 5.9%",
    "채널A",
    "경남 합천군",
    "합천군",
    "농업",
    "합천군 농민 인터뷰, 밀 이삭 피해, 양파 가격 급등 등 밭작물 영향",
    "합천 현장 인터뷰가 있으나 양파 직접 피해보다는 밭작물 보조 근거",
    "보조",
    "사용 후보",
    "합천 밭작물",
    "https://ichannela.com/news/detail/000000300239.do",
    "웹 재검색",
  ],
  [
    "NEWS-HC-20220614-01",
    new Date("2022-06-14"),
    "올해 경남 누적강수량 평년 55%… 가뭄 대비 농업용수 공급 총력전",
    "국제신문",
    "경남",
    "합천군 포함 가능",
    "농업",
    "경남도·시군·농어촌공사 급수대책, 경남 누적강수량 평년 55.5%",
    "합천 직접 기사보다 경남 광역 보조자료에 가까움",
    "광역",
    "보조",
    "경남 가뭄",
    "https://www.kookje.co.kr/news2011/asp/newsbody.asp?code=0300&key=20220614.99099003458",
    "웹 재검색",
  ],
  [
    "NEWS-HC-20220616-01",
    new Date("2022-06-16"),
    "법무부 청소년범죄예방위원 합천지구위원회 농촌일손돕기",
    "매일신문",
    "경남 합천군",
    "합천군",
    "농업",
    "율곡면 양파수확 일손돕기, 가뭄 등으로 양파수확량 감소 언급",
    "가뭄피해 기사라기보다 양파 수확량 감소 보조 근거",
    "보조",
    "사용 후보",
    "합천 양파수확량",
    "https://www.imaeil.com/page/view/2022061615462726414",
    "웹 재검색",
  ],
  [
    "NEWS-HC-20221122-01",
    new Date("2022-11-22"),
    "경남 5곳, 석 달 안에 가뭄 ‘경계’…섬 주민 “생수로 연명”",
    "KBS/다음",
    "경남 합천군",
    "합천군",
    "농업",
    "합천 농업용 저수지 저수율 22.8%, 물 사용 중단, 양파·마늘 폐기 우려",
    "합천군 저수율과 월동작물 피해 우려를 직접 다루는 핵심 기사",
    "직접",
    "사용",
    "합천 저수율 양파마늘",
    "https://v.daum.net/v/0jSXWPo5pT",
    "웹 재검색",
  ],
  [
    "NEWS-HC-20221213-01",
    new Date("2022-12-13"),
    "남부권 가뭄 심각…전남·북 저수율 평년 77% 그쳐",
    "농민신문",
    "경남 합천군",
    "합천군",
    "농업",
    "합천군 농업용수 가뭄 단계 전망 상향 가능성, 남부권 가뭄 상황",
    "합천군이 전망 지역에 포함되지만 영향 상세는 제한적",
    "보조",
    "사용 후보",
    "합천 가뭄전망",
    "https://www.nongmin.com/article/20221213368440",
    "웹 재검색",
  ],
  [
    "NEWS-HC-20230215-01",
    new Date("2023-02-15"),
    "경남 합천군, 원활한 농업용수 공급 위한 ‘상생협의체’ 열어, 봄가뭄 선제적 대응",
    "매일신문",
    "경남 합천군",
    "합천군",
    "농업",
    "양파·마늘 생육기 농업용수 공급, 양수장 운영기간 확대, 봄가뭄 대응",
    "2023년 합천군 봄가뭄 대응 직접 기사",
    "직접",
    "사용",
    "합천 2023 봄가뭄",
    "https://www.imaeil.com/page/view/2023021514461549652",
    "웹 재검색",
  ],
];

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

function writeTable(sheet, values, widths = []) {
  const range = sheet.getRangeByIndexes(2, 0, values.length, values[0].length);
  range.values = values;
  range.format.wrapText = true;
  range.format.font = { color: theme.text, size: 10 };
  range.format.borders = { preset: "all", style: "thin", color: theme.border };
  sheet.getRangeByIndexes(2, 0, 1, values[0].length).format = {
    fill: theme.navy,
    font: { bold: true, color: "#FFFFFF", size: 10 },
  };
  widths.forEach((width, index) => {
    sheet.getRangeByIndexes(0, index, 1, 1).format.columnWidth = width;
  });
  sheet.freezePanes.freezeRows(3);
}

const workbook = Workbook.create();

const summary = workbook.worksheets.add("수집요약");
summary.showGridLines = false;
styleTitle(
  summary,
  "고흥군·합천군 가뭄뉴스 재수집 요약",
  "2022~2023년 남부 사례 검증을 위해 웹 검색으로 재수집한 기사 후보입니다. 원문 DB 적재 전 검토용 목록입니다.",
  8,
);
const summaryRows = [
  ["항목", "값", "설명"],
  ["전체 후보 기사", null, "후보기사 시트의 총 행 수"],
  ["고흥군 관련", null, "지역=전남 고흥군"],
  ["합천군 관련", null, "지역=경남 합천군"],
  ["직접 기사", null, "연결수준=직접"],
  ["사용 판정", null, "사용으로 분류한 기사"],
  ["중복검토", null, "같은 보도자료/내용으로 중복 검토가 필요한 기사"],
  ["2022년 기사", null, "기사일자 기준"],
  ["2023년 기사", null, "기사일자 기준"],
  ["주의", "합천은 직접 기사와 광역·보조 기사가 섞여 있음", "최종 등급 산출 전 직접/보조 구분 유지 필요"],
];
writeTable(summary, summaryRows, [24, 30, 82]);

const candidate = workbook.worksheets.add("후보기사");
candidate.showGridLines = false;
styleTitle(
  candidate,
  "후보기사",
  "검색으로 재수집한 기사 후보입니다. 기사 본문 전체가 아니라 검증에 필요한 메타데이터와 영향 요약만 기록했습니다.",
  17,
);
const header = [
  "article_id",
  "기사일자",
  "연도",
  "연월",
  "뉴스제목",
  "언론사",
  "표준지역명",
  "상세지역",
  "영향분야",
  "영향요약",
  "검증활용근거",
  "연결수준",
  "사용판정",
  "중복그룹",
  "URL",
  "수집경로",
  "비고",
];
const data = articles.map((row) => [
  row[0],
  row[1],
  null,
  null,
  ...row.slice(2),
  "",
]);
writeTable(candidate, [header, ...data], [
  24,
  12,
  8,
  10,
  54,
  16,
  18,
  18,
  12,
  56,
  56,
  12,
  14,
  22,
  62,
  18,
  24,
]);
const endRow = articles.length + 3;
candidate.getRange("C4").formulas = [["=YEAR(B4)"]];
candidate.getRange(`C4:C${endRow}`).fillDown();
candidate.getRange("D4").formulas = [["=TEXT(B4,\"yyyy-mm\")"]];
candidate.getRange(`D4:D${endRow}`).fillDown();
candidate.getRange(`B4:B${endRow}`).format.numberFormat = "yyyy-mm-dd";
candidate.getRange(`C4:C${endRow}`).format.numberFormat = "#,##0";
candidate.getRange(`L4:L${endRow}`).dataValidation = { rule: { type: "list", values: ["직접", "보조", "광역"] } };
candidate.getRange(`M4:M${endRow}`).dataValidation = { rule: { type: "list", values: ["사용", "사용 후보", "중복검토", "보조", "제외", "확인 필요"] } };

const monthly = workbook.worksheets.add("월별집계");
monthly.showGridLines = false;
styleTitle(
  monthly,
  "월별집계",
  "후보기사를 사례 검증용 월별 근거로 묶은 표입니다. 직접 기사와 사용 후보를 구분해 봅니다.",
  9,
);
const monthKeys = [
  ["전남 고흥군", "2022-05"],
  ["전남 고흥군", "2022-07"],
  ["전남 고흥군", "2022-08"],
  ["전남 고흥군", "2022-10"],
  ["전남 고흥군", "2022-11"],
  ["전남 고흥군", "2023-02"],
  ["경남 합천군", "2022-03"],
  ["경남 합천군", "2022-05"],
  ["경남 합천군", "2022-06"],
  ["경남 합천군", "2022-11"],
  ["경남 합천군", "2022-12"],
  ["경남 합천군", "2023-02"],
];
const monthlyRows = [
  ["표준지역명", "연월", "전체 후보", "직접 기사", "사용 기사", "농업 기사", "물공급 기사", "대표 영향", "검토 메모"],
  ...monthKeys.map(([region, ym]) => [region, ym, null, null, null, null, null, "", ""]),
];
writeTable(monthly, monthlyRows, [18, 10, 12, 12, 12, 12, 12, 64, 40]);
const monthlyEnd = monthKeys.length + 3;
monthly.getRange("C4").formulas = [[`=COUNTIFS('후보기사'!$G$4:$G$${endRow},A4,'후보기사'!$D$4:$D$${endRow},B4)`]];
monthly.getRange(`C4:C${monthlyEnd}`).fillDown();
monthly.getRange("D4").formulas = [[`=COUNTIFS('후보기사'!$G$4:$G$${endRow},A4,'후보기사'!$D$4:$D$${endRow},B4,'후보기사'!$L$4:$L$${endRow},"직접")`]];
monthly.getRange(`D4:D${monthlyEnd}`).fillDown();
monthly.getRange("E4").formulas = [[`=COUNTIFS('후보기사'!$G$4:$G$${endRow},A4,'후보기사'!$D$4:$D$${endRow},B4,'후보기사'!$M$4:$M$${endRow},"사용")`]];
monthly.getRange(`E4:E${monthlyEnd}`).fillDown();
monthly.getRange("F4").formulas = [[`=COUNTIFS('후보기사'!$G$4:$G$${endRow},A4,'후보기사'!$D$4:$D$${endRow},B4,'후보기사'!$I$4:$I$${endRow},"농업")`]];
monthly.getRange(`F4:F${monthlyEnd}`).fillDown();
monthly.getRange("G4").formulas = [[`=COUNTIFS('후보기사'!$G$4:$G$${endRow},A4,'후보기사'!$D$4:$D$${endRow},B4,'후보기사'!$I$4:$I$${endRow},"물공급")`]];
monthly.getRange(`G4:G${monthlyEnd}`).fillDown();

const review = workbook.worksheets.add("검토필요");
review.showGridLines = false;
styleTitle(
  review,
  "검토필요",
  "재수집 후 바로 결정해야 하는 쟁점입니다.",
  5,
);
const reviewRows = [
  ["우선순위", "쟁점", "현재 확인", "필요 조치", "담당/비고"],
  ["P0", "합천군 2023 기사 부족", "2023-02-15 봄가뭄 대응 기사 1건 중심", "DB/Naver/KINDS 등 추가 검색 필요", ""],
  ["P0", "고흥 기사 중 중복 여부", "2022-05-30 봄가뭄 대책 기사가 연합뉴스/아시아경제로 중복 가능", "대표기사 1건만 사용할지 결정", ""],
  ["P0", "광역/보조 기사 활용", "정부·경남 광역 기사는 합천 직접 피해 근거로는 약함", "직접/보조 구분을 최종 통합표에 유지", ""],
  ["P0", "뉴스 등급 산출", "기사 수만으로 등급 산출 시 월별 편향 가능", "직접 기사 수 + 영향상세 조합 기준 마련", ""],
];
writeTable(review, reviewRows, [12, 28, 50, 56, 24]);

summary.getRange("B4").formulas = [[`=COUNTA('후보기사'!A4:A${endRow})`]];
summary.getRange("B5").formulas = [[`=COUNTIF('후보기사'!G4:G${endRow},"전남 고흥군")`]];
summary.getRange("B6").formulas = [[`=COUNTIF('후보기사'!G4:G${endRow},"경남 합천군")`]];
summary.getRange("B7").formulas = [[`=COUNTIF('후보기사'!L4:L${endRow},"직접")`]];
summary.getRange("B8").formulas = [[`=COUNTIF('후보기사'!M4:M${endRow},"사용")`]];
summary.getRange("B9").formulas = [[`=COUNTIF('후보기사'!M4:M${endRow},"중복검토")`]];
summary.getRange("B10").formulas = [[`=COUNTIF('후보기사'!C4:C${endRow},2022)`]];
summary.getRange("B11").formulas = [[`=COUNTIF('후보기사'!C4:C${endRow},2023)`]];
summary.getRange("B4:B11").format.numberFormat = "#,##0";

const inspect = await workbook.inspect({
  kind: "table",
  sheetId: "수집요약",
  range: "A3:C12",
  include: "values,formulas",
  tableMaxRows: 12,
  tableMaxCols: 3,
  maxChars: 4000,
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
for (const sheetName of ["수집요약", "후보기사", "월별집계", "검토필요"]) {
  const preview = await workbook.render({ sheetName, autoCrop: "all", scale: 1, format: "png" });
  await fs.writeFile(`${supportDir}/고흥합천뉴스_${sheetName}_v1.png`, new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(outputPath);
