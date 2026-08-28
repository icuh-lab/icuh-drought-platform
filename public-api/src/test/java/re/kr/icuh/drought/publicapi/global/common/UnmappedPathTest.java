package re.kr.icuh.drought.publicapi.global.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import re.kr.icuh.drought.publicapi.article.api.ArticleController;
import re.kr.icuh.drought.publicapi.article.application.ArticleService;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 앱 클래스(IcuhPlatformApplication)에 @EnableJpaRepositories가 붙어 있어 슬라이스만으로는
// EntityManagerFactory를 요구한다. 라우팅 동작만 보면 되므로 컨텍스트를 명시해 JPA를 끊는다.
@WebMvcTest
@ContextConfiguration(classes = {ArticleController.class, GlobalExceptionHandler.class})
class UnmappedPathTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleService articleService;

    @Test
    @DisplayName("매핑되지 않은 경로는 500이 아니라 404를 내고, 데이터 없음과 다른 코드로 구분된다")
    void 매핑되지_않은_경로는_404로_응답한다() throws Exception {
        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error.code").value("ENDPOINT_NOT_FOUND"));
    }

    @Test
    @DisplayName("매핑된 경로에서 터진 진짜 예외는 그대로 500이다")
    void 진짜_예외는_500으로_남는다() throws Exception {
        when(articleService.findArticleById(anyLong())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/api/v1/articles/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));
    }
}
