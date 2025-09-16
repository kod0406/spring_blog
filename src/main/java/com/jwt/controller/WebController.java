package com.jwt.controller;

import com.jwt.dto.BoardDto;
import com.jwt.dto.RegistrationDto;
import com.jwt.dto.loginDto;
import com.jwt.entity.User;
import com.jwt.service.BoardService;
import com.jwt.service.UserService;
import com.jwt.jwt.JwtTokenProvider;
import com.jwt.redis.TokenRedisService;
import com.jwt.jwt.JwtCookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebController {

    private final UserService userService;
    private final BoardService boardService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenRedisService tokenRedisService;
    private final JwtCookieUtil jwtCookieUtil;

    @org.springframework.beans.factory.annotation.Value("${jwt.refresh-Millis}")
    private long refreshExpirationMillis;

    // 메인 페이지 (로그인 전 랜딩 페이지)
    @GetMapping("/")
    public String index() {
        return "index";
    }

    // 이메일 테스트 페이지
    @GetMapping("/email")
    public String emailForm() {
        return "email";
    }

    // 게시판 목록 페이지 (로그인 후)
    @GetMapping("/board")
    public String boardList(@RequestParam(defaultValue = "0") int page, Model model, @AuthenticationPrincipal User user) {
        if (user == null) {
            return "redirect:/login";
        }

        Pageable pageable = PageRequest.of(page, 10, Sort.by("createdAt").descending());
        Page<BoardDto.Response> boards = boardService.getAllBoards(pageable);

        model.addAttribute("boards", boards);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", boards.getTotalPages());
        return "board/list";
    }

    // 회원가입 페이지
    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registrationDto", new RegistrationDto());
        return "register";
    }

    // 회원가입 처리
    @PostMapping("/register")
    public String register(@ModelAttribute RegistrationDto registrationDto, RedirectAttributes redirectAttributes) {
        try {
            userService.registerUser(registrationDto);
            redirectAttributes.addFlashAttribute("message", "회원가입이 완료되었습니다. 로그인해주세요.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "회원가입 실패: " + e.getMessage());
            return "redirect:/register";
        }
    }

    // 로그인 페이지
    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("loginDto", new loginDto());
        return "login";
    }

    // 로그인 처리
    @PostMapping("/login")
    public String login(@ModelAttribute loginDto loginDto, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.authenticateUser(loginDto.getEmail(), loginDto.getPassword());

            log.info("[로그인 인증 성공] 사용자 이름: {}, 이메일: {}", user.getName(), user.getEmail());

            // JWT 액세스 토큰 생성
            String accessToken = jwtTokenProvider.createAccessToken(String.valueOf(user.getUserId()), user.getRole());
            // JWT 리프레시 토큰 생성
            String refreshToken = jwtTokenProvider.createRefreshToken(String.valueOf(user.getUserId()), user.getRole());

            // 토큰을 쿠키에 저장
            ResponseCookie accessTokenCookie = jwtCookieUtil.createAccessTokenCookie(accessToken);
            response.addHeader("Set-Cookie", accessTokenCookie.toString());
            ResponseCookie refreshTokenCookie = jwtCookieUtil.createRefreshTokenCookie(refreshToken);
            response.addHeader("Set-Cookie", refreshTokenCookie.toString());

            // 리프레시 토큰을 Redis에 저장
            tokenRedisService.saveRefreshToken(String.valueOf(user.getUserId()), refreshToken, refreshExpirationMillis);

            redirectAttributes.addFlashAttribute("message", "로그인 성공!");
            return "redirect:/board";  // 로그인 성공 후 게시판으로 이동
        } catch (Exception e) {
            log.error("[로그인 실패] {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "로그인 실패: " + e.getMessage());
            return "redirect:/login";
        }
    }

    // 로그아웃 처리
    @PostMapping("/logout")
    public String logout(@AuthenticationPrincipal User user, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        try {
            log.info("[로그아웃 시작] 사용자: {}", user != null ? user.getName() : "익명");

            if (user != null) {
                // Redis에서 리프레시 토큰 삭제
                tokenRedisService.deleteRefreshToken(String.valueOf(user.getUserId()));
                log.info("[로그아웃] 리프레시 토큰 삭제 완료");
            }

            // 토큰 쿠키 삭제
            ResponseCookie deleteAccessTokenCookie = jwtCookieUtil.deleteAccessTokenCookie();
            response.addHeader("Set-Cookie", deleteAccessTokenCookie.toString());
            ResponseCookie deleteRefreshTokenCookie = jwtCookieUtil.deleteRefreshTokenCookie();
            response.addHeader("Set-Cookie", deleteRefreshTokenCookie.toString());

            log.info("[로그아웃] 쿠키 삭제 헤더 추가: {}", deleteAccessTokenCookie.toString());

            // SecurityContext 정리
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            log.info("[로그아웃] SecurityContext 정리 완료");

            if (user != null) {
                log.info("[로그아웃 완료] 사용자: {}", user.getName());
            }
            redirectAttributes.addFlashAttribute("message", "로그아웃 되었습니다.");
            return "redirect:/login";
        } catch (Exception e) {
            log.error("[로그아웃 실패] {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "로그아웃 실패: " + e.getMessage());
            return "redirect:/login";
        }
    }

    // 비밀번호 재설정 페이지
    @GetMapping("/reset-password")
    public String resetPasswordForm() {
        return "reset-password";
    }

    // 이메일 확인 API (AJAX 요청용)
    @PostMapping("/reset-password/check-email")
    @ResponseBody
    public Map<String, Boolean> checkEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        Map<String, Boolean> response = new HashMap<>();

        try {
            // UserService를 통해 이메일로 사용자 조회
            User user = userService.findByEmail(email);
            response.put("success", true);
            log.info("[비밀번호 재설정] 이메일 확인 성공: {}", email);
        } catch (Exception e) {
            response.put("success", false);
            log.warn("[비밀번호 재설정] 이메일 확인 실패: {}", email);
        }

        return response;
    }

    // 비밀번호 재설정 처리
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String email,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                RedirectAttributes redirectAttributes) {
        try {
            // 비밀번호 확인
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
                return "redirect:/reset-password";
            }

            // 비밀번호 길이 검증
            if (newPassword.length() < 4) {
                redirectAttributes.addFlashAttribute("error", "비밀번호는 최소 4자 이상이어야 합니다.");
                return "redirect:/reset-password";
            }

            // 사용자 조회 및 비밀번호 변경
            userService.updatePassword(email, newPassword);

            log.info("[비밀번호 재설정 완료] 사용자 이메일: {}", email);
            redirectAttributes.addFlashAttribute("message", "비밀번호가 성공적으로 변경되었습니다. 새 비밀번호로 로그인해주세요.");
            return "redirect:/login";

        } catch (IllegalArgumentException e) {
            log.error("[비밀번호 재설정 실패] {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "비밀번호 재설정 실패: " + e.getMessage());
            return "redirect:/reset-password";
        } catch (Exception e) {
            log.error("[비밀번호 재설정 오류] {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/reset-password";
        }
    }

    // 게시글 작성 페이지
    @GetMapping("/board/write")
    public String writeForm(@AuthenticationPrincipal User user, Model model) {
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("boardDto", new BoardDto.Request());
        return "board/write";
    }

    // 게시글 작성 처리
    @PostMapping("/board/write")
    public String writeBoard(@ModelAttribute BoardDto.Request boardDto,
                             @AuthenticationPrincipal User user,
                             RedirectAttributes redirectAttributes) {
        if (user == null) {
            return "redirect:/login";
        }

        try {
            boardService.createBoard(boardDto, user);
            redirectAttributes.addFlashAttribute("message", "게시글이 작성되었습니다.");
            return "redirect:/board";  // 게시판으로 이동
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "게시글 작성 실패: " + e.getMessage());
            return "redirect:/board/write";
        }
    }

    // 게시글 상세보기
    @GetMapping("/board/{boardId}")
    public String boardDetail(@PathVariable Long boardId, Model model, @AuthenticationPrincipal User user) {
        try {
            BoardDto.Response board = boardService.getBoardById(boardId);
            model.addAttribute("board", board);
            // 현재 로그인한 사용자가 게시글 작성자인지 확인
            model.addAttribute("isAuthor", user != null && user.getName().equals(board.getAuthorName()));
            return "board/detail";
        } catch (IllegalArgumentException e) {
            return "redirect:/board";
        }
    }

    // 게시글 수정 페이지
    @GetMapping("/board/{boardId}/edit")
    public String editForm(@PathVariable Long boardId, Model model, @AuthenticationPrincipal User user, RedirectAttributes redirectAttributes) {
        if (user == null) {
            return "redirect:/login";
        }

        try {
            BoardDto.Response board = boardService.getBoardById(boardId);
            if (!user.getName().equals(board.getAuthorName())) {
                redirectAttributes.addFlashAttribute("error", "수정 권한이 없습니다.");
                return "redirect:/board/" + boardId;
            }

            BoardDto.Request editDto = new BoardDto.Request(board.getTitle(), board.getContent());
            model.addAttribute("boardDto", editDto);
            model.addAttribute("boardId", boardId);
            return "board/edit";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "해당 게시글을 찾을 수 없습니다.");
            return "redirect:/board";
        }
    }

    // 게시글 수정 처리
    @PostMapping("/board/{boardId}/edit")
    public String editBoard(@PathVariable Long boardId,
                           @ModelAttribute BoardDto.Request boardDto,
                           @AuthenticationPrincipal User user,
                           RedirectAttributes redirectAttributes) {
        if (user == null) {
            return "redirect:/login";
        }

        try {
            boardService.updateBoard(boardId, boardDto, user);
            redirectAttributes.addFlashAttribute("message", "게시글이 수정되었습니다.");
            return "redirect:/board/" + boardId;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "수정 실패: " + e.getMessage());
            return "redirect:/board/" + boardId + "/edit";
        }
    }

    // 게시글 삭제 처리
    @PostMapping("/board/{boardId}/delete")
    public String deleteBoard(@PathVariable Long boardId,
                            @AuthenticationPrincipal User user,
                            RedirectAttributes redirectAttributes) {
        if (user == null) {
            return "redirect:/login";
        }

        try {
            boardService.deleteBoard(boardId, user);
            redirectAttributes.addFlashAttribute("message", "게시글이 삭제되었습니다.");
            return "redirect:/board";  // 게시판으로 이동
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "삭제 실패: " + e.getMessage());
            return "redirect:/board/" + boardId;
        }
    }
}