package re.kr.icuh.drought.adminapi.core.api.controller.v1;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import re.kr.icuh.drought.common.error.BusinessException;
import re.kr.icuh.drought.common.error.ErrorCode;
import re.kr.icuh.drought.adminapi.core.domain.FileEditRequest;
import re.kr.icuh.drought.persistence.article.entity.FileEntity;
import re.kr.icuh.drought.adminapi.core.domain.FileEditRequestRepository;
import re.kr.icuh.drought.adminapi.core.domain.FileRepository;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FileDownloadController {

    private final AmazonS3Client amazonS3Client;
    private final FileRepository fileRepository;
    private final FileEditRequestRepository fileEditRequestRepository;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable Long fileId) {
        // 파일 정보 조회
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        try {
            // S3에서 파일 가져오기
            S3Object s3Object = amazonS3Client.getObject(new GetObjectRequest(bucket, fileEntity.getStoredFilename()));

            // 파일 이름 인코딩 (한글 등 지원)
            String encodedFileName = URLEncoder.encode(fileEntity.getOriginalFilename(), StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            // 응답 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(s3Object.getObjectMetadata().getContentLength()));

            // 스트림으로 응답
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(s3Object.getObjectContent()));

        } catch (UnsupportedEncodingException e) {
            log.error("파일 이름 인코딩 오류", e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_ERROR);
        } catch (Exception e) {
            log.error("파일 다운로드 오류", e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_ERROR);
        }
    }

    @GetMapping("/update/files/{fileId}/download")
    public ResponseEntity<InputStreamResource> downloadUpdateFile(@PathVariable Long fileId) {
        // 파일 정보 조회
        FileEditRequest fileEntity = fileEditRequestRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        try {
            // S3에서 파일 가져오기
            S3Object s3Object = amazonS3Client.getObject(new GetObjectRequest(bucket, fileEntity.getStoredFilename()));

            // 파일 이름 인코딩 (한글 등 지원)
            String encodedFileName = URLEncoder.encode(fileEntity.getOriginalFilename(), StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            // 응답 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(s3Object.getObjectMetadata().getContentLength()));

            // 스트림으로 응답
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(s3Object.getObjectContent()));

        } catch (UnsupportedEncodingException e) {
            log.error("파일 이름 인코딩 오류", e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_ERROR);
        } catch (Exception e) {
            log.error("파일 다운로드 오류", e);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_ERROR);
        }
    }
}