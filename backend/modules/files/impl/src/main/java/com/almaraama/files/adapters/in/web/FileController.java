package com.almaraama.files.adapters.in.web;

import com.almaraama.files.api.FilePurpose;
import com.almaraama.files.application.FileService;
import com.almaraama.files.application.FileService.Download;
import com.almaraama.files.domain.StoredFile;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.perimeter.Perimeter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Upload of photos and documents, reading through signed links (contract tag {@code files}, ADR-038). */
@RestController
class FileController {

    private final FileService files;

    /** @param files upload and reading of files */
    FileController(FileService files) {
        this.files = files;
    }

    /** A stored file as the uploader sees it (contract {@code UploadedFile}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UploadedFileResponse(UUID id, FilePurpose purpose, String contentType, long sizeBytes, String originalName,
                                Instant uploadedAt, Instant usableUntil) {
        /** From the stored file. */
        static UploadedFileResponse of(StoredFile file) {
            return new UploadedFileResponse(file.id(), file.purpose(), file.contentType(), file.sizeBytes(),
                    file.originalName().orElse(null), file.uploadedAt(),
                    file.uploadedAt().plus(StoredFile.USABLE_FOR));
        }
    }

    /** {@code POST /v1/files}: see {@link FileService#upload}. */
    @PostMapping(path = "/v1/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    UploadedFileResponse upload(Perimeter perimeter, @RequestParam("file") MultipartFile file,
                                @RequestParam("purpose") FilePurpose purpose) throws IOException {
        if (file.getSize() > purpose.maxBytes()) {
            throw new BusinessException("TOO_LARGE", ErrorCategory.TOO_LARGE, "File over the limit");
        }
        return UploadedFileResponse.of(files.upload(perimeter, purpose, file.getBytes(),
                file.getOriginalFilename()));
    }

    /**
     * {@code GET /v1/files/{id}/content}: public, protected by the signature (ADR-038). Strict headers: detected type,
     * {@code nosniff}, {@code sandbox}, private cache of 5 minutes; images inline, PDF as attachment.
     */
    @GetMapping("/v1/files/{fileId}/content")
    ResponseEntity<byte[]> content(@PathVariable UUID fileId, @RequestParam long expires,
                                   @RequestParam String signature) {
        Download download = files.read(fileId, expires, signature);
        StoredFile file = download.file();
        boolean pdf = MediaType.APPLICATION_PDF_VALUE.equals(file.contentType());
        String name = file.originalName().orElse(pdf ? "document.pdf" : "photo.jpg");
        ContentDisposition disposition = (pdf ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(name, java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(download.bytes());
    }
}
