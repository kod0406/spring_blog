package com.jwt.controller;

import com.jwt.dto.BoardDto;
import com.jwt.dto.RegistrationDto;
import com.jwt.dto.loginDto;
import com.jwt.entity.User;
import com.jwt.jwt.JwtCookieUtil;
import com.jwt.jwt.JwtTokenProvider;
import com.jwt.redis.TokenRedisService;
import com.jwt.service.AuthorizationService;
import com.jwt.service.BoardService;
import com.jwt.service.CategoryService;
import com.jwt.service.UserAccountRecoveryService;
import com.jwt.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebController {

    private final UserService userService;
    private final UserAccountRecoveryService userAccountRecoveryService;
    private final BoardService boardService;
    private final CategoryService categoryService;
    private final AuthorizationService authorizationService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenRedisService tokenRedisService;
    private final JwtCookieUtil jwtCookieUtil;

    @Value("${jwt.refresh-Millis}")
    private long refreshExpirationMillis;

    @GetMapping("/")
    public String index(Model model, @AuthenticationPrincipal User user) {
        Page<BoardDto.Response> posts = boardService.getPosts(null, null, null, null, PageRequest.of(0, 5, Sort.by("createdAt").descending()), user);
        model.addAttribute("posts", posts.getContent());
        model.addAttribute("categories", categoryService.getVisibleCategories(user));
        model.addAttribute("isAdmin", authorizationService.isAdmin(user));
        return "index";
    }

    @GetMapping({"/posts", "/board"})
    public String postList(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(required = false) String category,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(required = false) String searchType,
                           @RequestParam(required = false) String sort,
                           @AuthenticationPrincipal User user,
                           Model model) {
        int resolvedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(resolvedPage, 10, Sort.by("createdAt").descending());
        Page<BoardDto.Response> posts = boardService.getPosts(category, keyword, searchType, sort, pageable, user);

        model.addAttribute("posts", posts);
        model.addAttribute("boards", posts);
        model.addAttribute("categories", categoryService.getVisibleCategories(user));
        model.addAttribute("selectedCategory", category);
        model.addAttribute("keyword", keyword);
        model.addAttribute("searchType", searchType == null || searchType.isBlank() ? "title_content" : searchType);
        model.addAttribute("sort", sort == null || sort.isBlank() ? "latest" : sort);
        model.addAttribute("currentPage", resolvedPage);
        model.addAttribute("totalPages", posts.getTotalPages());
        model.addAttribute("isAdmin", authorizationService.isAdmin(user));
        return "board/list";
    }

    @GetMapping({"/posts/{postId}", "/board/{postId}"})
    public String postDetail(@PathVariable Long postId,
                             Model model,
                             @AuthenticationPrincipal User user,
                             RedirectAttributes redirectAttributes) {
        return WebRedirectSupport.redirectWithError(redirectAttributes, "redirect:/posts", null, () -> {
            BoardDto.Response post = boardService.getBoardById(postId, user);
            boolean isAdmin = authorizationService.isAdmin(user);
            boolean canComment = Boolean.TRUE.equals(post.getPrivatePost()) ? isAdmin : authorizationService.isActiveUser(user);
            model.addAttribute("post", post);
            model.addAttribute("board", post);
            model.addAttribute("canComment", canComment);
            model.addAttribute("isAdmin", isAdmin);
            return "board/detail";
        });
    }

    @GetMapping({"/admin/posts/new", "/posts/write", "/board/write"})
    public String writeForm(@AuthenticationPrincipal User user, Model model, RedirectAttributes redirectAttributes) {
        return WebRedirectSupport.redirectWithError(redirectAttributes, "redirect:/login", null, () -> {
            authorizationService.requireAdmin(user);
            model.addAttribute("boardDto", new BoardDto.Request());
            model.addAttribute("categories", categoryService.getAllCategories(user));
            return "board/write";
        });
    }

    @PostMapping({"/admin/posts", "/posts/write", "/board/write"})
    public String writePost(@ModelAttribute BoardDto.Request boardDto,
                            @AuthenticationPrincipal User user,
                            RedirectAttributes redirectAttributes) {
        return WebRedirectSupport.redirectWithError(redirectAttributes, "redirect:/admin/posts/new", "글 작성 실패: ", () -> {
            BoardDto.Response post = boardService.createBoard(boardDto, user);
            redirectAttributes.addFlashAttribute("message", "글이 작성되었습니다.");
            return "redirect:/posts/" + post.getPostId();
        });
    }

    @GetMapping({"/admin/posts/{postId}/edit", "/posts/{postId}/edit", "/board/{postId}/edit"})
    public String editForm(@PathVariable Long postId, Model model, @AuthenticationPrincipal User user, RedirectAttributes redirectAttributes) {
        return WebRedirectSupport.redirectWithError(redirectAttributes, "redirect:/posts", null, () -> {
            BoardDto.Response post = boardService.getAdminPost(postId, user);
            BoardDto.Request editDto = new BoardDto.Request(post.getTitle(), post.getContent());
            editDto.setCategoryKey(post.getCategoryKey());
            editDto.setPublished(post.getPublished());
            model.addAttribute("boardDto", editDto);
            model.addAttribute("post", post);
            model.addAttribute("postId", postId);
            model.addAttribute("boardId", postId);
            model.addAttribute("categories", categoryService.getAllCategories(user));
            return "board/edit";
        });
    }

    @PostMapping({"/admin/posts/{postId}/edit", "/posts/{postId}/edit", "/board/{postId}/edit"})
    public String editPost(@PathVariable Long postId,
                           @ModelAttribute BoardDto.Request boardDto,
                           @AuthenticationPrincipal User user,
                           RedirectAttributes redirectAttributes) {
        return WebRedirectSupport.redirectWithError(redirectAttributes, "redirect:/admin/posts/" + postId + "/edit", "수정 실패: ", () -> {
            boardService.updateBoard(postId, boardDto, user);
            redirectAttributes.addFlashAttribute("message", "글이 수정되었습니다.");
            return "redirect:/posts/" + postId;
        });
    }

    @PostMapping({"/admin/posts/{postId}/delete", "/posts/{postId}/delete", "/board/{postId}/delete"})
    public String deletePost(@PathVariable Long postId,
                             @AuthenticationPrincipal User user,
                             RedirectAttributes redirectAttributes) {
        return WebRedirectSupport.redirectWithError(redirectAttributes, "redirect:/posts/" + postId, "삭제 실패: ", () -> {
            boardService.deleteBoard(postId, user);
            redirectAttributes.addFlashAttribute("message", "글이 삭제되었습니다.");
            return "redirect:/posts";
        });
    }

    @GetMapping("/email")
    public String emailForm() {
        return "email";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registrationDto", new RegistrationDto());
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute RegistrationDto registrationDto, RedirectAttributes redirectAttributes) {
        return WebRedirectSupport.redirectWithError(redirectAttributes, "redirect:/register", "회원가입 실패: ", () -> {
            userService.registerUser(registrationDto);
            redirectAttributes.addFlashAttribute("message", "회원가입이 완료되었습니다. 관리자 승인 후 로그인할 수 있습니다.");
            return "redirect:/login";
        });
    }

    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("loginDto", new loginDto());
        return "login";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute loginDto loginDto, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.authenticateUser(loginDto.getEmail(), loginDto.getPassword());

            String accessToken = jwtTokenProvider.createAccessToken(String.valueOf(user.getUserId()), user.getRole());
            String refreshToken = jwtTokenProvider.createRefreshToken(String.valueOf(user.getUserId()), user.getRole());

            ResponseCookie accessTokenCookie = jwtCookieUtil.createAccessTokenCookie(accessToken);
            response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
            ResponseCookie refreshTokenCookie = jwtCookieUtil.createRefreshTokenCookie(refreshToken);
            response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

            tokenRedisService.saveRefreshToken(String.valueOf(user.getUserId()), refreshToken, refreshExpirationMillis);

            redirectAttributes.addFlashAttribute("message", "로그인되었습니다.");
            return "redirect:/posts";
        } catch (Exception e) {
            log.warn("[로그인 실패] {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "로그인 실패: " + e.getMessage());
            return "redirect:/login";
        }
    }

    @PostMapping("/logout")
    public String logout(@AuthenticationPrincipal User user, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        if (user != null) {
            tokenRedisService.deleteRefreshToken(String.valueOf(user.getUserId()));
        }

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieUtil.deleteAccessTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieUtil.deleteRefreshTokenCookie().toString());
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        redirectAttributes.addFlashAttribute("message", "로그아웃되었습니다.");
        return "redirect:/";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm() {
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String email,
                                @RequestParam String verificationCode,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                RedirectAttributes redirectAttributes) {
        return WebRedirectSupport.redirectWithError(redirectAttributes, "redirect:/reset-password", "비밀번호 재설정 실패: ", () -> {
            userAccountRecoveryService.resetPassword(email, verificationCode, newPassword, confirmPassword);
            redirectAttributes.addFlashAttribute("message", "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.");
            return "redirect:/login";
        });
    }
}
