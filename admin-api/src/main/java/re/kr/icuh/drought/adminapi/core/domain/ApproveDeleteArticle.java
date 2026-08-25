package re.kr.icuh.drought.adminapi.core.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.domain.article.ArticleStatus;
import re.kr.icuh.drought.domain.article.FileStatus;

@Service
@RequiredArgsConstructor
public class ApproveDeleteArticle {

    private final ArticleQueryRepository articleQueryRepository;

    @Transactional
    public void approveDeleteArticle(Long id) {
        Article article = articleQueryRepository.findArticle(id);
        article.changeStatus(ArticleStatus.DELETED);

        article.getFiles().stream()
                .filter(file -> file.getStatus() == FileStatus.DELETED_PENDING)
                .forEach(file -> file.changeStatus(FileStatus.DELETED));
    }
}
