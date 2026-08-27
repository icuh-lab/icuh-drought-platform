package re.kr.icuh.drought.domain.article;

/**
 * 게시글의 국내/해외 구분값.
 *
 * <p>이 값은 목록 조회에서 {@code article.source.eq(요청값)} 완전 일치로 필터링된다. 그래서 표기가
 * 하나라도 흔들리면 그 글은 필터 결과에서 <b>조용히 빠진다</b> — 에러도 로그도 남지 않는다.
 *
 * <p>실제로 그런 일이 있었다. 검증이 없던 동안 운영 데이터에 {@code domestic} 78건과 {@code 국내}
 * 1건, {@code foreign} 4건과 {@code 해외} 2건이 함께 쌓였고, {@code source=domestic}으로 거른 목록에서
 * 1건이, {@code foreign}에서 2건이 빠져 있었다. 2026-08-27에 3건을 정규화하고 이 제약을 세웠다.
 *
 * <p>enum이 아니라 문자열 상수인 이유: 이 값을 담는 {@code UpdateArticleRequest}가
 * {@code articles.pending_update} 컬럼의 직렬화 포맷을 겸하고 있어, 타입을 바꾸면 이미 저장된 행을
 * 역직렬화할 수 없다. 검증만 세우는 쪽이 안전하다.
 */
public final class ArticleSource {

    public static final String DOMESTIC = "domestic";
    public static final String FOREIGN = "foreign";

    /** 앞뒤 공백과 대소문자 변형까지 막는다. eq 비교라 한 글자만 달라도 필터에서 빠지기 때문이다. */
    public static final String PATTERN = "^(domestic|foreign)$";

    public static final String MESSAGE = "source는 domestic 또는 foreign 이어야 합니다.";

    private ArticleSource() {
    }
}
