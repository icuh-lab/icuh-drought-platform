package re.kr.icuh.drought.openapi.core.api.wildfire;

import re.kr.icuh.drought.application.openapi.wildfire.service.WildFireRiskIndexService;
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

@WebMvcTest(WildFireRiskIndexApiController.class)
class WildFireRiskIndexApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WildFireRiskIndexService wildFireRiskIndexService;

    @Test
    @DisplayName("뉴스 기사 정상 요청이면 200과 SUCCESS 래퍼로 응답한다")
    void returnsSuccessForValidNewsRequest() throws Exception {
        when(wildFireRiskIndexService.getNewsArticle(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/wild-fire-risk/news-article")
                        .param("year", "2026")
                        .param("month", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"));
    }

    @Test
    @DisplayName("월이 범위를 벗어나면 400과 검증 메시지를 응답한다")
    void returnsBadRequestForInvalidMonth() throws Exception {
        mockMvc.perform(get("/api/v1/wild-fire-risk/news-article")
                        .param("year", "2026")
                        .param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));
    }
}
