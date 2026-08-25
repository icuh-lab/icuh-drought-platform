# -*- coding: utf-8 -*-
"""Prepare T3 news impact dataset for drought case verification.

Inputs are local reviewed files only. This script does not modify the DB.
It normalizes article-level news into case/month/region/impact structures.
"""

from __future__ import annotations

import ast
import json
import re
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import pandas as pd
from openpyxl import load_workbook


WORKSHOP_DIR = Path("/Users/jeongseok/Desktop/9월실측가뭄워크샵")
SOUTH_CANDIDATE_XLSX = WORKSHOP_DIR / "실측가뭄_고흥합천_가뭄뉴스_재수집_v1.xlsx"
SOUTH_RESULT_XLSX = WORKSHOP_DIR / "실측가뭄과제_drought_news_result_2022_2023_고흥합천_재수집_v1.xlsx"
GANGNEUNG_CSV = Path("/Users/jeongseok/Downloads/실측가뭄과제_drought_news_result_1990_2025_수정_2.csv")
OUTPUT_JSON = Path("/Users/jeongseok/Desktop/workspace_intelliJ/icuh-platform-api/outputs/db_check_work/t3_news_impact_dataset.json")


def parse_listish(value):
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return []
    if isinstance(value, list):
        return [str(v) for v in value]
    text = str(value).strip()
    if not text:
        return []
    if text.startswith("[") and text.endswith("]"):
        try:
            parsed = ast.literal_eval(text)
            if isinstance(parsed, list):
                return [str(v) for v in parsed if str(v).strip()]
        except Exception:
            pass
        text = text.strip("[]")
    if "," in text:
        return [x.strip().strip("'\"") for x in text.split(",") if x.strip().strip("'\"")]
    return [text.strip("'\"")]


def compact_terms(value, max_terms=10):
    items = parse_listish(value)
    out = []
    for item in items:
        item = re.sub(r"\s+", " ", str(item)).strip()
        if item and item not in out:
            out.append(item)
    return ", ".join(out[:max_terms])


def load_south_rows():
    result = pd.read_excel(SOUTH_RESULT_XLSX, sheet_name="drought_news_result")
    candidate = pd.read_excel(SOUTH_CANDIDATE_XLSX, sheet_name="후보기사", header=2)
    candidate = candidate.dropna(subset=["URL"]).copy()
    meta_by_url = {
        str(row["URL"]).strip(): row
        for _, row in candidate.iterrows()
        if str(row.get("URL", "")).strip()
    }

    rows = []
    for idx, row in result.iterrows():
        url = str(row.get("뉴스링크") or "").strip()
        meta = meta_by_url.get(url)
        article_date = pd.to_datetime(row.get("일자"), errors="coerce")
        region = str(meta.get("표준지역명")) if meta is not None else ""
        if not region or region == "nan":
            sigungu = compact_terms(row.get("시군구"))
            region = "전남 고흥군" if "고흥" in sigungu else "경남 합천군" if "합천" in sigungu else sigungu
        province = "전라남도" if "고흥" in region else "경상남도" if "합천" in region else compact_terms(row.get("광역시도"))
        sigungu = "고흥군" if "고흥" in region else "합천군" if "합천" in region else compact_terms(row.get("시군구"))
        impact = str(row.get("영향구분") or (meta.get("영향분야") if meta is not None else "")).strip()
        damage = compact_terms(row.get("피해상세")) or str(meta.get("영향요약") if meta is not None else "").strip()
        connection = str(meta.get("연결수준") if meta is not None else "직접")
        use_status = str(meta.get("사용판정") if meta is not None else "사용")
        basis = str(meta.get("검증활용근거") if meta is not None else "").strip()
        summary = str(meta.get("영향요약") if meta is not None else damage).strip()
        region_role = "핵심" if connection == "직접" else "보조"
        rows.append(
            {
                "article_key": f"SOUTH-{idx+1:03d}",
                "case_id": "CASE_SOUTH_22_23",
                "사례명": "2022~2023 남부 가뭄",
                "기사일자": article_date.strftime("%Y-%m-%d") if pd.notna(article_date) else "",
                "연도": int(article_date.year) if pd.notna(article_date) else None,
                "연월": article_date.strftime("%Y-%m") if pd.notna(article_date) else "",
                "표준지역명": region,
                "시도": province,
                "시군구": sigungu,
                "지역역할": region_role,
                "뉴스링크": url,
                "뉴스제목": str(row.get("뉴스제목") or "").strip(),
                "영향분야": impact,
                "피해상세": damage,
                "영향요약": summary,
                "검증활용근거": basis,
                "본문요약": str(row.get("기사본문") or "").strip()[:500],
                "연결수준": connection,
                "사용판정": use_status,
                "원천": "고흥합천 재수집",
            }
        )
    return rows


def load_gangneung_rows():
    df = pd.read_csv(GANGNEUNG_CSV)
    df["dt"] = pd.to_datetime(df["일자"], errors="coerce")
    df = df[df["dt"].dt.year.eq(2025)].copy()

    selected = []
    for _, row in df.iterrows():
        provinces = parse_listish(row.get("광역시도"))
        sigungus = parse_listish(row.get("시군구"))
        province_text = " ".join(provinces)
        sigungu_text = " ".join(sigungus)
        is_gangneung = "강릉" in sigungu_text
        is_gangwon = "강원" in province_text
        if not (is_gangneung or is_gangwon):
            continue

        if is_gangneung:
            region = "강원 강릉시"
            sigungu = "강릉시"
            region_role = "핵심"
            connection = "직접"
        elif "평창" in sigungu_text or "대관령" in sigungu_text:
            region = "강원 평창군 대관령면"
            sigungu = "평창군"
            region_role = "보조"
            connection = "보조"
        else:
            region = "강원 관련지역"
            sigungu = compact_terms(sigungus) or "확인 필요"
            region_role = "관련"
            connection = "광역/관련"

        impacts = parse_listish(row.get("영향구분")) or [str(row.get("영향구분") or "").strip()]
        if not impacts:
            impacts = ["확인 필요"]
        for impact in impacts:
            impact = str(impact).strip() or "확인 필요"
            selected.append(
                {
                    "article_key": f"GN-{len(selected)+1:03d}",
                    "case_id": "CASE_GANGNEUNG_2025",
                    "사례명": "2025 강릉 가뭄",
                    "기사일자": row["dt"].strftime("%Y-%m-%d") if pd.notna(row["dt"]) else "",
                    "연도": int(row["dt"].year) if pd.notna(row["dt"]) else None,
                    "연월": row["dt"].strftime("%Y-%m") if pd.notna(row["dt"]) else "",
                    "표준지역명": region,
                    "시도": "강원특별자치도",
                    "시군구": sigungu,
                    "지역역할": region_role,
                    "뉴스링크": str(row.get("뉴스링크") or "").strip(),
                    "뉴스제목": str(row.get("뉴스제목") or "").strip(),
                    "영향분야": impact,
                    "피해상세": compact_terms(row.get("피해상세")),
                    "영향요약": compact_terms(row.get("피해상세")) or str(row.get("기사본문") or "").strip()[:160],
                    "검증활용근거": "기존 1990~2025 뉴스 CSV에서 2025년 강릉/강원 조건으로 필터링",
                    "본문요약": str(row.get("기사본문") or "").strip()[:500],
                    "연결수준": connection,
                    "사용판정": "사용" if region_role in {"핵심", "보조"} else "사용 후보",
                    "원천": "기존 1990~2025 CSV",
                }
            )
    return selected


def score_row(row):
    score = 0
    if row["사용판정"] == "사용":
        score += 10
    if row["연결수준"] == "직접":
        score += 5
    if row["피해상세"]:
        score += 2
    if row["본문요약"]:
        score += 1
    return score


def build_monthly(rows):
    grouped = defaultdict(list)
    for row in rows:
        key = (row["case_id"], row["사례명"], row["연월"], row["표준지역명"], row["지역역할"], row["영향분야"])
        grouped[key].append(row)

    monthly = []
    representative = []
    for key, items in sorted(grouped.items(), key=lambda x: x[0]):
        case_id, case_name, ym, region, role, impact = key
        direct_count = sum(1 for r in items if r["연결수준"] == "직접")
        use_count = sum(1 for r in items if r["사용판정"] == "사용")
        damages = []
        for r in items:
            for term in re.split(r",\s*", r["피해상세"] or ""):
                term = term.strip()
                if term and term not in damages:
                    damages.append(term)
        best = sorted(items, key=score_row, reverse=True)[0]
        monthly.append(
            {
                "case_id": case_id,
                "사례명": case_name,
                "연월": ym,
                "표준지역명": region,
                "지역역할": role,
                "영향분야": impact,
                "기사수": len(items),
                "직접기사수": direct_count,
                "사용기사수": use_count,
                "피해상세_통합": ", ".join(damages[:12]),
                "대표기사제목": best["뉴스제목"],
                "대표기사URL": best["뉴스링크"],
                "대표영향요약": best["영향요약"],
                "T5등급산출대상": "예" if use_count > 0 else "검토",
            }
        )
        representative.append(
            {
                "case_id": case_id,
                "사례명": case_name,
                "연월": ym,
                "표준지역명": region,
                "영향분야": impact,
                "대표기사제목": best["뉴스제목"],
                "대표기사URL": best["뉴스링크"],
                "대표선정근거": f"사용판정={best['사용판정']}, 연결수준={best['연결수준']}, 피해상세 존재={'예' if best['피해상세'] else '아니오'}",
                "영향요약": best["영향요약"],
            }
        )
    return monthly, representative


def build_status(rows, monthly):
    def count_case(case_id):
        return [r for r in rows if r["case_id"] == case_id]

    south = count_case("CASE_SOUTH_22_23")
    gang = count_case("CASE_GANGNEUNG_2025")
    return [
        {
            "ID": "T3-1",
            "세부작업": "뉴스 원자료 확보",
            "상태": "완료",
            "판단근거": f"고흥·합천 재수집 {len(south)}건, 강릉/강원 기존 CSV {len(gang)}건을 T3 입력으로 확보",
            "추가작업": "원문 DB 적재 여부는 별도 결정",
        },
        {
            "ID": "T3-2",
            "세부작업": "기사 날짜 정리",
            "상태": "완료",
            "판단근거": "모든 기사에 기사일자·연도·연월 생성",
            "추가작업": "없음",
        },
        {
            "ID": "T3-3",
            "세부작업": "기사별 지역 구분",
            "상태": "진행 중",
            "판단근거": "고흥·합천·강릉 직접지역과 강원 관련지역 구분 완료. 강원 관련지역은 핵심/보조 사용 여부 검토 필요",
            "추가작업": "강릉 관련지역 포함/제외 기준 확정",
        },
        {
            "ID": "T3-4",
            "세부작업": "영향 분야 분류",
            "상태": "완료",
            "판단근거": "영향분야 기준으로 월별 집계 생성",
            "추가작업": "기존 분류체계와 A1~A8 매핑이 필요하면 T5 전에 보강",
        },
        {
            "ID": "T3-5",
            "세부작업": "피해상세 정리",
            "상태": "완료",
            "판단근거": "피해상세/영향요약을 기사 단위와 월별 통합 단위에 기록",
            "추가작업": "보고서용 문장 다듬기",
        },
        {
            "ID": "T3-6",
            "세부작업": "대표 근거 기사 선정",
            "상태": "완료",
            "판단근거": f"월×지역×분야 집계 {len(monthly)}개 그룹별 대표기사 선정",
            "추가작업": "최종 보고서 반영 전 제목/URL 수동 검수",
        },
        {
            "ID": "T3-7",
            "세부작업": "뉴스 영향 등급 확인",
            "상태": "진행 중",
            "판단근거": "T3에서는 기사수와 직접기사수까지만 산출. 등급은 T5에서 기사수/직접성/피해상세 기준으로 산출 예정",
            "추가작업": "T5 등급 산출 기준 확정",
        },
        {
            "ID": "T3-8",
            "세부작업": "월별 대표기사 목록",
            "상태": "완료",
            "판단근거": "대표기사 시트 생성",
            "추가작업": "워크숍용 대표기사 1~2건 압축",
        },
        {
            "ID": "T3-9",
            "세부작업": "기사 상세 테이블 표준화",
            "상태": "완료",
            "판단근거": "기사정규화 시트에 case_id, 연월, 표준지역명, 영향분야, 링크, 제목, 피해상세 포함",
            "추가작업": "DB 적재 시 컬럼명 매핑",
        },
        {
            "ID": "T3-10",
            "세부작업": "월별 뉴스 집계 생성",
            "상태": "완료",
            "판단근거": "월별영향집계 시트 생성",
            "추가작업": "T5 등급 산출과 T6 통합표에 연결",
        },
    ]


def main():
    rows = load_south_rows() + load_gangneung_rows()
    rows = sorted(rows, key=lambda r: (r["case_id"], r["기사일자"], r["표준지역명"], r["뉴스제목"]))
    monthly, representative = build_monthly(rows)
    status = build_status(rows, monthly)

    qa = {
        "전체기사행": len(rows),
        "남부기사행": sum(1 for r in rows if r["case_id"] == "CASE_SOUTH_22_23"),
        "강릉기사행": sum(1 for r in rows if r["case_id"] == "CASE_GANGNEUNG_2025"),
        "월별집계행": len(monthly),
        "대표기사행": len(representative),
        "강릉핵심기사행": sum(1 for r in rows if r["case_id"] == "CASE_GANGNEUNG_2025" and r["표준지역명"] == "강원 강릉시"),
        "강원관련기사행": sum(1 for r in rows if r["case_id"] == "CASE_GANGNEUNG_2025" and r["표준지역명"] == "강원 관련지역"),
    }
    OUTPUT_JSON.write_text(
        json.dumps({"articles": rows, "monthly": monthly, "representative": representative, "status": status, "qa": qa}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(qa, ensure_ascii=False, indent=2))
    print(f"[INFO] saved {OUTPUT_JSON}")


if __name__ == "__main__":
    main()
