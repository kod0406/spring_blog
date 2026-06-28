package com.jwt.controller;

import com.jwt.entity.User;
import com.jwt.exception.RangeNotSatisfiableException;
import com.jwt.service.MediaService;
import com.jwt.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequiredArgsConstructor
public class MediaController {
    private static final Pattern SINGLE_RANGE = Pattern.compile("^bytes=(\\d*)-(\\d*)$");

    private final MediaService mediaService;

    @GetMapping("/media/{mediaId}")
    public ResponseEntity<InputStreamResource> getMedia(
            @PathVariable Long mediaId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @AuthenticationPrincipal User user
    ) {
        ObjectStorageService.ObjectRange range = parseRange(rangeHeader);
        MediaService.MediaDownload download = mediaService.open(mediaId, user, range);
        ObjectStorageService.StoredContent content = download.content();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.mimeType()));
        headers.setContentLength(content.contentLength());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(download.originalFilename(), StandardCharsets.UTF_8)
                .build());
        if (content.eTag() != null && !content.eTag().isBlank()) {
            String eTag = content.eTag();
            headers.setETag(eTag.startsWith("\"") ? eTag : "\"" + eTag + "\"");
        }
        if (download.partial()) {
            headers.set(HttpHeaders.CONTENT_RANGE, download.contentRange());
        }
        headers.setCacheControl(CacheControl.noStore());

        InputStreamResource body = new InputStreamResource(content.inputStream());
        return new ResponseEntity<>(body, headers, download.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK);
    }

    private ObjectStorageService.ObjectRange parseRange(String rangeHeader) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return null;
        }
        Matcher matcher = SINGLE_RANGE.matcher(rangeHeader.trim());
        if (!matcher.matches() || matcher.group(1).isEmpty() && matcher.group(2).isEmpty()) {
            throw new RangeNotSatisfiableException("단일 bytes Range만 지원합니다.");
        }
        try {
            Long start = matcher.group(1).isEmpty() ? null : Long.parseLong(matcher.group(1));
            Long end = matcher.group(2).isEmpty() ? null : Long.parseLong(matcher.group(2));
            return new ObjectStorageService.ObjectRange(start, end);
        } catch (IllegalArgumentException e) {
            throw new RangeNotSatisfiableException("유효하지 않은 Range입니다.");
        }
    }
}
