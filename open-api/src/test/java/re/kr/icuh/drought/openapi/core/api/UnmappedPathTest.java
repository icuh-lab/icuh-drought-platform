package re.kr.icuh.drought.openapi.core.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import re.kr.icuh.drought.application.openapi.hydropower.service.HydroPowerService;
import re.kr.icuh.drought.openapi.core.api.hydropower.HydroPowerApiController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HydroPowerApiController.class)
class UnmappedPathTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HydroPowerService hydroPowerService;

    @Test
    @DisplayName("매핑되지 않은 경로는 500이 아니라 404를 내고, 데이터 없음과 다른 메시지로 구분된다")
    void 매핑되지_않은_경로는_404로_응답한다() throws Exception {
        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E404"))
                .andExpect(jsonPath("$.error.message").value("Endpoint Not Found"));
    }

    @Test
    @DisplayName("매핑된 경로에서 터진 진짜 예외는 그대로 500이다")
    void 진짜_예외는_500으로_남는다() throws Exception {
        when(hydroPowerService.getMonthlyGeneration(any())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/v1/hydropower/monthly-generation")
                        .param("year", "2026")
                        .param("damName", "합천"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("E500"));
    }
}
