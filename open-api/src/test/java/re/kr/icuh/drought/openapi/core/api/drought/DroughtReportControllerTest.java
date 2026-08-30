package re.kr.icuh.drought.openapi.core.api.drought;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import re.kr.icuh.drought.application.openapi.drought.response.DroughtReportDetailResponse;
import re.kr.icuh.drought.application.openapi.drought.service.DroughtReportService;
import re.kr.icuh.drought.common.openapi.error.CoreException;
import re.kr.icuh.drought.common.openapi.error.ErrorType;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DroughtReportController.class)
class DroughtReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DroughtReportService droughtReportService;

    @Test
    @DisplayName("목록 조회는 200과 SUCCESS 래퍼로 응답한다")
    void returnsSuccessForList() throws Exception {
        when(droughtReportService.getReports(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/drought/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"));
    }

    @Test
    @DisplayName("상세 조회는 200과 데이터를 응답한다")
    void returnsDetail() throws Exception {
        DroughtReportDetailResponse detail = new DroughtReportDetailResponse(
                "2026-05", LocalDateTime.of(2026, 8, 30, 15, 39), 748, 16, List.of(), List.of());
        when(droughtReportService.getReportDetail(eq("2026-05"))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/drought/reports/2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.reportYm").value("2026-05"))
                .andExpect(jsonPath("$.data.articleCount").value(748));
    }

    @Test
    @DisplayName("없는 reportYm 조회는 404를 응답한다")
    void returnsNotFoundForMissingReport() throws Exception {
        when(droughtReportService.getReportDetail(eq("1999-01")))
                .thenThrow(new CoreException(ErrorType.DATA_NOT_FOUND));

        mockMvc.perform(get("/api/v1/drought/reports/1999-01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value("ERROR"));
    }
}
