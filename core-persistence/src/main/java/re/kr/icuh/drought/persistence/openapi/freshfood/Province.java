package re.kr.icuh.drought.persistence.openapi.freshfood;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public enum Province {
    ALL("전국", 99),
    SEOUL("서울특별시", 11),
    BUSAN("부산광역시", 21),
    DAEGU("대구광역시", 22),
    INCHEON("인천광역시", 23),
    GWANGJU("광주광역시", 24),
    DAEJEON("대전광역시", 25),
    ULSAN("울산광역시", 26),
    SEJONG("세종특별자치시", 29),
    GYEONGGI("경기도", 31),
    GANGWON("강원특별자치도", 32),
    CHUNGBUK("충청북도", 33),
    CHUNGNAM("충청남도", 34),
    JEONBUK("전라북도", 35),
    JEONNAM("전라남도", 36),
    GYEONGBUK("경상북도", 37),
    GYEONGNAM("경상남도", 38),
    JEJU("제주특별자치도", 39),
    JEONBUK_SPECIAL("전북특별자치도", 52),
    // 2026-06부터 적재된다. 광주광역시(24)·전라남도(36)를 대체하는 것이 아니라,
    // 전국(99)처럼 두 지역을 합산한 행이 하나 더 붙는 것이라 기존 항목은 그대로 둔다.
    JEONNAM_GWANGJU("전남광주통합특별시", 53);

    private static final Logger log = LoggerFactory.getLogger(Province.class);

    /** 코드를 못 찾은 지역. 한 행 때문에 그 달 전체가 죽지 않도록 이 값으로 흘려보낸다. */
    public static final int UNKNOWN_CODE = 0;

    private final String name;
    private final int code;

    Province(String name, int code) {
        this.name = name;
        this.code = code;
    }

    public String getName() {
        return name;
    }
    public int getCode() {
        return code;
    }

    /**
     * 지역명을 코드로 바꾼다.
     * 모르는 이름에 예외를 던지면 그 행 하나 때문에 해당 월 응답 전체가 500이 된다
     * (2026-06 전남광주통합특별시 신설 때 실제로 6·7월이 통째로 막혔다).
     * 행정구역 개편은 앞으로도 예고 없이 들어오므로 코드만 비우고 데이터는 내보낸다.
     */
    public static int findCodeByName(String name) {
        if (name == null || name.isEmpty()) {
            return UNKNOWN_CODE;
        }

        return Arrays.stream(Province.values())
                .filter(province -> province.getName().equals(name))
                .findFirst()
                .map(Province::getCode)
                .orElseGet(() -> {
                    log.warn("Province 코드가 없는 지역명이다. 코드 없이 응답한다: {}", name);
                    return UNKNOWN_CODE;
                });
    }
}
