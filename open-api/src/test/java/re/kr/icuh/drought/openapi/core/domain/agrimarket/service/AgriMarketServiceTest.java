package re.kr.icuh.drought.application.openapi.agrimarket.service;

import re.kr.icuh.drought.application.openapi.agrimarket.request.AgriMarketRequest;
import re.kr.icuh.drought.application.openapi.agrimarket.response.calendar.DailyPricePredictionResponse;
import re.kr.icuh.drought.application.openapi.agrimarket.response.prediction.MonthlyMarketPredictionResponse;
import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.DailyPricePrediction;
import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.MonthlyMarketPrediction;
import re.kr.icuh.drought.persistence.openapi.agrimarket.repository.AgriMarketRepository;
import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgriMarketServiceTest {

    @Mock
    private AgriMarketRepository agriMarketRepository;

    @InjectMocks
    private AgriMarketService agriMarketService;

    private final AgriMarketRequest request = new AgriMarketRequest("2026", "4", "서울");

    @Test
    @DisplayName("월간 가격 예측을 조회해 응답 DTO로 매핑한다")
    void mapsMonthlyPrediction() {
        MonthlyMarketPrediction entity = mock(MonthlyMarketPrediction.class);
        when(entity.getPredictionYear()).thenReturn("2026");
        when(entity.getPredictionMonth()).thenReturn("4");
        when(entity.getLocation()).thenReturn("서울");
        when(entity.getPredictedPriceLowerBound()).thenReturn(100);
        when(entity.getPredictedVolumeUpperBound()).thenReturn(900);
        when(agriMarketRepository.agriMarketPricePredict("2026", "4", "서울"))
                .thenReturn(Optional.of(entity));

        MonthlyMarketPredictionResponse response = agriMarketService.getAgriMarketPricePredict(request);

        assertThat(response.year()).isEqualTo("2026");
        assertThat(response.month()).isEqualTo("4");
        assertThat(response.location()).isEqualTo("서울");
        assertThat(response.priceInfo().predictedPriceLowerBound()).isEqualTo(100);
        assertThat(response.volumeInfo().predictedVolumeUpperBound()).isEqualTo(900);
    }

    @Test
    @DisplayName("월간 가격 예측이 없으면 DATA_NOT_FOUND 예외를 던진다")
    void throwsWhenPredictionMissing() {
        when(agriMarketRepository.agriMarketPricePredict("2026", "4", "서울"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> agriMarketService.getAgriMarketPricePredict(request))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.DATA_NOT_FOUND);
    }

    @Test
    @DisplayName("일별 가격 예측 리스트를 헤더 + 캘린더 데이터로 매핑한다")
    void mapsDailyPricePrediction() {
        DailyPricePrediction first = mock(DailyPricePrediction.class);
        when(first.getLocation()).thenReturn("서울");
        when(first.getItem()).thenReturn("배추");
        lenient().when(first.getPredictedPrice()).thenReturn(1000);
        DailyPricePrediction second = mock(DailyPricePrediction.class);
        lenient().when(second.getPredictedPrice()).thenReturn(2000);
        when(agriMarketRepository.dailyPricePrediction("2026", "4", "서울"))
                .thenReturn(List.of(first, second));

        DailyPricePredictionResponse response = agriMarketService.getDailyPricePrediction(request);

        assertThat(response.location()).isEqualTo("서울");
        assertThat(response.item()).isEqualTo("배추");
        assertThat(response.calendarData()).hasSize(2);
        assertThat(response.calendarData())
                .extracting("predictedPrice")
                .containsExactly(1000, 2000);
    }

    @Test
    @DisplayName("일별 가격 예측이 비어 있으면 DATA_NOT_FOUND 예외를 던진다")
    void throwsWhenDailyPredictionEmpty() {
        when(agriMarketRepository.dailyPricePrediction("2026", "4", "서울"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> agriMarketService.getDailyPricePrediction(request))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.DATA_NOT_FOUND);
    }
}
