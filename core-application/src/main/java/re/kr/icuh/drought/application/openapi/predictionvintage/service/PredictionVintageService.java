package re.kr.icuh.drought.application.openapi.predictionvintage.service;

import re.kr.icuh.drought.application.openapi.predictionvintage.request.PredictionVintageRequest;
import re.kr.icuh.drought.application.openapi.predictionvintage.response.PredictionVintageResponse;
import re.kr.icuh.drought.persistence.openapi.predictionvintage.entity.PredictionVintageLog;
import re.kr.icuh.drought.persistence.openapi.predictionvintage.repository.PredictionVintageRepository;
import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PredictionVintageService {

    private final PredictionVintageRepository predictionVintageRepository;

    public PredictionVintageService(PredictionVintageRepository predictionVintageRepository) {
        this.predictionVintageRepository = predictionVintageRepository;
    }

    public PredictionVintageResponse getPredictionVintage(PredictionVintageRequest request) {
        List<PredictionVintageLog> logs = predictionVintageRepository.findByLocationOrderByTargetDateAsc(request.location());

        if (logs.isEmpty()) {
            throw new CoreException(ErrorType.DATA_NOT_FOUND);
        }

        return PredictionVintageResponse.of(logs);
    }
}
