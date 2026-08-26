package re.kr.icuh.drought.domain.article;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link ArticleStatus}의 전이표 검증.
 *
 * <p>이 표는 실제 코드에서 상태를 쓰는 10개 경로를 전수 조사해 도출한 것이므로,
 * 테스트도 그 10개 경로를 그대로 따라간다. 표를 좁히면 지금 성공하는 관리자 조작이 실패하므로
 * <b>소스를 검사하지 않는 경로는 모든 소스 상태에서</b> 허용되는지 확인한다.
 */
class ArticleStatusTransitionTest {

    @Nested
    @DisplayName("전이표의 10개 경로가 전부 허용된다")
    class 허용 {

        // 1번(생성 -> PENDING)은 빌더 기본값이고 전이가 아니므로 canTransitionTo의 대상이 아니다.
        // 대신 "어떤 상태에서도 PENDING으로 전이할 수 없다"는 거부 테스트로 다룬다.

        @ParameterizedTest(name = "{0} -> DELETED_PENDING")
        @EnumSource(ArticleStatus.class)
        @DisplayName("2번 Article.delete(): 소스 제약이 없으므로 모든 상태에서 DELETED_PENDING으로 갈 수 있다")
        void 경로2_모든_상태에서_DELETED_PENDING(ArticleStatus source) {
            assertThat(source.canTransitionTo(ArticleStatus.DELETED_PENDING)).isTrue();
        }

        @Test
        @DisplayName("3번 Article.reject(): PENDING -> REJECTED")
        void 경로3_PENDING에서_REJECTED() {
            assertThat(ArticleStatus.PENDING.canTransitionTo(ArticleStatus.REJECTED)).isTrue();
        }

        @ParameterizedTest(name = "{0} -> APPROVED")
        @EnumSource(ArticleStatus.class)
        @DisplayName("4번 ApproveCreateArticle: findArticle(id)에 상태 필터가 없으므로 모든 상태에서 APPROVED로 갈 수 있다")
        void 경로4_모든_상태에서_APPROVED(ArticleStatus source) {
            assertThat(source.canTransitionTo(ArticleStatus.APPROVED)).isTrue();
        }

        @ParameterizedTest(name = "{0} -> DELETED")
        @EnumSource(ArticleStatus.class)
        @DisplayName("5번 ApproveDeleteArticle: 소스 제약이 없으므로 모든 상태에서 DELETED로 갈 수 있다")
        void 경로5_모든_상태에서_DELETED(ArticleStatus source) {
            assertThat(source.canTransitionTo(ArticleStatus.DELETED)).isTrue();
        }

        @ParameterizedTest(name = "{0} -> APPROVED")
        @EnumSource(ArticleStatus.class)
        @DisplayName("6번 ArticleFinder.updateArticleStatus: findById에 상태 필터가 없으므로 모든 상태에서 APPROVED로 갈 수 있다")
        void 경로6_모든_상태에서_APPROVED(ArticleStatus source) {
            assertThat(source.canTransitionTo(ArticleStatus.APPROVED)).isTrue();
        }

        @ParameterizedTest(name = "{0} -> REJECTED")
        @EnumSource(ArticleStatus.class)
        @DisplayName("7번 ArticleFinder.rejectArticle: findById에 상태 필터가 없으므로 모든 상태에서 REJECTED로 갈 수 있다")
        void 경로7_모든_상태에서_REJECTED(ArticleStatus source) {
            assertThat(source.canTransitionTo(ArticleStatus.REJECTED)).isTrue();
        }

        @Test
        @DisplayName("8번 ArticleFinder.applyPendingUpdate: APPROVED -> APPROVED 자기 전이를 반드시 허용한다")
        void 경로8_APPROVED_자기_전이() {
            assertThat(ArticleStatus.APPROVED.canTransitionTo(ArticleStatus.APPROVED)).isTrue();
        }

        @ParameterizedTest(name = "{0} -> UPDATED_APPROVED")
        @EnumSource(ArticleStatus.class)
        @DisplayName("9번 ApproveUpdateArticle(Article): 소스 제약이 없으므로 모든 상태에서 UPDATED_APPROVED로 갈 수 있다")
        void 경로9_모든_상태에서_UPDATED_APPROVED(ArticleStatus source) {
            assertThat(source.canTransitionTo(ArticleStatus.UPDATED_APPROVED)).isTrue();
        }

        @Test
        @DisplayName("10번 ApproveUpdateArticle(ArticleEditRequest): UPDATED_PENDING -> UPDATED_APPROVED")
        void 경로10_UPDATED_PENDING에서_UPDATED_APPROVED() {
            assertThat(ArticleStatus.UPDATED_PENDING.canTransitionTo(ArticleStatus.UPDATED_APPROVED)).isTrue();
        }
    }

    @Nested
    @DisplayName("어떤 경로로도 도달할 수 없는 전이는 거부된다")
    class 거부 {

        /*
         * Article의 상태를 쓰는 호출은 전부 컴파일 타임 리터럴이며(APPROVED / REJECTED / DELETED /
         * DELETED_PENDING / UPDATED_APPROVED), PENDING이나 UPDATED_PENDING을 목표로 삼는 호출은 없다.
         * PENDING은 게시글 생성 시 빌더 기본값으로만, UPDATED_PENDING은 ArticleEditRequest 생성 시
         * 기본값으로만 나타난다. 둘 다 "생성"이지 "전이"가 아니므로 목표 상태로는 도달 불가능하다.
         */

        @ParameterizedTest(name = "{0} -> PENDING")
        @EnumSource(ArticleStatus.class)
        @DisplayName("PENDING은 생성 시 기본값으로만 설정되므로 어떤 상태에서도 전이할 수 없다 (예: DELETED -> PENDING)")
        void PENDING으로는_전이할_수_없다(ArticleStatus source) {
            assertThat(source.canTransitionTo(ArticleStatus.PENDING)).isFalse();
        }

        @ParameterizedTest(name = "{0} -> UPDATED_PENDING")
        @EnumSource(ArticleStatus.class)
        @DisplayName("UPDATED_PENDING은 수정 요청 생성 시 기본값으로만 설정되므로 어떤 상태에서도 전이할 수 없다")
        void UPDATED_PENDING으로는_전이할_수_없다(ArticleStatus source) {
            assertThat(source.canTransitionTo(ArticleStatus.UPDATED_PENDING)).isFalse();
        }

        @ParameterizedTest(name = "{0} -> null")
        @EnumSource(ArticleStatus.class)
        @DisplayName("null은 상태가 아니므로 전이 대상이 될 수 없다")
        void null로는_전이할_수_없다(ArticleStatus source) {
            assertThat(source.canTransitionTo(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("전이표 초기화")
    class 초기화 {

        /*
         * EnumSet/EnumMap을 enum 생성자나 인스턴스 필드 초기화식에서 만들면 아직 대입되지 않은 상수를
         * 참조해 NullPointerException -> ExceptionInInitializerError로 깨진다. 아래 두 테스트는 모든
         * (소스, 목표) 쌍을 실제로 호출하므로 표가 일부만 채워져 있어도(= 순환 초기화가 났어도) 실패한다.
         */

        @ParameterizedTest(name = "source={0}")
        @EnumSource(ArticleStatus.class)
        @DisplayName("모든 소스 상태에 대해 전이표가 채워져 있어 어떤 조합도 예외 없이 판정된다")
        void 모든_소스_상태에_대해_판정이_가능하다(ArticleStatus source) {
            for (ArticleStatus target : ArticleStatus.values()) {
                assertThatCode(() -> source.canTransitionTo(target)).doesNotThrowAnyException();
            }
        }

        @ParameterizedTest(name = "source={0}")
        @EnumSource(ArticleStatus.class)
        @DisplayName("모든 소스 상태는 무제약 경로 5개의 목표 상태를 전부 허용한다")
        void 모든_소스_상태가_무제약_목표를_허용한다(ArticleStatus source) {
            assertThat(source.canTransitionTo(ArticleStatus.APPROVED)).isTrue();
            assertThat(source.canTransitionTo(ArticleStatus.REJECTED)).isTrue();
            assertThat(source.canTransitionTo(ArticleStatus.DELETED)).isTrue();
            assertThat(source.canTransitionTo(ArticleStatus.DELETED_PENDING)).isTrue();
            assertThat(source.canTransitionTo(ArticleStatus.UPDATED_APPROVED)).isTrue();
        }
    }
}
