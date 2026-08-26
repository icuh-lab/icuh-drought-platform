package re.kr.icuh.drought.openapi.core.api.hydropower;

import re.kr.icuh.drought.application.openapi.hydropower.request.HydroPowerRequest;
import re.kr.icuh.drought.application.openapi.hydropower.response.generation.DamMonthlyGenerationResponse;
import re.kr.icuh.drought.application.openapi.hydropower.response.prediction.MonthlyDamPredictionResponse;
import re.kr.icuh.drought.application.openapi.hydropower.service.HydroPowerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    @DisplayName("0을 채운 월도 허용하고 서비스에는 0을 뗀 값으로 전달한다")
    void normalizesZeroPaddedMonth() throws Exception {
        when(hydroPowerService.getMonthlyPredictions(any()))
                .thenReturn(MonthlyDamPredictionResponse.builder().damName("소양강댐").build());

        mockMvc.perform(get("/api/v1/hydropower/monthly-predict")
                        .param("year", "2026")
                        .param("month", "04")
                        .param("damName", "소양강댐"))
                .andExpect(status().isOk());

        ArgumentCaptor<HydroPowerRequest> captor = ArgumentCaptor.forClass(HydroPowerRequest.class);
        verify(hydroPowerService).getMonthlyPredictions(captor.capture());
        assertThat(captor.getValue().month()).isEqualTo("4");
    }

    @Test
    @DisplayName("연 단위 조회인 월별 발전 실적은 월 없이도 200으로 응답한다")
    void monthlyGenerationDoesNotRequireMonth() throws Exception {
        when(hydroPowerService.getMonthlyGeneration(any()))
                .thenReturn(DamMonthlyGenerationResponse.builder().damName("충주댐").build());

        mockMvc.perform(get("/api/v1/hydropower/monthly-generation")
                        .param("year", "2024")
                        .param("damName", "충주댐"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.damName").value("충주댐"));
    }

    @Test
    @DisplayName("월별 저수 현황도 연도 형식이 어긋나면 400을 응답한다")
    void monthlyReservoirValidatesYear() throws Exception {
        mockMvc.perform(get("/api/v1/hydropower/monthly-reservoir")
                        .param("year", "20")
                        .param("damName", "합천댐"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));
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
