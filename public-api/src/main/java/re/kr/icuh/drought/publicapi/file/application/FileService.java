package re.kr.icuh.drought.publicapi.file.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import re.kr.icuh.drought.publicapi.article.domain.Article;
import re.kr.icuh.drought.publicapi.article.infra.ArticleRepository;
import re.kr.icuh.drought.domain.article.FileStatus;
import re.kr.icuh.drought.publicapi.file.domain.FileEntity;
import re.kr.icuh.drought.publicapi.file.dto.request.CompleteUploadRequestDto;
import re.kr.icuh.drought.publicapi.file.dto.response.CompleteUploadResponseDto;
import re.kr.icuh.drought.publicapi.file.infra.FileRepository;
import re.kr.icuh.drought.common.error.BusinessException;
import re.kr.icuh.drought.common.error.ErrorCode;
import re.kr.icuh.drought.publicapi.global.common.FileUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileUtils fileUtils;
    private final FileRepository fileRepository;
    private final ArticleRepository articleRepository;

    @Transactional
    public void createFileEntity(CompleteUploadRequestDto request, String location) {
        Article savedArticle = articleRepository.findById(request.getArticleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        FileEntity fileEntity = FileEntity.builder()
                .article(savedArticle)
                .originalFilename(request.getOriginFileName())
                .storedFilename(request.getFileName())
                .filePath(location)
                .fileSize(request.getFileSize())
                .extension(fileUtils.extractExtensionName(request.getOriginFileName()))
                .status(FileStatus.PENDING)
                .build();

        fileRepository.save(fileEntity);
    }

    @Transactional
    public CompleteUploadResponseDto updateFileMetaData(CompleteUploadRequestDto request, String location) {
        // article id로 게시글을 찾고, 해당 게시글의 pending_update 컬럼에 값이 있다면 해당 게시글은 업데이트 대기 중인 상태
        return CompleteUploadResponseDto.builder()
                .originalFileName(request.getOriginFileName())
                .storedFileName(request.getFileName())
                .filePath(location)
                .fileSize(request.getFileSize())
                .extension(fileUtils.extractExtensionName(request.getOriginFileName()))
                .build();
    }

}
