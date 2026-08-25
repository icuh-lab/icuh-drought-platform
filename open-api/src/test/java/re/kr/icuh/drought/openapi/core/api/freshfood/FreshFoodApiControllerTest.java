package re.kr.icuh.drought.openapi.core.api.freshfood;

import re.kr.icuh.drought.application.openapi.freshfood.response.FreshVegetableIndexResponse;
import re.kr.icuh.drought.application.openapi.freshfood.service.FreshFoodService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FreshFoodApiController.class)
class FreshFoodApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FreshFoodService freshFoodService;

    @Test
    @DisplayName("정상 요청이면 200과 SUCCESS 래퍼로 응답한다")
    void returnsSuccessForValidRequest() throws Exception {
        when(freshFoodService.getFreshVegetableIndex(any()))
                .thenReturn(new FreshVegetableIndexResponse("2026-04-01", List.of(), Map.of()));

        mockMvc.perform(get("/api/v1/freshfood/fresh-vegetable")
                        .param("year", "2026")
                        .param("month", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.baseDate").value("2026-04-01"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("월이 범위를 벗어나면 400과 검증 메시지를 응답한다")
    void returnsBadRequestForInvalidMonth() throws Exception {
        mockMvc.perform(get("/api/v1/freshfood/fresh-vegetable")
                        .param("year", "2026")
                        .param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"))
                .andExpect(jsonPath("$.error.data").value(org.hamcrest.Matchers.containsString("month")));
    }

    @Test
    @DisplayName("연도가 비어 있으면 400을 응답한다")
    void returnsBadRequestForMissingYear() throws Exception {
        mockMvc.perform(get("/api/v1/freshfood/fresh-vegetable")
                        .param("month", "4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));
    }
}
