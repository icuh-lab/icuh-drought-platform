package re.kr.icuh.drought.application.openapi.freshfood.response;

public record Province(
    String regionCode,
    String regionName,
    Float indexValue,
    String grade
) {
}
