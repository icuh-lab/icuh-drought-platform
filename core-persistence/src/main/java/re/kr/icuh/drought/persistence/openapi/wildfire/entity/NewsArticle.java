package re.kr.icuh.drought.persistence.openapi.wildfire.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Date;

@Entity
@Getter
@Table(name = "wild_fire_news_articles")
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate publishDate;
    private String title;
    private String linkUrl;
    private String provinceName;
    private String cityName;
    private String category;
    private String sentiment;
    private String keywords;
}
