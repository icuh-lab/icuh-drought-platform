package re.kr.icuh.drought.publicapi.article.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import re.kr.icuh.drought.domain.article.UpdateArticleRequest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code source}에 허용값 검증이 걸려 있는지 확인한다.
 *
 * <p>이 검증이 없던 동안 운영 데이터에 {@code domestic}과 {@code 국내}가 함께 쌓였다. 목록 조회의
 * source 필터가 {@code article.source.eq(...)} 완전 일치라서, {@code source=domestic}으로 거른
 * 결과에서 {@code 국내}로 저장된 글이 조용히 빠졌다. 2026-08-27에 운영 데이터 3건을 정규화했고,
 * 이 테스트는 그 상태가 다시 무너지지 않게 막는다.
 *
 * <p>프론트는 이미 영문 값만 보낸다 — {@code ArticleForm.tsx}가
 * {@code <option value="domestic">국내</option>} 형태로 한글은 라벨로만 쓴다. 따라서 이 검증이
 * 프론트의 기존 동작을 깨지 않는다.
 */
class ArticleSourceValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** source를 뺀 나머지는 전부 유효하게 채운다 — 위반이 나오면 그것은 source 때문이어야 한다. */
    private static CreateArticleWithFilesRequest createWith(String source) {
        return new CreateArticleWithFilesRequest(
                "제목", "설명", "작성자", "작성기관", "부서", "임시비밀번호",
                "DT001", "SD001", source, List.of()
        );
    }

    private static UpdateArticleRequest updateWith(String source) {
        return new UpdateArticleRequest(
                "제목", "설명", "작성자", "작성기관", "부서", "임시비밀번호",
                "DT001", "SD001", source, List.of()
        );
    }

    private static Set<String> violatedFields(Object target) {
        Set<ConstraintViolation<Object>> violations = validator.validate(target);
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Nested
    @DisplayName("허용된 값은 통과한다")
    class 허용 {

        @ParameterizedTest(name = "source=\"{0}\"")
        @ValueSource(strings = {"domestic", "foreign"})
        @DisplayName("생성 요청")
        void 생성_요청(String source) {
            assertThat(violatedFields(createWith(source))).isEmpty();
        }

        @ParameterizedTest(name = "source=\"{0}\"")
        @ValueSource(strings = {"domestic", "foreign"})
        @DisplayName("수정 요청")
        void 수정_요청(String source) {
            assertThat(violatedFields(updateWith(source))).isEmpty();
        }
    }

    @Nested
    @DisplayName("허용되지 않은 값은 거부한다")
    class 거부 {

        // "국내"·"해외"는 실제로 운영 데이터를 오염시킨 값이다.
        // 대소문자 변형과 공백도 함께 막는다 — eq 비교라 한 글자만 달라도 필터에서 빠진다.
        @ParameterizedTest(name = "source=\"{0}\"")
        @ValueSource(strings = {"국내", "해외", "DOMESTIC", "Foreign", " domestic", "domestic ", "", "unknown"})
        @DisplayName("생성 요청")
        void 생성_요청(String source) {
            assertThat(violatedFields(createWith(source))).contains("source");
        }

        @ParameterizedTest(name = "source=\"{0}\"")
        @ValueSource(strings = {"국내", "해외", "DOMESTIC", "Foreign", " domestic", "domestic ", "", "unknown"})
        @DisplayName("수정 요청")
        void 수정_요청(String source) {
            assertThat(violatedFields(updateWith(source))).contains("source");
        }
    }

    @Nested
    @DisplayName("null은 기존 @NotNull이 계속 잡는다")
    class 널 {

        @org.junit.jupiter.api.Test
        @DisplayName("생성 요청")
        void 생성_요청() {
            assertThat(violatedFields(createWith(null))).contains("source");
        }

        @org.junit.jupiter.api.Test
        @DisplayName("수정 요청")
        void 수정_요청() {
            assertThat(violatedFields(updateWith(null))).contains("source");
        }
    }
}
