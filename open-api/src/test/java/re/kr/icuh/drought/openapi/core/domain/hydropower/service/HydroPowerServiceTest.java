package re.kr.icuh.drought.application.openapi.hydropower.service;

import re.kr.icuh.drought.application.openapi.hydropower.request.HydroPowerRequest;
import re.kr.icuh.drought.application.openapi.hydropower.request.HydroPowerYearlyRequest;
import re.kr.icuh.drought.application.openapi.hydropower.response.generation.DamMonthlyGenerationResponse;
import re.kr.icuh.drought.application.openapi.hydropower.response.prediction.MonthlyDamPredictionResponse;
import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyGeneration;
import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyPrediction;
import re.kr.icuh.drought.persistence.openapi.hydropower.repository.HydroPowerRepository;
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
class HydroPowerServiceTest {

    @Mock
    private HydroPowerRepository hydroPowerRepository;

    @InjectMocks
    private HydroPowerService hydroPowerService;

    private final HydroPowerRequest request = new HydroPowerRequest("2026", "4", "소양강댐");
    private final HydroPowerYearlyRequest yearlyRequest = new HydroPowerYearlyRequest("2026", "소양강댐");

    @Test
    @DisplayName("월간 댐 예측을 조회해 중첩 응답 DTO로 매핑한다")
    void mapsMonthlyPrediction() {
        DamMonthlyPrediction entity = mock(DamMonthlyPrediction.class);
        when(entity.getDamName()).thenReturn("소양강댐");
        when(entity.getPredictedPowerGenerationLowerBound()).thenReturn(50);
        when(entity.getPredictedWaterStorageUpperBound()).thenReturn(800);
        when(hydroPowerRepository.damMonthlyPrediction("2026", "4", "소양강댐"))
                .thenReturn(Optional.of(entity));

        MonthlyDamPredictionResponse response = hydroPowerService.getMonthlyPredictions(request);

        assertThat(response.damName()).isEqualTo("소양강댐");
        assertThat(response.predictedPowerGenerationDto().predictedPowerGenerationLowerBound()).isEqualTo(50);
        assertThat(response.predictedWaterStorageDto().predictedWaterStorageUpperBound()).isEqualTo(800);
    }

    @Test
    @DisplayName("월간 댐 예측이 없으면 DATA_NOT_FOUND 예외를 던진다")
    void throwsWhenPredictionMissing() {
        when(hydroPowerRepository.damMonthlyPrediction("2026", "4", "소양강댐"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> hydroPowerService.getMonthlyPredictions(request))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.DATA_NOT_FOUND);
    }

    @Test
    @DisplayName("월간 발전량 리스트를 헤더 + 항목으로 매핑한다")
    void mapsMonthlyGeneration() {
        DamMonthlyGeneration first = mock(DamMonthlyGeneration.class);
        when(first.getDamName()).thenReturn("소양강댐");
        lenient().when(first.getActualMwh()).thenReturn(111);
        DamMonthlyGeneration second = mock(DamMonthlyGeneration.class);
        lenient().when(second.getActualMwh()).thenReturn(222);
        when(hydroPowerRepository.damMonthlyGeneration("2026", "소양강댐"))
                .thenReturn(List.of(first, second));

        DamMonthlyGenerationResponse response = hydroPowerService.getMonthlyGeneration(yearlyRequest);

        assertThat(response.damName()).isEqualTo("소양강댐");
        assertThat(response.monthlyGenerationDto()).hasSize(2);
        assertThat(response.monthlyGenerationDto())
                .extracting("actualMwh")
                .containsExactly(111, 222);
    }

    @Test
    @DisplayName("월간 발전량이 비어 있으면 DATA_NOT_FOUND 예외를 던진다")
    void throwsWhenGenerationEmpty() {
        when(hydroPowerRepository.damMonthlyGeneration("2026", "소양강댐"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> hydroPowerService.getMonthlyGeneration(yearlyRequest))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.DATA_NOT_FOUND);
    }
}
