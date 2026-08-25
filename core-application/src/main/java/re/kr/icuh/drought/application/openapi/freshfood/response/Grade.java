package re.kr.icuh.drought.application.openapi.freshfood.response;

public record Grade(
    Integer veryHigh,
    Integer high,
    Integer normal,
    Integer low,
    Integer veryLow
) {
}
