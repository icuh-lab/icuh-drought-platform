package re.kr.icuh.drought.publicapi.file.api;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import re.kr.icuh.drought.publicapi.file.application.FileService;
import re.kr.icuh.drought.publicapi.file.domain.FileEntity;
import re.kr.icuh.drought.publicapi.file.dto.request.CompleteUploadRequestDto;
import re.kr.icuh.drought.publicapi.file.dto.request.InitiateUploadRequestDto;
import re.kr.icuh.drought.publicapi.file.dto.request.PresignedUrlRequestDto;
import re.kr.icuh.drought.publicapi.file.dto.response.CompleteUploadResponseDto;
import re.kr.icuh.drought.publicapi.file.dto.response.InitiateUploadResponseDto;
import re.kr.icuh.drought.publicapi.file.infra.FileRepository;
import re.kr.icuh.drought.common.error.BusinessException;
import re.kr.icuh.drought.common.error.ErrorCode;
import re.kr.icuh.drought.publicapi.global.common.FileUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/multipart-upload")
@RequiredArgsConstructor
public class FileController {

    private final AmazonS3Client amazonS3Client;
    private final FileRepository fileRepository;
    private final FileService fileStorageService;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @PostMapping("/generate-upload-id")
    public ResponseEntity<InitiateUploadResponseDto> generateUploadId(@RequestBody InitiateUploadRequestDto request) {
        // NOTE: 원본 파일명, 디비에 저장될 파일명, 파일 사이즈를 기반으로 파일 메타데이터 생성
        FileUtils fileUtils = new FileUtils();
        String storedFileName = fileUtils.createStoreFileName(request.getFileName());

        String s3Key = "upload/" + storedFileName;

        InitiateMultipartUploadResult initiateUpload = amazonS3Client.initiateMultipartUpload(new InitiateMultipartUploadRequest(bucketName, s3Key));
        String uploadId = initiateUpload.getUploadId();

        log.info("Initiated upload for {}: {}", s3Key, uploadId);

        return ResponseEntity.ok(new InitiateUploadResponseDto(uploadId, s3Key));
    }

    @PostMapping("/presigned-url")
    public ResponseEntity<String> presignedUrl(@RequestBody PresignedUrlRequestDto request) {
        // Pre-Signed URL 만료기간은 현재 시간으로부터 10분뒤까지만
        Date expirationDate = Date.from(LocalDateTime.now().plusMinutes(10).atZone(ZoneId.systemDefault()).toInstant());

        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, request.getFileName())
                .withMethod(HttpMethod.PUT)
                .withExpiration(expirationDate);

        generatePresignedUrlRequest.addRequestParameter("uploadId", request.getUploadId());
        generatePresignedUrlRequest.addRequestParameter("partNumber", String.valueOf(request.getPartNumber()));

        String presignedUrl = amazonS3Client.generatePresignedUrl(generatePresignedUrlRequest).toString();
        log.info("Generated presigned URL for part {}: {}", request.getPartNumber(), presignedUrl);

        return ResponseEntity.ok(presignedUrl);
    }

    @PostMapping("/complete-upload")
    public ResponseEntity<CompleteUploadResponseDto> completeUpload(@RequestBody CompleteUploadRequestDto request) {

        List<PartETag> partETags = request.getParts().stream()
                .map(part -> new PartETag(part.getPartNumber(), part.getEtag()))
                .collect(Collectors.toList());

        CompleteMultipartUploadRequest completeMultipartUploadRequest = new CompleteMultipartUploadRequest(
                bucketName,
                request.getFileName(),
                request.getUploadId(),
                partETags
        );

        CompleteMultipartUploadResult completeMultipartUploadResult = amazonS3Client.completeMultipartUpload(completeMultipartUploadRequest);
        log.info("Upload complete for {}", request.getFileName());

        // ❌ 기존: DB 저장
        // fileStorageService.createFileEntity(request, completeMultipartUploadResult.getLocation());

        // ✅ 수정: DB 저장 안 하고, 파일 메타데이터만 응답
        CompleteUploadResponseDto response = fileStorageService.updateFileMetaData(
                request, completeMultipartUploadResult.getLocation()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/update-upload")
    public ResponseEntity<CompleteUploadResponseDto> updateUpload(@RequestBody CompleteUploadRequestDto request) {

        // 클라이언트가 보낸 etag 리스트를 AWS SDK용 PartETag 리스트로 변환
        List<PartETag> partETags = request.getParts().stream()
                .map(part -> new PartETag(part.getPartNumber(), part.getEtag()))
                .collect(Collectors.toList());

        CompleteMultipartUploadRequest completeMultipartUploadRequest = new CompleteMultipartUploadRequest(
                bucketName,
                request.getFileName(),
                request.getUploadId(),
                partETags
        );

        CompleteMultipartUploadResult completeMultipartUploadResult = amazonS3Client.completeMultipartUpload(completeMultipartUploadRequest);

        return ResponseEntity.ok(fileStorageService.updateFileMetaData(request, completeMultipartUploadResult.getLocation()));
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable Long fileId) {
        // 파일 정보 조회
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        try {
            // S3에서 파일 가져오기
            S3Object s3Object = amazonS3Client.getObject(new GetObjectRequest(bucketName, fileEntity.getStoredFilename()));
            
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
