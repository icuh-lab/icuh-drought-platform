package re.kr.icuh.drought.application.openapi.hydropower.service;

import re.kr.icuh.drought.application.openapi.hydropower.request.HydroPowerRequest;
import re.kr.icuh.drought.application.openapi.hydropower.request.HydroPowerYearlyRequest;
import re.kr.icuh.drought.application.openapi.hydropower.response.comparison.DamMonthlyComparisonResponse;
import re.kr.icuh.drought.application.openapi.hydropower.response.generation.DamMonthlyGenerationResponse;
import re.kr.icuh.drought.application.openapi.hydropower.response.prediction.MonthlyDamPredictionResponse;
import re.kr.icuh.drought.application.openapi.hydropower.response.reservoir.DamMonthlyReservoirStatusResponse;
import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyGeneration;
import re.kr.icuh.drought.persistence.openapi.hydropower.entity.DamMonthlyReservoirStatus;
import re.kr.icuh.drought.persistence.openapi.hydropower.repository.HydroPowerRepository;
import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class HydroPowerService {

    private final HydroPowerRepository hydroPowerRepository;

    public HydroPowerService(HydroPowerRepository hydroPowerRepository) {
        this.hydroPowerRepository = hydroPowerRepository;
    }

    public MonthlyDamPredictionResponse getMonthlyPredictions(HydroPowerRequest request) {
        return hydroPowerRepository.damMonthlyPrediction(
                        request.year(), request.month(), request.damName())
                .map(MonthlyDamPredictionResponse::of)
                .orElseThrow(() -> new CoreException(ErrorType.DATA_NOT_FOUND));
    }

    public DamMonthlyComparisonResponse getMonthlyComparison(HydroPowerRequest request) {
        return hydroPowerRepository.damMonthlyComparison(
                        request.year(), request.month(), request.damName())
                .map(DamMonthlyComparisonResponse::of)
                .orElseThrow(() -> new CoreException(ErrorType.DATA_NOT_FOUND));
    }

    public DamMonthlyGenerationResponse getMonthlyGeneration(HydroPowerYearlyRequest request) {
        List<DamMonthlyGeneration> generations =
                hydroPowerRepository.damMonthlyGeneration(request.year(), request.damName());

        if (generations.isEmpty()) {
            throw new CoreException(ErrorType.DATA_NOT_FOUND);
        }

        return DamMonthlyGenerationResponse.of(generations);
    }

    public DamMonthlyReservoirStatusResponse getMonthlyReservoirStatus(HydroPowerYearlyRequest request) {
        List<DamMonthlyReservoirStatus> statuses =
                hydroPowerRepository.damMonthlyReservoirStatus(request.year(), request.damName());

        if (statuses.isEmpty()) {
            throw new CoreException(ErrorType.DATA_NOT_FOUND);
        }

        return DamMonthlyReservoirStatusResponse.of(statuses);
    }
}
