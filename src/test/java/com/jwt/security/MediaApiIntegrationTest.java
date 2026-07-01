package com.jwt.security;

import com.jwt.dto.BoardDto;
import com.jwt.dto.CategoryDto;
import com.jwt.entity.Board;
import com.jwt.entity.CategoryVisibility;
import com.jwt.entity.MediaFile;
import com.jwt.entity.MediaStatus;
import com.jwt.entity.MediaType;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.jwt.JwtTokenProvider;
import com.jwt.repository.BoardRepository;
import com.jwt.repository.MediaFileRepository;
import com.jwt.repository.UserRepository;
import com.jwt.service.BoardService;
import com.jwt.service.CategoryService;
import com.jwt.service.ObjectStorageService;
import com.jwt.service.ObjectStorageException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MediaApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired BoardRepository boardRepository;
    @Autowired MediaFileRepository mediaFileRepository;
    @Autowired BoardService boardService;
    @Autowired CategoryService categoryService;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @MockitoBean ObjectStorageService objectStorageService;

    @Value("${jwt.access-cookie-name}") String accessCookieName;

    @Test
    void uploadRequiresAdminAndReturnsOnlyApplicationMediaUrl() throws Exception {
        User admin = userRepository.saveAndFlush(user("media-admin@example.com", UserRole.ADMIN));
        User member = userRepository.saveAndFlush(user("media-user@example.com", UserRole.USER));
        BoardDto.Response post = boardService.createBoard(new BoardDto.Request("Post", "Body"), admin);
        MockMultipartFile image = pngFile();
        when(objectStorageService.upload(any(), any(), any(), anyLong(), any()))
                .thenAnswer(invocation -> new ObjectStorageService.StoredObject(invocation.getArgument(1), "etag"));

        mockMvc.perform(multipart("/api/admin/uploads/images")
                        .file(image).param("postId", post.getPostId().toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/uploads/images")
                        .file(image).param("postId", post.getPostId().toString())
                        .cookie(accessCookie(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/uploads/images")
                        .file(image).param("postId", post.getPostId().toString())
                        .cookie(accessCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url", startsWith("/media/")));

        verify(objectStorageService).upload(
                org.mockito.ArgumentMatchers.eq(MediaType.IMAGE),
                org.mockito.ArgumentMatchers.startsWith("images/"),
                org.mockito.ArgumentMatchers.eq("image/png"),
                anyLong(),
                any()
        );
    }

    @Test
    void spoofedImageIsRejectedBeforeObjectStorage() throws Exception {
        User admin = userRepository.saveAndFlush(user("media-spoof-admin@example.com", UserRole.ADMIN));
        BoardDto.Response post = boardService.createBoard(new BoardDto.Request("Post", "Body"), admin);
        MockMultipartFile fake = new MockMultipartFile(
                "file", "fake.png", "image/png", "plain text".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/admin/uploads/images")
                        .file(fake).param("postId", post.getPostId().toString())
                        .cookie(accessCookie(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(objectStorageService, never()).upload(any(), any(), any(), anyLong(), any());
    }

    @Test
    void objectStorageFailureReturnsServiceUnavailable() throws Exception {
        User admin = userRepository.saveAndFlush(user("media-unavailable-admin@example.com", UserRole.ADMIN));
        BoardDto.Response post = boardService.createBoard(new BoardDto.Request("Post", "Body"), admin);
        when(objectStorageService.upload(any(), any(), any(), anyLong(), any()))
                .thenThrow(new ObjectStorageException("unavailable", new RuntimeException("down"), false));

        mockMvc.perform(multipart("/api/admin/uploads/images")
                        .file(pngFile()).param("postId", post.getPostId().toString())
                        .cookie(accessCookie(admin)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void publicMediaSupportsAnonymousFullAndRangeStreaming() throws Exception {
        User admin = userRepository.saveAndFlush(user("media-public-admin@example.com", UserRole.ADMIN));
        Board board = boardRepository.findById(
                boardService.createBoard(new BoardDto.Request("Public", "Body"), admin).getPostId()).orElseThrow();
        MediaFile media = mediaFileRepository.saveAndFlush(media(board, admin, MediaStatus.ACTIVE));

        when(objectStorageService.get(MediaType.VIDEO, media.getObjectKey()))
                .thenReturn(storedContent("0123456789", null));
        mockMvc.perform(get("/media/{id}", media.getMediaFileId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().bytes("0123456789".getBytes(StandardCharsets.UTF_8)));

        when(objectStorageService.getRange(
                org.mockito.ArgumentMatchers.eq(MediaType.VIDEO),
                org.mockito.ArgumentMatchers.eq(media.getObjectKey()),
                any(ObjectStorageService.ObjectRange.class)))
                .thenReturn(storedContent("2345", "bytes 2-5/10"));
        mockMvc.perform(get("/media/{id}", media.getMediaFileId())
                        .header(HttpHeaders.RANGE, "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-5/10"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "4"))
                .andExpect(content().bytes("2345".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/media/{id}", media.getMediaFileId())
                        .header(HttpHeaders.RANGE, "bytes=50-60"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void privateUnpublishedDraftOrOrphanMediaReturnsNotFoundToNonAdmin() throws Exception {
        User admin = userRepository.saveAndFlush(user("media-policy-admin@example.com", UserRole.ADMIN));
        User member = userRepository.saveAndFlush(user("media-policy-user@example.com", UserRole.USER));
        CategoryDto.Request privateRequest = new CategoryDto.Request();
        privateRequest.setKey("media-private");
        privateRequest.setDisplayName("Private");
        privateRequest.setVisibility(CategoryVisibility.PRIVATE);
        CategoryDto.Response category = categoryService.create(privateRequest, admin);

        BoardDto.Request privatePost = new BoardDto.Request("Private", "Body");
        privatePost.setCategoryKey(category.getKey());
        Board privateBoard = boardRepository.findById(boardService.createBoard(privatePost, admin).getPostId()).orElseThrow();
        MediaFile privateMedia = mediaFileRepository.saveAndFlush(media(privateBoard, admin, MediaStatus.ACTIVE));

        BoardDto.Request unpublishedPost = new BoardDto.Request("Unpublished", "Body");
        unpublishedPost.setPublished(false);
        Board unpublishedBoard = boardRepository.findById(boardService.createBoard(unpublishedPost, admin).getPostId()).orElseThrow();
        MediaFile unpublishedMedia = mediaFileRepository.saveAndFlush(media(unpublishedBoard, admin, MediaStatus.ACTIVE));

        Board draftBoard = boardRepository.findById(boardService.createDraft(admin).getPostId()).orElseThrow();
        MediaFile draftMedia = mediaFileRepository.saveAndFlush(media(draftBoard, admin, MediaStatus.ACTIVE));
        MediaFile orphanMedia = mediaFileRepository.saveAndFlush(media(privateBoard, admin, MediaStatus.ORPHAN));
        MediaFile unlinkedMedia = mediaFileRepository.saveAndFlush(media(null, admin, MediaStatus.ACTIVE));

        for (Long id : new Long[]{privateMedia.getMediaFileId(), unpublishedMedia.getMediaFileId(), draftMedia.getMediaFileId(), orphanMedia.getMediaFileId(), unlinkedMedia.getMediaFileId()}) {
            mockMvc.perform(get("/media/{id}", id).cookie(accessCookie(member)))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/media/{id}", unlinkedMedia.getMediaFileId()).cookie(accessCookie(admin)))
                .andExpect(status().isNotFound());

        when(objectStorageService.get(MediaType.VIDEO, privateMedia.getObjectKey()))
                .thenReturn(storedContent("0123456789", null));
        mockMvc.perform(get("/media/{id}", privateMedia.getMediaFileId())
                        .cookie(accessCookie(admin)))
                .andExpect(status().isOk());
    }

    private ObjectStorageService.StoredContent storedContent(String value, String range) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new ObjectStorageService.StoredContent(
                new ByteArrayInputStream(bytes), bytes.length, "video/mp4", "etag", range);
    }

    private MockMultipartFile pngFile() {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        return new MockMultipartFile("file", "image.png", "image/png", png);
    }

    private MediaFile media(Board board, User uploader, MediaStatus status) {
        MediaFile media = new MediaFile();
        media.setOriginalFilename("video.mp4");
        media.setObjectKey("videos/" + java.util.UUID.randomUUID());
        media.setUrl("");
        media.setMimeType("video/mp4");
        media.setSize(10L);
        media.setMediaType(MediaType.VIDEO);
        media.setUploader(uploader);
        media.setBoard(board);
        media.setStatus(status);
        return media;
    }

    private Cookie accessCookie(User user) {
        Cookie cookie = new Cookie(accessCookieName,
                jwtTokenProvider.createAccessToken(String.valueOf(user.getUserId()), user.getRole()));
        cookie.setPath("/");
        return cookie;
    }

    private User user(String email, UserRole role) {
        return User.builder()
                .email(email)
                .name(email)
                .password("{noop}password")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
