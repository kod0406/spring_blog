package com.jwt.controller;

import com.jwt.dto.ApiResponse;
import com.jwt.dto.MediaDto;
import com.jwt.entity.User;
import com.jwt.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class UploadController {
    private final MediaService mediaService;

    @PostMapping("/api/admin/uploads/images")
    public ResponseEntity<ApiResponse<MediaDto.Response>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok("이미지가 업로드되었습니다.", mediaService.uploadImage(file, user)));
    }

    @PostMapping("/api/admin/uploads/videos")
    public ResponseEntity<ApiResponse<MediaDto.Response>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok("영상이 업로드되었습니다.", mediaService.uploadVideo(file, user)));
    }
}
