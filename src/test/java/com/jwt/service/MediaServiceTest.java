package com.jwt.service;

import com.jwt.dto.MediaDto;
import com.jwt.entity.Board;
import com.jwt.entity.MediaFile;
import com.jwt.entity.MediaType;
import com.jwt.entity.User;
import com.jwt.repository.MediaFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import org.apache.tika.Tika;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {
    @Mock AuthorizationService authorizationService;
    @Mock ObjectStorageService objectStorageService;
    @Mock MediaFileRepository mediaFileRepository;
    @Mock BoardService boardService;
    @Mock MediaLifecycleService mediaLifecycleService;
    @Mock MultipartFile multipartFile;

    MediaService mediaService;
    User admin;
    Board board;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(
                authorizationService,
                objectStorageService,
                mediaFileRepository,
                new MediaDtoMapper(),
                boardService,
                mediaLifecycleService
        );
        admin = new User();
        board = new Board();
        board.setBoardId(10L);
    }

    @Test
    void imageUploadStreamsDetectedContentWithoutCallingGetBytes() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        when(boardService.getPostEntity(10L)).thenReturn(board);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn((long) png.length);
        when(multipartFile.getOriginalFilename()).thenReturn("image.png");
        when(multipartFile.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(png));
        when(objectStorageService.upload(any(), any(), any(), anyLong(), any()))
                .thenAnswer(invocation -> new ObjectStorageService.StoredObject(invocation.getArgument(1), "etag"));
        when(mediaFileRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            MediaFile media = invocation.getArgument(0);
            media.setMediaFileId(99L);
            return media;
        });

        MediaDto.Response response = mediaService.uploadImage(10L, multipartFile, admin);

        assertThat(response.getUrl()).isEqualTo("/media/99");
        assertThat(response.getMimeType()).isEqualTo("image/png");
        verify(multipartFile, never()).getBytes();
        verify(objectStorageService).upload(
                org.mockito.ArgumentMatchers.eq(MediaType.IMAGE),
                org.mockito.ArgumentMatchers.startsWith("images/"),
                org.mockito.ArgumentMatchers.eq("image/png"),
                org.mockito.ArgumentMatchers.eq((long) png.length),
                any()
        );
    }

    @ParameterizedTest
    @MethodSource("imageFormatsWithAnimationSupport")
    void gifAndWebpUseImageStorage(String filename, String base64, String expectedMimeType) throws Exception {
        byte[] content = Base64.getDecoder().decode(base64);
        when(boardService.getPostEntity(10L)).thenReturn(board);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn((long) content.length);
        when(multipartFile.getOriginalFilename()).thenReturn(filename);
        when(multipartFile.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(content));
        when(objectStorageService.upload(any(), any(), any(), anyLong(), any()))
                .thenAnswer(invocation -> new ObjectStorageService.StoredObject(invocation.getArgument(1), "etag"));
        when(mediaFileRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            MediaFile media = invocation.getArgument(0);
            media.setMediaFileId(101L);
            return media;
        });

        MediaDto.Response response = mediaService.uploadImage(10L, multipartFile, admin);

        assertThat(response.getMimeType()).isEqualTo(expectedMimeType);
        verify(objectStorageService).upload(
                org.mockito.ArgumentMatchers.eq(MediaType.IMAGE),
                org.mockito.ArgumentMatchers.startsWith("images/"),
                org.mockito.ArgumentMatchers.eq(expectedMimeType),
                org.mockito.ArgumentMatchers.eq((long) content.length),
                any()
        );
    }

    private static Stream<Arguments> imageFormatsWithAnimationSupport() {
        return Stream.of(
                Arguments.of("animated.gif", "R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==", "image/gif"),
                Arguments.of("animated.webp", "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/vuUAAA=", "image/webp")
        );
    }

    @Test
    void spoofedMimeTypeIsRejectedFromActualBytes() throws Exception {
        byte[] text = "not an image".getBytes();
        when(boardService.getPostEntity(10L)).thenReturn(board);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn((long) text.length);
        when(multipartFile.getOriginalFilename()).thenReturn("fake.png");
        when(multipartFile.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(text));

        assertThatThrownBy(() -> mediaService.uploadImage(10L, multipartFile, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("실제 이미지 형식");
        verify(objectStorageService, never()).upload(any(), any(), any(), anyLong(), any());
    }

    @Test
    void mp4UploadUsesDetectedVideoTypeAndInputStream() throws Exception {
        byte[] mp4 = new byte[]{
                0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70,
                0x69, 0x73, 0x6f, 0x6d, 0, 0, 2, 0,
                0x69, 0x73, 0x6f, 0x6d, 0x69, 0x73, 0x6f, 0x32
        };
        assertThat(new Tika().detect(new ByteArrayInputStream(mp4), "video.mp4"))
                .isIn("video/mp4", "application/mp4");
        when(boardService.getPostEntity(10L)).thenReturn(board);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn((long) mp4.length);
        when(multipartFile.getOriginalFilename()).thenReturn("video.mp4");
        when(multipartFile.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(mp4));
        when(objectStorageService.upload(any(), any(), any(), anyLong(), any()))
                .thenAnswer(invocation -> new ObjectStorageService.StoredObject(invocation.getArgument(1), "etag"));
        when(mediaFileRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            MediaFile media = invocation.getArgument(0);
            media.setMediaFileId(100L);
            return media;
        });

        MediaDto.Response response = mediaService.uploadVideo(10L, multipartFile, admin);

        assertThat(response.getMimeType()).isEqualTo("video/mp4");
        verify(multipartFile, never()).getBytes();
        verify(objectStorageService).upload(
                org.mockito.ArgumentMatchers.eq(MediaType.VIDEO),
                org.mockito.ArgumentMatchers.startsWith("videos/"),
                org.mockito.ArgumentMatchers.eq("video/mp4"),
                org.mockito.ArgumentMatchers.eq((long) mp4.length),
                any()
        );
    }

    @Test
    void emptyAndOversizedFilesAreRejectedBeforeReading() {
        when(boardService.getPostEntity(10L)).thenReturn(board);
        when(multipartFile.isEmpty()).thenReturn(true);
        assertThatThrownBy(() -> mediaService.uploadImage(10L, multipartFile, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("파일이 없습니다");

        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(5L * 1024 * 1024 + 1);
        assertThatThrownBy(() -> mediaService.uploadImage(10L, multipartFile, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");

        when(multipartFile.getSize()).thenReturn(60L * 1024 * 1024 + 1);
        assertThatThrownBy(() -> mediaService.uploadVideo(10L, multipartFile, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60MB");
    }

    @Test
    void overlyLongFilenameIsRejectedBeforeObjectStorageUpload() {
        when(boardService.getPostEntity(10L)).thenReturn(board);
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(1L);
        when(multipartFile.getOriginalFilename()).thenReturn("a".repeat(181));

        assertThatThrownBy(() -> mediaService.uploadImage(10L, multipartFile, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("180자");
        verify(objectStorageService, never()).upload(any(), any(), any(), anyLong(), any());
    }
}
