package re.kr.icuh.drought.adminapi.core.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.domain.article.ArticleStatus;
import re.kr.icuh.drought.domain.article.FileStatus;
import re.kr.icuh.drought.persistence.article.entity.Article;

@Service
@RequiredArgsConstructor
public class ApproveCreateArticle {

    private final ArticleQueryRepository articleQueryRepository;

    @Transactional
    public void approveCreateArticle(Long id) {
        Article article = articleQueryRepository.findArticle(id);
        article.changeStatus(ArticleStatus.APPROVED);

        article.getFiles().stream()
                .filter(file -> file.getStatus() == FileStatus.PENDING)
                .forEach(file -> file.changeStatus(FileStatus.APPROVED));
    }
}
