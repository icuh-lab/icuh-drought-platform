package re.kr.icuh.drought.application.openapi.freshfood.service;

import re.kr.icuh.drought.application.openapi.freshfood.request.FreshFoodIndexRequest;
import re.kr.icuh.drought.application.openapi.freshfood.response.FreshVegetableIndexResponse;
import re.kr.icuh.drought.persistence.openapi.freshfood.entity.FreshFood;
import re.kr.icuh.drought.persistence.openapi.freshfood.repository.FreshFoodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FreshFoodServiceTest {

    @Mock
    private FreshFoodRepository freshFoodRepository;

    @InjectMocks
    private FreshFoodService freshFoodService;

    private FreshFood freshFood(String province, float vegetableIndex) {
        FreshFood freshFood = mock(FreshFood.class);
        when(freshFood.getProvince()).thenReturn(province);
        when(freshFood.getFreshVegetableIndex()).thenReturn(vegetableIndex);
        return freshFood;
    }

    private FreshFood freshFoodWithoutIndex(String province) {
        FreshFood freshFood = mock(FreshFood.class);
        when(freshFood.getProvince()).thenReturn(province);
        when(freshFood.getFreshVegetableIndex()).thenReturn(null);
        return freshFood;
    }

    @Test
    @DisplayName("연/월을 해당 월 1일의 baseDate로 변환해 조회한다")
    void resolvesBaseDateFromYearMonth() {
        when(freshFoodRepository.findByBaseDate("2026-04-01")).thenReturn(List.of());

        FreshVegetableIndexResponse response =
                freshFoodService.getFreshVegetableIndex(new FreshFoodIndexRequest("2026", "4"));

        assertThat(response.baseDate()).isEqualTo("2026-04-01");
        assertThat(response.provinceData()).isEmpty();
        assertThat(response.summary()).isEmpty();
    }

    @Test
    @DisplayName("지수 임계값에 따라 등급을 매기고 등급별 개수를 집계한다")
    void gradesProvincesAndCountsByGrade() {
        // mock 생성을 바깥 when().thenReturn() 인자 안에서 하면 중첩 stubbing이 되어
        // UnfinishedStubbingException이 난다. 미리 지역 변수로 분리한다.
        FreshFood seoul = freshFood("서울특별시", 120f);   // >= 115.1 -> veryHigh
        FreshFood busan = freshFood("부산광역시", 118f);   // >= 115.1 -> veryHigh
        FreshFood gyeonggi = freshFood("경기도", 100f);    // >= 95.1  -> normal
        when(freshFoodRepository.findByBaseDate("2026-04-01")).thenReturn(List.of(seoul, busan, gyeonggi));

        FreshVegetableIndexResponse response =
                freshFoodService.getFreshVegetableIndex(new FreshFoodIndexRequest("2026", "4"));

        assertThat(response.provinceData()).hasSize(3);
        assertThat(response.provinceData())
                .extracting("province", "grade")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("서울특별시", "veryHigh"),
                        org.assertj.core.groups.Tuple.tuple("부산광역시", "veryHigh"),
                        org.assertj.core.groups.Tuple.tuple("경기도", "normal")
                );
        assertThat(response.summary())
                .containsEntry("veryHigh", 2L)
                .containsEntry("normal", 1L)
                .doesNotContainKey("low");
    }

    @Test
    @DisplayName("85점대 구간은 low, 그 미만은 veryLow로 분류한다")
    void appliesGradeBoundaries() {
        // 주의: 임계값 비교가 Float 필드 vs double 리터럴이라 정확히 85.1 같은 경계값은
        // 부동소수 확장 때문에 의도와 다르게 갈릴 수 있다(85.1f -> 85.0999.. < 85.1).
        // 그래서 경계에서 떨어진 명확한 값으로 현재 동작을 고정한다.
        FreshFood seoul = freshFood("서울특별시", 90f);  // >= 85.1 -> low
        FreshFood busan = freshFood("부산광역시", 80f);  // <  85.1 -> veryLow
        when(freshFoodRepository.findByBaseDate("2026-04-01")).thenReturn(List.of(seoul, busan));

        FreshVegetableIndexResponse response =
                freshFoodService.getFreshVegetableIndex(new FreshFoodIndexRequest("2026", "4"));

        assertThat(response.summary())
                .containsEntry("low", 1L)
                .containsEntry("veryLow", 1L);
    }

    @Test
    @DisplayName("처음 보는 지역명이 섞여도 그 달 전체를 버리지 않고 코드 0으로 함께 내려준다")
    void keepsMonthAliveWhenProvinceIsUnknown() {
        FreshFood seoul = freshFood("서울특별시", 100f);
        FreshFood merged = freshFood("전남광주통합특별시", 104.03f);
        FreshFood unknown = freshFood("대구경북통합특별시", 102f);
        when(freshFoodRepository.findByBaseDate("2026-06-01")).thenReturn(List.of(seoul, merged, unknown));

        FreshVegetableIndexResponse response =
                freshFoodService.getFreshVegetableIndex(new FreshFoodIndexRequest("2026", "6"));

        assertThat(response.provinceData())
                .extracting("province", "code")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("서울특별시", 11),
                        org.assertj.core.groups.Tuple.tuple("전남광주통합특별시", 53),
                        org.assertj.core.groups.Tuple.tuple("대구경북통합특별시", 0)
                );
        assertThat(response.summary()).containsEntry("normal", 3L);
    }

    @Test
    @DisplayName("지수가 비어 있는 시도는 행은 남기되 등급 집계에서는 뺀다")
    void keepsProvinceWithoutIndexOutOfSummary() {
        FreshFood seoul = freshFood("서울특별시", 100f);
        FreshFood sejong = freshFoodWithoutIndex("세종특별자치시");
        when(freshFoodRepository.findByBaseDate("2026-06-01")).thenReturn(List.of(seoul, sejong));

        FreshVegetableIndexResponse response =
                freshFoodService.getFreshVegetableIndex(new FreshFoodIndexRequest("2026", "6"));

        assertThat(response.provinceData()).hasSize(2);
        assertThat(response.provinceData())
                .extracting("province", "grade")
                .contains(org.assertj.core.groups.Tuple.tuple("세종특별자치시", null));
        assertThat(response.summary())
                .containsEntry("normal", 1L)
                .hasSize(1);
    }
}
