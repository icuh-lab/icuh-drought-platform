package re.kr.icuh.drought.application.openapi.freshfood.service;

import re.kr.icuh.drought.application.openapi.freshfood.request.FreshFoodIndexRequest;
import re.kr.icuh.drought.application.openapi.freshfood.response.FreshFruitIndexResponse;
import re.kr.icuh.drought.application.openapi.freshfood.response.FreshVegetableIndexResponse;
import re.kr.icuh.drought.persistence.openapi.freshfood.FreshFruitIndex;
import re.kr.icuh.drought.persistence.openapi.freshfood.FreshVegetableIndex;
import re.kr.icuh.drought.persistence.openapi.freshfood.repository.FreshFoodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

@Service
@Transactional(readOnly = true)
public class FreshFoodService {

    private final FreshFoodRepository freshFoodRepository;

    public FreshFoodService(FreshFoodRepository freshFoodRepository) {
        this.freshFoodRepository = freshFoodRepository;
    }

    public FreshVegetableIndexResponse getFreshVegetableIndex(FreshFoodIndexRequest freshFoodIndexRequest) {
        // 지도용 데이터 - 조회일자로 지역별
        String baseDate = makeBaseDate(freshFoodIndexRequest.year(), freshFoodIndexRequest.month());

        List<FreshVegetableIndex> provinceData = freshFoodRepository.findByBaseDate(baseDate)
                .stream()
                .map(FreshVegetableIndex::of)
                .toList();

        // 등급별 카운트 - 등급 없는(지수가 빈) 지역은 groupingBy의 null 키가 되어 NPE를 내므로 제외한다.
        Map<String, Long> summary = provinceData.stream()
                .filter(index -> index.grade() != null)
                .collect(groupingBy(FreshVegetableIndex::grade, counting()));

        return FreshVegetableIndexResponse.of(baseDate, provinceData, summary);
    }

    public FreshFruitIndexResponse getFreshFruitIndex(FreshFoodIndexRequest freshFoodIndexRequest) {
        // 지도용 데이터 - 조회일자로 지역별
        String baseDate = makeBaseDate(freshFoodIndexRequest.year(), freshFoodIndexRequest.month());

        List<FreshFruitIndex> provinceData = freshFoodRepository.findByBaseDate(baseDate)
                .stream()
                .map(FreshFruitIndex::of)
                .toList();

        // 등급별 카운트 - 등급 없는(지수가 빈) 지역은 groupingBy의 null 키가 되어 NPE를 내므로 제외한다.
        Map<String, Long> summary = provinceData.stream()
                .filter(index -> index.grade() != null)
                .collect(groupingBy(FreshFruitIndex::grade, counting()));

        return FreshFruitIndexResponse.of(baseDate, provinceData, summary);
    }

    private String makeBaseDate(String year, String month) {
        return YearMonth.of(Integer.parseInt(year), Integer.parseInt(month))
                .atDay(1) // 해당 월의 1일로 설정
                .toString(); // "YYYY-MM-DD" 형식으로 반환
    }
}
