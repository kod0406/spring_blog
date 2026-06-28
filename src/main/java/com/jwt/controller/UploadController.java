package com.jwt.controller;

import com.jwt.dto.ApiResponse;
import com.jwt.dto.MediaDto;
import com.jwt.entity.User;
import com.jwt.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class UploadController {
    private final MediaService mediaService;

    @PostMapping("/api/admin/uploads/images")
    public ResponseEntity<ApiResponse<MediaDto.Response>> uploadImage(
            @RequestParam("postId") Long postId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok("이미지가 업로드되었습니다.", mediaService.uploadImage(postId, file, user)));
    }

    @PostMapping("/api/admin/uploads/videos")
    public ResponseEntity<ApiResponse<MediaDto.Response>> uploadVideo(
            @RequestParam("postId") Long postId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok("영상이 업로드되었습니다.", mediaService.uploadVideo(postId, file, user)));
    }

    @DeleteMapping("/api/admin/media/{mediaId}")
    public ResponseEntity<ApiResponse<Void>> orphanMedia(
            @PathVariable Long mediaId,
            @AuthenticationPrincipal User user
    ) {
        mediaService.markOrphan(mediaId, user);
        return ResponseEntity.ok(ApiResponse.ok("미디어가 삭제 대기 상태로 변경되었습니다."));
    }
}
