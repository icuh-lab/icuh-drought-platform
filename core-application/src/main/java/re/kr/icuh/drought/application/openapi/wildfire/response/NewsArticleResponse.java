package re.kr.icuh.drought.application.openapi.wildfire.response;

import re.kr.icuh.drought.persistence.openapi.wildfire.entity.NewsArticle;

import java.time.LocalDate;

/**
 *       "id": 1023,
 *       "publishDate": "2023-03-04",
 *       "title": "울진 산불 1년... 숲에는 여전히 상처가",
 *       "linkUrl": "https://news.naver.com/...", // 클릭 시 이동할 링크
 *       "provinceName": "경상북도",
 *       "cityName": "울진",
 *       "category": "환경", // 기상영향분야
 *       "sentiment": "부정", // 감정분류 (긍정/부정/중립 or 태그 형태)
 *       "keywords": ["산불", "피해복구", "건조주의보"] // 감정분류 옆 태그 데이터
 */

public record NewsArticleResponse(
        Long id,
        LocalDate publishDate,
        String title,
        String linkUrl,
        String provinceName,
        String cityName,
        String category,
        String sentiment,
        String keywords
) {
    public static NewsArticleResponse of(NewsArticle newsArticle) {
        return new NewsArticleResponse(
                newsArticle.getId(),
                newsArticle.getPublishDate(),
                newsArticle.getTitle(),
                newsArticle.getLinkUrl(),
                newsArticle.getProvinceName(),
                newsArticle.getCityName(),
                newsArticle.getCategory(),
                newsArticle.getSentiment(),
                newsArticle.getKeywords()
        );
    }
}
