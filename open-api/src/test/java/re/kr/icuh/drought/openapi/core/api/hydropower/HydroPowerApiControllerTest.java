package re.kr.icuh.drought.openapi.core.api.hydropower;

import re.kr.icuh.drought.application.openapi.hydropower.response.prediction.MonthlyDamPredictionResponse;
import re.kr.icuh.drought.application.openapi.hydropower.service.HydroPowerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HydroPowerApiController.class)
class HydroPowerApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HydroPowerService hydroPowerService;

    @Test
    @DisplayName("정상 요청이면 200과 SUCCESS 래퍼로 응답한다")
    void returnsSuccessForValidRequest() throws Exception {
        when(hydroPowerService.getMonthlyPredictions(any()))
                .thenReturn(MonthlyDamPredictionResponse.builder().damName("소양강댐").build());

        mockMvc.perform(get("/api/v1/hydropower/monthly-predict")
                        .param("year", "2026")
                        .param("month", "4")
                        .param("damName", "소양강댐"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.damName").value("소양강댐"));
    }

    @Test
    @DisplayName("월이 범위를 벗어나면 400과 검증 메시지를 응답한다")
    void returnsBadRequestForInvalidMonth() throws Exception {
        mockMvc.perform(get("/api/v1/hydropower/monthly-predict")
                        .param("year", "2026")
                        .param("month", "13")
                        .param("damName", "소양강댐"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));
    }
}
