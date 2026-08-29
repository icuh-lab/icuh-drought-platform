package re.kr.icuh.drought.openapi.core.domain.predictionvintage.service;

import re.kr.icuh.drought.application.openapi.predictionvintage.request.PredictionVintageRequest;
import re.kr.icuh.drought.application.openapi.predictionvintage.response.PredictionVintageResponse;
import re.kr.icuh.drought.application.openapi.predictionvintage.service.PredictionVintageService;
import re.kr.icuh.drought.persistence.openapi.predictionvintage.entity.PredictionVintageLog;
import re.kr.icuh.drought.persistence.openapi.predictionvintage.repository.PredictionVintageRepository;
import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictionVintageServiceTest {

    @Mock
    private PredictionVintageRepository predictionVintageRepository;

    @InjectMocks
    private PredictionVintageService predictionVintageService;

    private final PredictionVintageRequest request = new PredictionVintageRequest("합천");

    @Test
    @DisplayName("vintage 로그 리스트를 헤더 + 항목 리스트로 매핑한다")
    void mapsPredictionVintage() {
        PredictionVintageLog first = mock(PredictionVintageLog.class);
        when(first.getLocation()).thenReturn("합천");
        when(first.getItem()).thenReturn("양파");
        when(first.getVariety()).thenReturn("1키로/상");
        lenient().when(first.getTargetDate()).thenReturn(LocalDate.of(2026, 8, 26));
        lenient().when(first.getHorizonDays()).thenReturn(180);
        lenient().when(first.getSource()).thenReturn("live");
        lenient().when(first.getPred()).thenReturn(new BigDecimal("1050.00"));
        PredictionVintageLog second = mock(PredictionVintageLog.class);
        lenient().when(second.getTargetDate()).thenReturn(LocalDate.of(2026, 8, 27));
        lenient().when(second.getHorizonDays()).thenReturn(180);
        lenient().when(second.getSource()).thenReturn("live");
        lenient().when(second.getPred()).thenReturn(new BigDecimal("1055.00"));
        when(predictionVintageRepository.findByLocationOrderByTargetDateAsc("합천"))
                .thenReturn(List.of(first, second));

        PredictionVintageResponse response = predictionVintageService.getPredictionVintage(request);

        assertThat(response.location()).isEqualTo("합천");
        assertThat(response.item()).isEqualTo("양파");
        assertThat(response.variety()).isEqualTo("1키로/상");
        assertThat(response.entries()).hasSize(2);
        assertThat(response.entries())
                .extracting("pred")
                .containsExactly(new BigDecimal("1050.00"), new BigDecimal("1055.00"));
    }

    @Test
    @DisplayName("vintage 로그가 비어 있으면 DATA_NOT_FOUND 예외를 던진다")
    void throwsWhenPredictionVintageEmpty() {
        when(predictionVintageRepository.findByLocationOrderByTargetDateAsc("합천"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> predictionVintageService.getPredictionVintage(request))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.DATA_NOT_FOUND);
    }
}
