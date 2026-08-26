package re.kr.icuh.drought.openapi.core.api.agrimarket;

import re.kr.icuh.drought.application.openapi.agrimarket.request.AgriMarketRequest;
import re.kr.icuh.drought.application.openapi.agrimarket.response.prediction.MonthlyMarketPredictionResponse;
import re.kr.icuh.drought.application.openapi.agrimarket.service.AgriMarketService;
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

@WebMvcTest(AgriMarketApiController.class)
class AgriMarketApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgriMarketService agriMarketService;

    @Test
    @DisplayName("정상 요청이면 200과 SUCCESS 래퍼로 응답한다")
    void returnsSuccessForValidRequest() throws Exception {
        when(agriMarketService.getAgriMarketPricePredict(any()))
                .thenReturn(MonthlyMarketPredictionResponse.builder().year("2026").location("서울").build());

        mockMvc.perform(get("/api/v1/agrimarket/market-price")
                        .param("year", "2026")
                        .param("month", "4")
                        .param("location", "서울"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.year").value("2026"));
    }

    @Test
    @DisplayName("월이 범위를 벗어나면 400과 검증 메시지를 응답한다")
    void returnsBadRequestForInvalidMonth() throws Exception {
        mockMvc.perform(get("/api/v1/agrimarket/market-price")
                        .param("year", "2026")
                        .param("month", "13")
                        .param("location", "서울"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));
    }

    @Test
    @DisplayName("0을 채운 월도 허용하고 서비스에는 0을 뗀 값으로 전달한다")
    void normalizesZeroPaddedMonth() throws Exception {
        when(agriMarketService.getAgriMarketPricePredict(any()))
                .thenReturn(MonthlyMarketPredictionResponse.builder().year("2026").build());

        mockMvc.perform(get("/api/v1/agrimarket/market-price")
                        .param("year", "2026")
                        .param("month", "07")
                        .param("location", "서울"))
                .andExpect(status().isOk());

        ArgumentCaptor<AgriMarketRequest> captor = ArgumentCaptor.forClass(AgriMarketRequest.class);
        verify(agriMarketService).getAgriMarketPricePredict(captor.capture());
        assertThat(captor.getValue().month()).isEqualTo("7");
    }

    @Test
    @DisplayName("지역명이 비어 있으면 400을 응답한다")
    void returnsBadRequestForMissingLocation() throws Exception {
        mockMvc.perform(get("/api/v1/agrimarket/market-price")
                        .param("year", "2026")
                        .param("month", "4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E400"));
    }
}
