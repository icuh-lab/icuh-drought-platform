package re.kr.icuh.drought.application.openapi.wildfire.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import re.kr.icuh.drought.application.openapi.wildfire.request.WildFireForecastRequest;
import re.kr.icuh.drought.application.openapi.wildfire.response.ForecastResponse;
import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;
import re.kr.icuh.drought.persistence.openapi.wildfire.repository.WildFireRiskIndexRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WildFireRiskIndexServiceTest {

    @Mock
    private WildFireRiskIndexRepository wildFireRiskIndexRepository;

    @InjectMocks
    private WildFireRiskIndexService wildFireRiskIndexService;

    @Test
    @DisplayName("날짜를 지정하면 그 날짜부터 3일치를 조회한다")
    void queriesThreeDaysFromRequestedDate() {
        when(wildFireRiskIndexRepository.findByAnaldate(any())).thenReturn(List.of());

        List<ForecastResponse> responses =
                wildFireRiskIndexService.getForeCast(new WildFireForecastRequest("2025", "12", "3"));

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(wildFireRiskIndexRepository, times(3)).findByAnaldate(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(LocalDateTime::toLocalDate)
                .containsExactly(
                        LocalDate.of(2025, 12, 3),
                        LocalDate.of(2025, 12, 4),
                        LocalDate.of(2025, 12, 5));
        assertThat(responses)
                .extracting(ForecastResponse::targetDate)
                .containsExactly("2025-12-03", "2025-12-04", "2025-12-05");
    }

    @Test
    @DisplayName("날짜를 생략하면 오늘부터 3일치를 조회한다")
    void queriesThreeDaysFromTodayWhenDateOmitted() {
        when(wildFireRiskIndexRepository.findByAnaldate(any())).thenReturn(List.of());
        LocalDate today = LocalDate.now();

        wildFireRiskIndexService.getForeCast(new WildFireForecastRequest(null, null, null));

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(wildFireRiskIndexRepository, times(3)).findByAnaldate(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(LocalDateTime::toLocalDate)
                .containsExactly(today, today.plusDays(1), today.plusDays(2));
    }

    @Test
    @DisplayName("달력에 없는 날짜면 INVALID_PARAMETER 예외를 던진다")
    void throwsForNonexistentDate() {
        assertThatThrownBy(() ->
                wildFireRiskIndexService.getForeCast(new WildFireForecastRequest("2026", "2", "30")))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.INVALID_PARAMETER);
    }
}
