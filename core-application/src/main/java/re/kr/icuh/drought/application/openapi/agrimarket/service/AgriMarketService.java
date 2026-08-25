package re.kr.icuh.drought.application.openapi.agrimarket.service;

import re.kr.icuh.drought.application.openapi.agrimarket.request.AgriMarketRequest;
import re.kr.icuh.drought.application.openapi.agrimarket.response.calendar.DailyPricePredictionResponse;
import re.kr.icuh.drought.application.openapi.agrimarket.response.prediction.MonthlyMarketPredictionResponse;
import re.kr.icuh.drought.application.openapi.agrimarket.response.trend.DailyMarketTrendResponse;
import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.DailyMarketTrend;
import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.DailyPricePrediction;
import re.kr.icuh.drought.persistence.openapi.agrimarket.entity.MonthlyMarketPrediction;
import re.kr.icuh.drought.persistence.openapi.agrimarket.repository.AgriMarketRepository;
import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AgriMarketService {

    private final AgriMarketRepository agriMarketRepository;

    public AgriMarketService(AgriMarketRepository agriMarketRepository) {
        this.agriMarketRepository = agriMarketRepository;
    }

    public MonthlyMarketPredictionResponse getAgriMarketPricePredict(AgriMarketRequest request) {
        MonthlyMarketPrediction prediction = agriMarketRepository.agriMarketPricePredict(
                request.year(),
                request.month(),
                request.location()
        ).orElseThrow(() -> new CoreException(ErrorType.DATA_NOT_FOUND));

        return MonthlyMarketPredictionResponse.of(prediction);
    }

    public DailyPricePredictionResponse getDailyPricePrediction(AgriMarketRequest request) {
        List<DailyPricePrediction> predictions = agriMarketRepository.dailyPricePrediction(
                request.year(), request.month(), request.location());

        if (predictions.isEmpty()) {
            throw new CoreException(ErrorType.DATA_NOT_FOUND);
        }

        return DailyPricePredictionResponse.of(predictions);
    }

    public DailyMarketTrendResponse getDailyMarketTrend(AgriMarketRequest request) {
        List<DailyMarketTrend> trends = agriMarketRepository.dailyMarketTrend(
                request.year(), request.month(), request.location());

        if (trends.isEmpty()) {
            throw new CoreException(ErrorType.DATA_NOT_FOUND);
        }

        return DailyMarketTrendResponse.of(trends);
    }
}
