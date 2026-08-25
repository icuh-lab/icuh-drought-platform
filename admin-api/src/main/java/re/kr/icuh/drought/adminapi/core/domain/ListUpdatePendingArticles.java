package re.kr.icuh.drought.adminapi.core.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import re.kr.icuh.drought.adminapi.core.api.controller.v1.response.ArticleEditRequestListResponse;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListUpdatePendingArticles {

    private final ArticleQueryRepository articleQueryRepository;

    public List<ArticleEditRequestListResponse> findUpdatePendingArticles() {

        return articleQueryRepository.findUpdatedPendingArticles().stream()
                .map(ArticleEditRequestListResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
