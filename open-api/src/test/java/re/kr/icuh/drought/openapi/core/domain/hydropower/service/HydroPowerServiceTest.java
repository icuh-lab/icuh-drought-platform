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
    @DisplayName("월간 발전량은 repository가 반환한 순서를 그대로 보존한다 (뒤섞인 순서를 서비스가 재정렬하지 않음)")
    void mapsMonthlyGenerationPreservesRepositoryOrderEvenWhenScrambled() {
        // repository mock이 연-월 오름차순이 아니라 일부러 뒤섞인 순서(10월 -> 2월 -> 1월)로 반환하도록 설정한다.
        // 실제 정렬 보장은 HydroPowerRepository의 JPQL ORDER BY가 책임지며, 이 테스트는 mock 레벨에서
        // 서비스가 그 순서를 임의로 바꾸지 않고 그대로 전달하는지만 확인한다(= mock으로는 JPQL의
        // ORDER BY 자체를 검증할 수 없음. 실제 정렬 검증은 core-persistence의 HydroPowerRepositoryTest 참고).
        DamMonthlyGeneration october = mock(DamMonthlyGeneration.class);
        when(october.getDamName()).thenReturn("소양강댐");
        lenient().when(october.getMonth()).thenReturn("10");
        lenient().when(october.getActualMwh()).thenReturn(1010);

        DamMonthlyGeneration february = mock(DamMonthlyGeneration.class);
        lenient().when(february.getMonth()).thenReturn("2");
        lenient().when(february.getActualMwh()).thenReturn(202);

        DamMonthlyGeneration january = mock(DamMonthlyGeneration.class);
        lenient().when(january.getMonth()).thenReturn("1");
        lenient().when(january.getActualMwh()).thenReturn(101);

        when(hydroPowerRepository.damMonthlyGeneration("2026", "소양강댐"))
                .thenReturn(List.of(october, february, january));

        DamMonthlyGenerationResponse response = hydroPowerService.getMonthlyGeneration(yearlyRequest);

        assertThat(response.monthlyGenerationDto())
                .extracting("month")
                .containsExactly("10", "2", "1");
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
