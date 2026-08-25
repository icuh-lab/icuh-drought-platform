package re.kr.icuh.drought.persistence.openapi.freshfood;

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
    JEONBUK_SPECIAL("전북특별자치도", 52);

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

    public static int findCodeByName(String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }

        return Arrays.stream(Province.values())
                .filter(province -> province.getName().equals(name))
                .findFirst()
                .map(Province::getCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid province name: " + name));
    }
}
