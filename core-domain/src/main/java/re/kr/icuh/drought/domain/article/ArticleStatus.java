package re.kr.icuh.drought.domain.article;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 게시글 승인 워크플로우의 상태.
 *
 * <p><b>주의:</b> 이 상수 이름은 {@code articles.status} / {@code article_edit_requests.status} 컬럼에
 * {@code @Enumerated(EnumType.STRING)}으로 저장된다. 이름을 바꾸면 기존 데이터를 읽을 수 없다.
 *
 * <h2>상태 전이표</h2>
 *
 * <p>이 표는 "이상적인 워크플로우"가 아니라 <b>실제 코드에서 상태를 쓰는 10개 경로를 전수 조사해
 * 도출한 것</b>이다. 10개 중 소스 상태를 실제로 좁히는 경로는 3·8·10번뿐이고, 나머지 6개는
 * ID로만 로드해 상태를 전혀 검사하지 않는다(목록 조회 쿼리만 상태로 필터링한다).
 * 따라서 그 6개 경로의 목표 상태는 <b>모든 소스 상태에서</b> 허용해야 한다.
 * 좁게 만들면 지금 200을 반환하는 관리자 조작이 예외를 던지게 되고, 그것은 HTTP 계약 변경이다.
 *
 * <pre>
 * #  쓰는 지점                                     목표 상태          소스 상태        근거
 * 1  publicapi ArticleService (생성)               PENDING           - (신규)        빌더 기본값, 전이가 아님
 * 2  Article.delete()                              DELETED_PENDING   제약 없음        findById, 상태 필터 없음
 * 3  Article.reject()                              REJECTED          PENDING 만      메서드 안의 명시적 가드
 * 4  ApproveCreateArticle                          APPROVED          제약 없음        findArticle(id), 필터 없음
 * 5  ApproveDeleteArticle                          DELETED           제약 없음        findArticle(id), 필터 없음
 * 6  ArticleFinder.updateArticleStatus             APPROVED          제약 없음        findById, 필터 없음
 * 7  ArticleFinder.rejectArticle                   REJECTED          제약 없음        findById, 필터 없음
 * 8  ArticleFinder.applyPendingUpdate              APPROVED          APPROVED        mergeArticle: findById + pendingUpdate != null (자기 전이)
 * 9  ApproveUpdateArticle (Article)                UPDATED_APPROVED  제약 없음        findArticle(...), 필터 없음
 * 10 ApproveUpdateArticle (ArticleEditRequest)     UPDATED_APPROVED  UPDATED_PENDING 수정 요청은 UPDATED_PENDING으로 생성된다
 * </pre>
 *
 * <p>8번은 <b>{@code APPROVED -> APPROVED} 자기 전이</b>다. 이 계획의 초안은 이를
 * {@code UPDATED_APPROVED -> APPROVED}로 잘못 적었다. 자기 전이를 빼면 수정 승인이 런타임에 깨진다.
 *
 * <p><b>8·10번의 소스 제약은 실제로는 표보다 더 느슨하다.</b> 상태로 필터링하는 쿼리는
 * <i>목록/상세 조회</i>에만 쓰이고 <i>변경 경로</i>에는 쓰이지 않는다:
 * 8번의 실제 진입점 {@code ArticleFinder.mergeArticle}은 {@code findById} + {@code pendingUpdate != null}만
 * 검사하고({@code findPendingUpdateArticle()}의 {@code status='APPROVED'} 필터는 목록 조회 전용),
 * 10번의 실제 진입점 {@code ApproveUpdateArticle}은 상태 필터가 없는 {@code findUpdatedRequestArticle(id)}로
 * 로드한다({@code UPDATED_PENDING}으로 좁히는 {@code findUpdatePendingArticle(id)}는 상세 조회 전용).
 * 두 경우 모두 목표 상태({@code APPROVED} / {@code UPDATED_APPROVED})가 6·9번의 무제약 경로에서
 * 이미 모든 소스에 허용되므로 이 표의 결과는 달라지지 않는다.
 *
 * <p>위 표를 합치면 결과는 이렇게 된다:
 *
 * <pre>
 * 모든 상태 -&gt; APPROVED, REJECTED, DELETED, DELETED_PENDING, UPDATED_APPROVED   (허용)
 * 모든 상태 -&gt; PENDING, UPDATED_PENDING                                          (거부)
 * </pre>
 *
 * <p>거부되는 두 목표 상태는 <b>어떤 경로로도 도달할 수 없다</b>:
 * {@code Article}의 상태를 쓰는 호출은 전부 컴파일 타임 리터럴이고 그중 {@code PENDING} /
 * {@code UPDATED_PENDING}을 목표로 삼는 것은 하나도 없다. {@code PENDING}은 생성 시 빌더 기본값으로만,
 * {@code UPDATED_PENDING}은 {@code ArticleEditRequest} 생성 시 기본값으로만 나타난다(둘 다 전이가 아니다).
 *
 * <p>3·8·10번의 좁은 제약은 6개 무제약 경로에 흡수되므로 표를 더 좁히지 않는다.
 * 특히 {@code Article.reject()}의 {@code PENDING} 전용 가드는 예외 타입과 {@code ErrorCode}가
 * 테스트된 계약이므로 <b>이 표로 대체하지 않고 그대로 둔다.</b>
 */
public enum ArticleStatus {
    PENDING,
    APPROVED,
    REJECTED,
    DELETED,
    UPDATED_PENDING,
    UPDATED_APPROVED,
    DELETED_PENDING;

    /**
     * 소스 상태별 허용 목표 상태.
     *
     * <p>enum 상수는 static 초기화 블록이 실행되는 시점에는 이미 전부 대입돼 있다.
     * 이 맵을 생성자나 인스턴스 필드 초기화식에서 만들면 아직 대입되지 않은 상수를 참조해
     * 순환 초기화로 깨지므로, 반드시 여기(static 블록)에서 만든다.
     */
    private static final Map<ArticleStatus, Set<ArticleStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<ArticleStatus, Set<ArticleStatus>> table = new EnumMap<>(ArticleStatus.class);
        for (ArticleStatus source : values()) {
            table.put(source, EnumSet.noneOf(ArticleStatus.class));
        }

        // 아래 등록은 위 Javadoc 전이표의 각 행과 1:1로 대응한다. (1번은 생성이라 전이가 아니다.)
        allowFromEveryState(table, DELETED_PENDING);   // 2  Article.delete()
        allow(table, PENDING, REJECTED);               // 3  Article.reject()
        allowFromEveryState(table, APPROVED);          // 4  ApproveCreateArticle
        allowFromEveryState(table, DELETED);           // 5  ApproveDeleteArticle
        allowFromEveryState(table, APPROVED);          // 6  ArticleFinder.updateArticleStatus
        allowFromEveryState(table, REJECTED);          // 7  ArticleFinder.rejectArticle
        allow(table, APPROVED, APPROVED);              // 8  ArticleFinder.applyPendingUpdate (자기 전이)
        allowFromEveryState(table, UPDATED_APPROVED);  // 9  ApproveUpdateArticle (Article)
        allow(table, UPDATED_PENDING, UPDATED_APPROVED); // 10 ApproveUpdateArticle (ArticleEditRequest)

        Map<ArticleStatus, Set<ArticleStatus>> frozen = new EnumMap<>(ArticleStatus.class);
        table.forEach((source, targets) -> frozen.put(source, Collections.unmodifiableSet(targets)));
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(frozen);
    }

    /** 소스 상태를 검사하지 않는 경로: 현재 존재하는 모든 상태에서 {@code target}으로 갈 수 있다. */
    private static void allowFromEveryState(Map<ArticleStatus, Set<ArticleStatus>> table, ArticleStatus target) {
        for (ArticleStatus source : values()) {
            allow(table, source, target);
        }
    }

    private static void allow(Map<ArticleStatus, Set<ArticleStatus>> table, ArticleStatus source, ArticleStatus target) {
        table.get(source).add(target);
    }

    /**
     * 이 상태에서 {@code next} 상태로 전이할 수 있는지 판정한다.
     * 같은 상태로의 자기 전이는 허용될 수 있다(8번 경로의 {@code APPROVED -> APPROVED}).
     *
     * @param next 목표 상태. {@code null}이면 상태가 아니므로 항상 {@code false}.
     */
    public boolean canTransitionTo(ArticleStatus next) {
        if (next == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.get(this).contains(next);
    }
}
