package re.kr.icuh.drought.adminapi.core.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticleListResponse;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListUpdateApprovedArticles {

    private final ArticleQueryRepository articleQueryRepository;

    @Transactional(readOnly = true)
    public List<ArticleListResponse> findUpdateApprovedArticles() {
        return articleQueryRepository.findUpdatedApprovedArticles().stream()
                .map(ArticleListResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
