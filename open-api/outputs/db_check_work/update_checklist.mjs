import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = "/Users/jeongseok/Downloads/실측가뭄_과제_세부작업_체크리스트.xlsx";
const outputDir = "/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work";
const outputPath = `${outputDir}/실측가뭄_과제_세부작업_체크리스트_DB확인_v1.xlsx`;

const allowed = new Set(["완료", "진행 중", "미착수", "막힘"]);

const rows = [
  ["T1-1", "완료", "검증 대상 2건(2022~2023 남부/광주·전남, 2025 강릉)이 지시문에서 확정됨", "없음"],
  ["T1-2", "완료", "분석 기간이 남부 2022.01~2023.12, 강릉 2025.01~12로 확정됨", "없음"],
  ["T1-3", "완료", "분석 지역이 광주·전남 / 강릉 및 강원 관련지역으로 확정됨", "세부 시군구 포함 범위는 통합표 설계 시 명시"],
  ["T1-4", "완료", "체크리스트와 지시문 모두 월 단위 연결을 기본 단위로 지정", "일 자료는 월 집계 규칙 필요"],
  ["T1-5", "진행 중", "DB 보유 테이블은 확인했으나 남부 양파·2025 강릉 뉴스 등 결손으로 최종 사용 데이터 확정 전", "사례별 사용/제외 데이터 목록 확정"],
  ["T1-6", "완료", "기술 결과-뉴스 영향-정형 데이터 변화 비교 흐름이 지시문에 정의됨", "실제 비교표 설계에 반영"],
  ["T1-7", "진행 중", "일치/부분일치/불일치 큰 틀은 있으나 정량 임계값과 예외 처리 기준 미정", "판정 기준서 작성"],
  ["T2-1", "미착수", "사례별 월 목록 테이블/파일이 DB 또는 산출물로 확인되지 않음", "남부 24개월, 강릉 12개월 기준 월 목록 생성"],
  ["T2-2", "미착수", "남부/강릉 사례 고유번호 테이블 또는 매핑 산출물 없음", "case_id 부여"],
  ["T2-3", "미착수", "연도·월·연월 기준 마스터 산출물 없음", "case_month 테이블/시트 생성"],
  ["T2-4", "진행 중", "지역 컬럼은 있으나 광주광역시/전라남도/강원특별자치도/강릉/대관령 등 표현이 혼재", "표준 지역명·시도 약칭·시군구 매핑 작성"],
  ["T2-5", "미착수", "분석 포함/제외 월 플래그 산출물 없음", "사례별 포함월 및 결측월 표시"],
  ["T2-6", "진행 중", "연월·지역 연결 가능성은 확인했으나 남부 양파, 강릉 뉴스/등급 공백으로 완전 연결 불가", "연결키와 결손 규칙 정의"],
  ["T3-1", "완료", "뉴스 테이블 위치 확인: DRGHT_MONITORING_DAMAGE, drought_article, agg_* 및 wild_fire_news_articles", "없음"],
  ["T3-2", "진행 중", "기사 날짜 컬럼은 있으나 YYMMDD 문자열/DATE가 혼재하고 신규 데이터는 2025-12 이후 중심", "날짜 파싱 및 월 필드 표준화"],
  ["T3-3", "진행 중", "남부 뉴스는 리스트 문자열 지역으로 연결 가능, 강릉 2025.01~11 가뭄뉴스는 없음", "지역 리스트 파싱 및 2025 강릉 뉴스 보강"],
  ["T3-4", "진행 중", "IPT_CLA 및 impact_code(A1~A8) 분류가 존재하나 구형/신형 체계 통합 필요", "분야 코드 매핑 통일"],
  ["T3-5", "진행 중", "DMG_DTL/damage_detail은 존재하나 사례별 영향 문장 정리 산출물은 없음", "기사별 영향 내용 정리"],
  ["T3-6", "진행 중", "제목·본문·피해상세는 존재하나 대표 근거 문장 선별 전", "월별 근거 문장 추출"],
  ["T3-7", "진행 중", "agg_period_grade 등급은 있으나 2025-12~2026-08 중심이라 사례 기간 대부분 미포함", "22~23 및 2025.01~11 등급 재산출"],
  ["T3-8", "미착수", "월별 대표기사 선정 산출물 없음", "주요 월별 대표기사 1~3건 선정"],
  ["T3-9", "진행 중", "기사 1건 단위 데이터는 있으나 구형 지역/분야 정규화와 강릉 기간 보강 필요", "기사 상세 테이블 표준화"],
  ["T3-10", "진행 중", "일/월 집계 테이블은 신규 2025-12 이후만 있고 남부 구형 뉴스 월 집계는 별도 계산 필요", "사례 기간 월별 집계 생성"],
  ["T4-1", "완료", "정형 후보 테이블과 범위 확인 완료: 신선물가, 고랭지배추 가격/예측, 수력, ASOS/SPI 등", "사용/제외 판정 반영"],
  ["T4-2", "완료", "신선식품지수는 광주·전남 2022~2023 24개월, 강원특별자치도 2025 12개월 값 확인", "2025-08 동일 중복 12건 제거 규칙 적용"],
  ["T4-3", "완료", "과실지수도 동일 테이블에서 사례 기간 월별 값 확인", "2025-08 동일 중복 제거"],
  ["T4-4", "완료", "채소지수도 동일 테이블에서 사례 기간 월별 값 확인", "2025-08 동일 중복 제거"],
  ["T4-5", "막힘", "강릉/대관령 고랭지배추 실제 일별 가격은 있으나 남부 양파/광주·전남 가격 0건", "남부 양파 도매가격 수집 또는 대체 품목 결정"],
  ["T4-6", "막힘", "강릉/대관령 고랭지배추 예측은 있으나 2025년 5월 월간 예측 누락, 남부 양파 예측 0건", "5월 처리 및 양파 예측 생성 여부 결정"],
  ["T4-7", "진행 중", "예측 테이블 일부 변화율은 저장되어 있으나 실제 가격·물가지수 전월/전년동월 변화율 통합 산출물 없음", "월별 변화율 계산"],
  ["T4-8", "막힘", "생산량/재배면적 컬럼은 확인되지 않고 반입량·거래량만 존재", "품목·지역별 생산량/재배면적 외부 수집"],
  ["T4-9", "막힘", "생산량 원자료가 없어 전년 대비 생산량 변화율 계산 불가", "생산량 확보 후 계산"],
  ["T4-10", "완료", "수력 데이터는 확인했으나 4개 댐은 남부/강릉 사례와 직접 공간연관 낮아 보조자료 또는 적용 부적합으로 판정", "검증 본표에는 선택 포함"],
  ["T4-11", "막힘", "정형 데이터별 연월·지역은 있으나 양파/생산량 누락과 통합 테이블 부재", "정형 통합표 생성 전 결손 보완"],
  ["T5-1", "막힘", "agg_period_grade는 2025-12~2026-08 중심, 두 사례 전체 기간 등급 결과 미확보", "사례 기간 기술결과 재산출/적재"],
  ["T5-2", "막힘", "월별 등급은 신규 뉴스 기간만 존재하고 22~23 남부·2025.01~11 강릉 미포함", "월별 결과 재산출"],
  ["T5-3", "진행 중", "영향 분야 A1~A8 구조는 있으나 사례 기간 결과가 부족", "구형 IPT_CLA와 A1~A8 매핑"],
  ["T5-4", "진행 중", "agg_daily_region_field_count와 break 테이블은 원값/등급구간 구조가 있으나 기간 제한", "사례 기간 원값 보존 산출물 생성"],
  ["T5-5", "막힘", "각 월의 분야별 가뭄 등급이 사례 기간에 대해 완비되지 않음", "22~23 및 2025년 월별 등급 산출"],
  ["T5-6", "진행 중", "Jenks 등급구간 테이블은 있으나 사례 기간 구간이 없음", "재산출 기준과 구간 기록"],
  ["T5-7", "진행 중", "관련 파이프라인/lineage 문서는 있으나 사례별 결과 원본 파일 매핑 산출물 없음", "원본 파일·쿼리·실행일 기록"],
  ["T5-8", "막힘", "사례·월·지역·분야별 기술 결과 조회 구조가 사례 기간에 대해 완성되지 않음", "기술결과 최종 테이블 생성"],
  ["T6-1", "막힘", "남부는 뉴스/신선물가는 있으나 양파 가격·생산량·기술등급이 부족", "P0 데이터 보강 후 통합"],
  ["T6-2", "막힘", "강릉은 고랭지배추/신선물가는 있으나 2025.01~11 뉴스 영향·등급 공백, 월간 예측 5월 누락", "뉴스/등급 보강 및 5월 처리"],
  ["T6-3", "미착수", "남부 주요 시기 선정 산출물 없음", "등급/뉴스/정형 통합 후 선정"],
  ["T6-4", "미착수", "강릉 주요 시기 선정 산출물 없음", "등급/뉴스/정형 통합 후 선정"],
  ["T6-5", "미착수", "기술 결과와 뉴스 비교표 없음", "월별 비교 수행"],
  ["T6-6", "미착수", "기술 결과와 정형 데이터 비교표 없음", "가격·물가·생산량 비교 수행"],
  ["T6-7", "미착수", "일치 사례 정리 산출물 없음", "비교 후 정리"],
  ["T6-8", "미착수", "불일치 사례 정리 산출물 없음", "비교 후 정리"],
  ["T6-9", "미착수", "월별 검증 판정표 없음", "일치/부분/불일치 판정"],
  ["T6-10", "미착수", "검증 결과 해석문 없음", "결과 해석 작성"],
  ["T6-11", "미착수", "월별 통합 검증표 없음", "사례별 통합표 작성"],
  ["T6-12", "미착수", "사례별 요약표 없음", "핵심 결과 요약"],
  ["T7-1", "미착수", "사례 개요 문서 산출물 없음", "개요 작성"],
  ["T7-2", "미착수", "검증 방법 문서 산출물 없음", "방법 작성"],
  ["T7-3", "미착수", "남부 결과 원고 없음", "남부 검증 후 작성"],
  ["T7-4", "미착수", "강릉 결과 원고 없음", "강릉 검증 후 작성"],
  ["T7-5", "미착수", "정형 데이터 그래프 없음", "가격·물가 그래프 작성"],
  ["T7-6", "미착수", "뉴스 결과 시각화 없음", "월별 기사/영향 시각화"],
  ["T7-7", "미착수", "통합 시간흐름 그림 없음", "정형·비정형 통합 그림 작성"],
  ["T7-8", "미착수", "사례별 결론 초안 없음", "핵심 결론 도출"],
  ["T7-9", "미착수", "검증 한계 정리 없음", "데이터 결손/해석 한계 작성"],
  ["T7-10", "미착수", "연구개발계획서 목표 연결 문서 없음", "목표 대응표 작성"],
  ["T7-11", "미착수", "정량 성과 정리 없음", "성과 항목 정리"],
  ["T7-12", "미착수", "워크숍 발표자료 반영본 없음", "발표용 표·그림 반영"],
  ["T8-1", "진행 중", "플랫폼 API 후보 항목은 있으나 사례 검증용 표시 항목 대응표 미완성", "화면 항목-DB 컬럼 매핑 확정"],
  ["T8-2", "미착수", "가시화 업체 전달용 데이터 패키지 없음", "통합 데이터 전달"],
  ["T8-3", "진행 중", "일부 API는 연도 조회 가능하나 사례 통합 조회는 없음", "사례별 연도 조회 검증"],
  ["T8-4", "진행 중", "일부 API는 월 조회 가능하나 통합 검증 월 조회는 없음", "월별 통합 조회 검증"],
  ["T8-5", "진행 중", "등급 데이터 구조는 있으나 사례 기간 등급/화면 일치 확인 불가", "등급 재산출 후 화면 대조"],
  ["T8-6", "진행 중", "뉴스 테이블은 있으나 2025 강릉 공백과 구형/신형 체계 차이 존재", "뉴스 표시 데이터 표준화"],
  ["T8-7", "진행 중", "가격·물가 API 후보는 있으나 사례 통합 정형 데이터 표시 확인 전", "정형 데이터 화면 값 대조"],
  ["T8-8", "미착수", "주요 월 원본-플랫폼 직접 비교 산출물 없음", "샘플 월 검수"],
  ["T8-9", "미착수", "오류사항 목록 없음", "검수 후 오류 목록 작성"],
  ["T8-10", "미착수", "시범운영 결과 정리 없음", "워크숍용 화면/결과 확보"],
];

for (const [, status] of rows) {
  if (!allowed.has(status)) throw new Error(`Invalid status: ${status}`);
}

const dbSummary = [
  ["데이터 종류", "테이블", "주요 컬럼", "기간", "지역", "주요 내용", "판정"],
  ["농산물", "daily_market_trends", "trend_date, location, item, variety, market_volume, avg_wholesale_price", "2022-07-01~2027-04-06", "강릉, 대관령", "고랭지배추 실제 일별 평균 도매가·반입량. 2025년 365일×2지역, 가격 NULL 6건", "강릉 사례 진행 중"],
  ["농산물", "daily_price_predictions", "prediction_date, location, item, variety, predicted_price, rate_of_change_from_prev_year", "2022-07-01~2027-04-06", "강릉, 대관령", "고랭지배추 일별 예측 가격. 2025년 12개월이나 5월 13일·6월 27일만 존재", "강릉 사례 진행 중"],
  ["농산물", "monthly_market_predictions", "prediction_year, prediction_month, predicted_price_*, predicted_volume_*", "2022-07~2026-12", "강릉, 대관령", "월별 가격·반입량 예측. 2025년은 5월 누락, 남부 양파 0건", "부분 가능/남부 막힘"],
  ["농산물", "drought_impact_crop_price_daily", "transaction_date, average_price, total_volume, item_name, origin_province, origin_city", "2024-08-01~2025-09-30", "강원도 10개 시군", "고냉지배추 실거래 가격·물량 427건. 양파/광주·전남 0건", "강릉 보조/남부 막힘"],
  ["신선물가지수", "drought_impact_fresh_food_price_index", "province, base_date, fresh_food_index, fresh_vegetable_index, fresh_fruit_index", "2022-01-01~2026-05-01", "19개 시도/전국", "광주·전남 2022~2023 24개월, 강원 2025 12개월 확인. 2025-08 동일 중복 12건", "사용 가능"],
  ["수력발전량", "dam_daily_generation", "dam_name, dam_code, generation_date, planned_mwh, actual_mwh", "2021-01-01~2026-06-06", "대청, 소양강, 충주, 합천", "일별 계획/실제 발전량 완비. 공간적으로 남부/강릉 직접 검증자료로는 약함", "보조자료/적용 부적합"],
  ["수력발전량", "dam_monthly_generation", "dam_name, year, month, planned_mwh, actual_mwh", "2021-01~2026-06", "대청, 소양강, 충주, 합천", "월별 계획/실제 발전량. 같은 월 3중복 존재", "정리 필요"],
  ["수력발전량", "dam_monthly_predictions", "dam_name, year, month, predicted_power_generation_*, predicted_water_storage_*", "2022-01~2026-09", "대청, 소양강, 충주, 합천", "월별 발전량/저수량 예측 56개월×4댐, 값 완비", "보조자료"],
  ["수력발전량", "WT_POWER, WT_POWER_INFO", "STN_NAME, ADDRESS, CRT_YMD, POWER", "2023-07-26~2023-12-26", "도암(강릉), 보성강(전남) 등 10개", "구형 발전소 발전량. 2025년 없음, 22~23 전체기간 불충분", "제한적 보조"],
  ["뉴스 비정형", "DRGHT_MONITORING_DAMAGE", "YYMMDD, NEWS_TIT, NEWS_URL, NEWS_ART, SIDO_NM, SGG_NM, IPT_CLA, DMG_DTL", "1990-08-23~2023-07-03", "리스트 문자열 지역", "22~23 남부 관련 76건/13개월. 제목·본문·영향분야·피해상세 존재", "남부 진행 중"],
  ["뉴스 비정형", "drought_article", "published_at, sido, sigungu, title, body, impact_code, damage_detail, link", "2025-12-18~2026-08-02", "16개 시도, 149개 시군구", "기사 15,483건, 영향분야 A1~A8. 2025 강릉은 12월 180건만 확인", "강릉 사례 막힘"],
  ["뉴스 비정형", "agg_daily_count, agg_daily_region_field_count, agg_period_grade, agg_period_grade_break", "stat_date/bucket, sido/dim_key, impact_code, article_count/grade/lower_bound", "2025-12-18~2026-08-02", "시도 단위", "월+지역+분야 등급 구조는 있으나 사례 기간 대부분 미포함", "재산출 필요"],
  ["뉴스 비정형", "wild_fire_news_articles", "publish_date, title, link_url, province_name, city_name, category, sentiment, keywords", "2025-09-02~2025-12-31", "16개 시도", "산불/기상 등 기사 60건. 강원 10건이나 가뭄 영향 구조 아님", "가뭄 검증 부적합"],
  ["기상/SPI 보조", "drought_impact_asos_gangneung, drought_impact_asos_daegwallyeong", "tm, avgTa, sumRn 등 ASOS 컬럼", "2023-01-01~2026-07-31", "강릉, 대관령", "2025 강릉 기상 보조자료 가능", "보조자료"],
  ["기상/SPI 보조", "drought_impact_*_spi_index", "observed_date, SPI1~SPI24", "2024-01-01~2025-10-23 등", "춘천, 충주, 대전, 합천", "강릉/광주·전남 SPI는 확인되지 않음", "직접 적용 어려움"],
];

const caseFindings = [
  ["사례", "항목", "DB 확인 결과", "판정", "추가 필요"],
  ["22~23 남부", "뉴스 영향", "DRGHT_MONITORING_DAMAGE에서 광주·전남 관련 76건, 2022-03~2023-05 13개월 확인", "진행 중", "지역 리스트 파싱, 월별 대표기사/영향등급 산출"],
  ["22~23 남부", "신선물가지수", "광주광역시·전라남도 2022~2023 24개월 값 확인", "사용 가능", "전월/전년동월 변화율 계산"],
  ["22~23 남부", "양파 가격/예측", "현행 농산물 테이블 4종에서 양파·광주·전남 조건 0건", "막힘", "양파 도매가격/예측 수집 또는 대체 지표 결정"],
  ["22~23 남부", "생산량/재배면적", "생산량·재배면적 컬럼/테이블 확인 안 됨", "막힘", "외부 통계 수집"],
  ["22~23 남부", "수력", "보성강/칠보 등 구형 발전소 자료는 2023.07~12만 존재, 새 댐 4종은 공간연관 낮음", "보조 또는 제외", "사용 여부 명시"],
  ["25 강릉", "고랭지배추 실제", "daily_market_trends 2025년 730행, 365일×2지역, 가격 NULL 6건", "진행 중", "가격 결측 6건 처리 및 월 집계"],
  ["25 강릉", "고랭지배추 예측", "daily_price_predictions 2025년 688행, 월간 예측은 11개월(5월 누락)", "진행 중", "5월 예측 공백 처리"],
  ["25 강릉", "신선물가지수", "강원특별자치도 2025년 12개월 값 확인, 8월 동일 중복 12건", "사용 가능", "중복 제거"],
  ["25 강릉", "뉴스 영향", "drought_article은 2025-12 강릉/강원만 있고 2025.01~11 없음; 구형 뉴스도 2025년 0건", "막힘", "2025 강릉 가뭄 뉴스 재수집/재처리"],
  ["25 강릉", "가뭄영향 등급", "agg_period_grade는 2025-12부터라 2025.01~11 검증 불가", "막힘", "사례 기간 등급 재산출"],
  ["25 강릉", "수력", "도암/강릉 구형 발전소는 2023년만, 새 댐은 소양강 등 광역 보조 수준", "보조 또는 제외", "공간 적용성 결정"],
];

const remaining = [
  ["우선순위", "ID", "남은 작업", "예상 작업내용", "선행 작업"],
  ["P0", "T2-1~T2-6", "사례-연월-지역 기준 마스터 생성", "두 사례 월 목록, case_id, 표준 지역명, 포함/제외 플래그 작성", "없음"],
  ["P0", "T3-2~T3-10", "뉴스 데이터 표준화 및 월별 집계", "구형/신형 뉴스 날짜·지역·분야 통합, 대표기사·영향내용·등급 산출", "T2 지역 기준"],
  ["P0", "T4-5~T4-9", "남부 양파 및 생산량/재배면적 결손 해결", "양파 도매가격/예측/생산량 수집 또는 대체 지표 의사결정", "검증 데이터 선정"],
  ["P0", "T4-7", "정형 변화율 계산", "물가지수·가격의 전월/전년동월 변화율 생성", "T4-2~T4-6"],
  ["P0", "T5-1~T5-8", "사례 기간 가뭄영향 등급 재산출", "2022~2023 남부, 2025 강릉 월×지역×분야 등급과 원값/구간 저장", "뉴스 표준화"],
  ["P0", "T6-1~T6-12", "월별 통합 검증표 및 판정", "사례·연월·지역 기준으로 기술등급, 뉴스 영향, 정형값을 연결해 일치/부분/불일치 판정", "T2~T5"],
  ["P1", "T7-1~T7-12", "기술보고서·워크숍 자료 작성", "사례 개요, 방법, 결과표/그래프, 한계, 결론, 발표자료 반영", "T6 통합검증 결과"],
  ["P2", "T8-1~T8-10", "플랫폼 시범운영 및 화면 검수", "플랫폼 항목 대응표, 업체 전달, 연/월 조회, 등급·뉴스·정형값 화면 대조", "T6 확정 데이터"],
];

const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);
const checklist = workbook.worksheets.getItem("실측가뭄_체크리스트");

const ids = checklist.getRange("A5:A80").values.map((r) => String(r[0] ?? ""));
const byId = new Map(rows.map(([id, status, reason, todo]) => [id, { status, reason, todo }]));

const statusMatrix = [];
const reasonMatrix = [];
const todoMatrix = [];
for (const id of ids) {
  const item = byId.get(id);
  if (!item) throw new Error(`Missing status for ${id}`);
  statusMatrix.push([item.status]);
  reasonMatrix.push([item.reason]);
  todoMatrix.push([item.todo]);
}

checklist.getRange("E5:E80").values = statusMatrix;
checklist.getRange("F4:G4").values = [["확인 근거", "추가 작업"]];
checklist.getRange("F5:F80").values = reasonMatrix;
checklist.getRange("G5:G80").values = todoMatrix;
checklist.getRange("A4:G80").format = {
  font: { color: "#1F2937" },
  borders: { preset: "all", style: "thin", color: "#9CC2E5" },
};
checklist.getRange("A1:E1").unmerge();
checklist.getRange("A2:E2").unmerge();
checklist.getRange("A1:G1").merge();
checklist.getRange("A2:G2").merge();
checklist.getRange("A4:G4").format = {
  fill: "#5B9BD5",
  font: { bold: true, color: "#FFFFFF" },
};
checklist.getRange("F5:G80").format.wrapText = true;
checklist.getRange("A:A").format.columnWidth = 10;
checklist.getRange("B:B").format.columnWidth = 24;
checklist.getRange("C:C").format.columnWidth = 30;
checklist.getRange("D:D").format.columnWidth = 46;
checklist.getRange("E:E").format.columnWidth = 12;
checklist.getRange("F:F").format.columnWidth = 68;
checklist.getRange("G:G").format.columnWidth = 48;
checklist.freezePanes.freezeRows(4);
checklist.getRange("E5:E80").dataValidation = { rule: { type: "list", values: ["완료", "진행 중", "미착수", "막힘"] } };

const statusByTask = new Map();
for (const [id, status] of rows) {
  const taskNo = id.split("-")[0];
  const name = checklist.getRange(`B${ids.indexOf(id) + 5}`).values[0][0];
  if (!statusByTask.has(name)) {
    statusByTask.set(name, { total: 0, done: 0, doing: 0, todo: 0, blocked: 0 });
  }
  const rec = statusByTask.get(name);
  rec.total += 1;
  if (status === "완료") rec.done += 1;
  if (status === "진행 중") rec.doing += 1;
  if (status === "미착수") rec.todo += 1;
  if (status === "막힘") rec.blocked += 1;
}

const progress = workbook.worksheets.getItem("진행현황");
const progressRows = [["업무", "전체 세부작업", "완료", "진행 중", "미착수", "막힘", "완료율"]];
for (const [name, rec] of statusByTask) {
  progressRows.push([name, rec.total, rec.done, rec.doing, rec.todo, rec.blocked, rec.done / rec.total]);
}
progress.getRange("A1:G9").values = progressRows;
progress.getRange("A1:G9").conditionalFormats.deleteAll();
progress.getRange("A1:G1").format = { fill: "#5B9BD5", font: { bold: true, color: "#FFFFFF" } };
progress.getRange("A2:G9").format.fill = "#FFFFFF";
progress.getRange("A1:G9").format.borders = { preset: "all", style: "thin", color: "#9CC2E5" };
progress.getRange("B2:F9").format.numberFormat = "#,##0";
progress.getRange("G2:G9").format.numberFormat = "0.0%";
progress.getRange("A:A").format.columnWidth = 32;
progress.getRange("B:F").format.columnWidth = 14;
progress.getRange("G:G").format.columnWidth = 14;

function addSheet(name, matrix, widths) {
  const sheet = workbook.worksheets.add(name);
  const rowCount = matrix.length;
  const colCount = matrix[0].length;
  const range = sheet.getRangeByIndexes(0, 0, rowCount, colCount);
  range.values = matrix;
  sheet.getRangeByIndexes(0, 0, 1, colCount).format = {
    fill: "#1F4E79",
    font: { bold: true, color: "#FFFFFF" },
  };
  range.format.borders = { preset: "all", style: "thin", color: "#D9EAF7" };
  range.format.wrapText = true;
  widths.forEach((width, idx) => {
    sheet.getRangeByIndexes(0, idx, rowCount, 1).format.columnWidth = width;
  });
  sheet.freezePanes.freezeRows(1);
  return sheet;
}

addSheet("DB 현황", dbSummary, [16, 34, 60, 24, 28, 70, 24]);
addSheet("사례별 확인", caseFindings, [16, 24, 72, 18, 48]);
addSheet("남은 작업", remaining, [12, 18, 40, 70, 32]);

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "formula error scan",
  maxChars: 3000,
});
console.log(errors.ndjson);

await fs.mkdir(outputDir, { recursive: true });
for (const sheetName of ["실측가뭄_체크리스트", "진행현황", "DB 현황", "사례별 확인", "남은 작업"]) {
  const preview = await workbook.render({
    sheetName,
    autoCrop: "all",
    scale: 1,
    format: "png",
  });
  await fs.writeFile(`${outputDir}/${sheetName}.png`, new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(outputPath);
