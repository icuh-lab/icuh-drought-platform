package re.kr.icuh.drought.openapi.core.api.predictionvintage;

import re.kr.icuh.drought.application.openapi.predictionvintage.response.PredictionVintageResponse;
import re.kr.icuh.drought.application.openapi.predictionvintage.service.PredictionVintageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PredictionVintageController.class)
class PredictionVintageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PredictionVintageService predictionVintageService;

    @Test
    @DisplayName("정상 요청이면 200과 SUCCESS 래퍼로 응답한다")
    void returnsSuccessForValidRequest() throws Exception {
        when(predictionVintageService.getPredictionVintage(any()))
                .thenReturn(new PredictionVintageResponse("합천", "양파", "1키로/상", List.of()));

        mockMvc.perform(get("/api/v1/agrimarket/prediction-vintage")
                        .param("location", "합천"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.location").value("합천"));
    }

    @Test
    @DisplayName("지역명이 비어 있으면 400을 응답한다")
    void returnsBadRequestForMissingLocation() throws Exception {
        mockMvc.perform(get("/api/v1/agrimarket/prediction-vintage"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E400"));
    }
}
