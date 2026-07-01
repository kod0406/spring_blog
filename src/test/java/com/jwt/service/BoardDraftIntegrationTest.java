package com.jwt.service;

import com.jwt.dto.BoardDto;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.repository.BoardRepository;
import com.jwt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BoardDraftIntegrationTest {
    @Autowired BoardService boardService;
    @Autowired BoardRepository boardRepository;
    @Autowired UserRepository userRepository;

    @MockitoBean ObjectStorageService objectStorageService;

    @Test
    void draftCanBeSavedManuallyThenPublishedAndIsHiddenUntilPublish() {
        User admin = userRepository.saveAndFlush(User.builder()
                .email("draft-admin@example.com")
                .name("Admin")
                .password("{noop}password")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        BoardDto.Response created = boardService.createDraft(admin);
        BoardDto.Request partial = new BoardDto.Request();
        partial.setTitle("작성 중");
        partial.setContentMarkdown("");
        BoardDto.Response saved = boardService.saveDraft(created.getPostId(), partial, admin);

        assertThat(saved.getDraft()).isTrue();
        assertThat(saved.getPublished()).isFalse();
        assertThat(boardService.getPosts(null, PageRequest.of(0, 10), null).getContent()).isEmpty();

        BoardDto.Request publish = new BoardDto.Request("게시 글", "본문");
        publish.setPublished(true);
        BoardDto.Response published = boardService.updateBoard(created.getPostId(), publish, admin);

        assertThat(published.getDraft()).isFalse();
        assertThat(published.getPublished()).isTrue();
        assertThat(boardService.getPosts(null, PageRequest.of(0, 10), null).getContent())
                .extracting(BoardDto.Response::getPostId)
                .containsExactly(created.getPostId());
    }

    @Test
    void adminDraftFilterReturnsDrafts() {
        User admin = userRepository.saveAndFlush(User.builder()
                .email("draft-filter-admin@example.com")
                .name("Admin")
                .password("{noop}password")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        Long draftId = boardService.createDraft(admin).getPostId();
        boardService.createBoard(new BoardDto.Request("Published", "Body"), admin);

        assertThat(boardService.getAdminPosts("draft", null, PageRequest.of(0, 10), admin).getContent())
                .extracting(BoardDto.Response::getPostId)
                .containsExactly(draftId);
    }
}
