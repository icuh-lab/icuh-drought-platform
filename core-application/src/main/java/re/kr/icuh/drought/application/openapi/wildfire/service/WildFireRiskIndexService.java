package re.kr.icuh.drought.application.openapi.wildfire.service;

import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;
import re.kr.icuh.drought.application.openapi.wildfire.request.WildFireForecastRequest;
import re.kr.icuh.drought.application.openapi.wildfire.request.WildFireRiskIndexRequest;
import re.kr.icuh.drought.application.openapi.wildfire.response.ForecastResponse;
import re.kr.icuh.drought.application.openapi.wildfire.response.NewsArticleResponse;
import re.kr.icuh.drought.persistence.openapi.wildfire.Sigungu;
import re.kr.icuh.drought.persistence.openapi.wildfire.repository.WildFireRiskIndexRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class WildFireRiskIndexService {

    private final WildFireRiskIndexRepository wildFireRiskIndexRepository;

    public WildFireRiskIndexService(WildFireRiskIndexRepository wildFireRiskIndexRepository) {
        this.wildFireRiskIndexRepository = wildFireRiskIndexRepository;
    }

    public List<ForecastResponse> getForeCast(WildFireForecastRequest request) {

        LocalDateTime currentTime = resolveBaseTime(request);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        return IntStream.range(0, 3)
                .mapToObj(i -> {
                    LocalDateTime targetTime = currentTime.plusDays(i);

                    List<Sigungu> sigungus = wildFireRiskIndexRepository
                            .findByAnaldate(targetTime)
                            .stream()
                            .map(Sigungu::of)
                            .toList();

                    return new ForecastResponse(
                            targetTime.format(dateFormatter),   // "2026-04-20"
                            targetTime.format(timeFormatter),   // "18:00:00"
                            sigungus
                    );
                })
                .toList();
    }

    public List<NewsArticleResponse> getNewsArticle(WildFireRiskIndexRequest wildFireRiskIndexRequest) {
        // 해당 년/월 기준으로 데이터를 조회해야한다.
        YearMonth yearMonth = YearMonth.of(Integer.parseInt(wildFireRiskIndexRequest.year()), Integer.parseInt(wildFireRiskIndexRequest.month()));
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        return wildFireRiskIndexRepository.findNewsArticleByPublishDate(startDate, endDate)
                .stream()
                .map(NewsArticleResponse::of)
                .toList();
    }

    /**
     * 조회 기준 시각을 정한다.
     * 날짜는 요청값(없으면 오늘)을 쓰고, 시간대는 현재 시각이 속한 3시간 슬롯을 쓴다.
     */
    private LocalDateTime resolveBaseTime(WildFireForecastRequest request) {
        LocalDateTime now = LocalDateTime.now();
        int slotHour = (now.getHour() / 3) * 3;     // 19시 -> 18시 (int끼리 나누면 자동 내림)

        if (!request.hasDate()) {
            return now.toLocalDate().atTime(slotHour, 0);
        }

        try {
            return LocalDate.of(
                    Integer.parseInt(request.year()),
                    Integer.parseInt(request.month()),
                    Integer.parseInt(request.day())
            ).atTime(slotHour, 0);
        } catch (DateTimeException e) {
            throw new CoreException(ErrorType.INVALID_PARAMETER);
        }
    }

    private String createStartDate(String year, String month, String day, String hour) {
        return String.format("%s-%02d-%02d %s", year, Integer.parseInt(month), Integer.parseInt(day), hour);
    }

    private String createMiddleDate(String startDate) {
        LocalDateTime endDate = LocalDateTime.parse(startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        endDate = endDate.plusDays(1);
        return endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String createEndDate(String startDate) {
        LocalDateTime endDate = LocalDateTime.parse(startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        endDate = endDate.plusDays(2);
        return endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
