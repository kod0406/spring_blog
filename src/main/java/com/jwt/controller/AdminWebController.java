package com.jwt.controller;

import com.jwt.dto.CategoryDto;
import com.jwt.entity.User;
import com.jwt.service.AuthorizationService;
import com.jwt.service.BoardService;
import com.jwt.service.CategoryService;
import com.jwt.service.CommentService;
import com.jwt.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
public class AdminWebController {
    private final AuthorizationService authorizationService;
    private final BoardService boardService;
    private final CategoryService categoryService;
    private final UserService userService;
    private final CommentService commentService;

    @GetMapping("/admin")
    public String dashboard(@AuthenticationPrincipal User user, Model model, RedirectAttributes redirectAttributes) {
        if (!requireAdmin(user, redirectAttributes)) {
            return "redirect:/login";
        }
        model.addAttribute("categories", categoryService.getAllCategories(user).size());
        model.addAttribute("posts", boardService.getAdminPosts("all", null, PageRequest.of(0, 1), user).getTotalElements());
        model.addAttribute("users", userService.getAllUsers(user).size());
        model.addAttribute("comments", commentService.getAllComments(user).size());
        return "admin/dashboard";
    }

    @GetMapping("/admin/posts")
    public String posts(@RequestParam(defaultValue = "all") String visibility,
                        @RequestParam(required = false) String category,
                        @RequestParam(defaultValue = "0") int page,
                        @AuthenticationPrincipal User user,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (!requireAdmin(user, redirectAttributes)) {
            return "redirect:/login";
        }
        model.addAttribute("posts", boardService.getAdminPosts(visibility, category, PageRequest.of(page, 20, Sort.by("createdAt").descending()), user));
        model.addAttribute("categories", categoryService.getAllCategories(user));
        model.addAttribute("visibility", visibility);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("currentPage", page);
        return "admin/posts";
    }

    @GetMapping({"/admin/categories", "/admin/settings/categories"})
    public String categories(@AuthenticationPrincipal User user, Model model, RedirectAttributes redirectAttributes) {
        if (!requireAdmin(user, redirectAttributes)) {
            return "redirect:/login";
        }
        model.addAttribute("categories", categoryService.getAllCategories(user));
        model.addAttribute("categoryDto", new CategoryDto.Request());
        return "admin/categories";
    }

    @PostMapping({"/admin/categories", "/admin/settings/categories"})
    public String createCategory(@ModelAttribute CategoryDto.Request request,
                                 @AuthenticationPrincipal User user,
                                 RedirectAttributes redirectAttributes) {
        try {
            categoryService.create(request, user);
            redirectAttributes.addFlashAttribute("message", "글머리가 생성되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping({"/admin/categories/{categoryId}/edit", "/admin/settings/categories/{categoryId}/edit"})
    public String updateCategory(@PathVariable Long categoryId,
                                 @ModelAttribute CategoryDto.Request request,
                                 @AuthenticationPrincipal User user,
                                 RedirectAttributes redirectAttributes) {
        try {
            categoryService.update(categoryId, request, user);
            redirectAttributes.addFlashAttribute("message", "글머리가 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping({"/admin/categories/{categoryId}/delete", "/admin/settings/categories/{categoryId}/delete"})
    public String deleteCategory(@PathVariable Long categoryId,
                                 @AuthenticationPrincipal User user,
                                 RedirectAttributes redirectAttributes) {
        try {
            categoryService.delete(categoryId, user);
            redirectAttributes.addFlashAttribute("message", "글머리가 삭제되었습니다. 연결된 글은 미분류로 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @GetMapping("/admin/users")
    public String users(@RequestParam(defaultValue = "all") String status,
                        @AuthenticationPrincipal User user,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (!requireAdmin(user, redirectAttributes)) {
            return "redirect:/login";
        }
        model.addAttribute("users", userService.getAllUsers(user).stream()
                .filter(member -> "all".equalsIgnoreCase(status) || status.equalsIgnoreCase(member.getStatus()))
                .toList());
        model.addAttribute("status", status);
        return "admin/users";
    }

    @PostMapping("/admin/users/{userId}")
    public String updateUser(@PathVariable Long userId,
                             @RequestParam String role,
                             @RequestParam String status,
                             @AuthenticationPrincipal User user,
                             RedirectAttributes redirectAttributes) {
        try {
            userService.updateAdminFields(userId, role, status, user);
            redirectAttributes.addFlashAttribute("message", "회원 정보가 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{userId}/approve")
    public String approveUser(@PathVariable Long userId,
                              @AuthenticationPrincipal User user,
                              RedirectAttributes redirectAttributes) {
        try {
            authorizationService.requireAdmin(user);
            userService.approveUser(userId);
            redirectAttributes.addFlashAttribute("message", "회원이 승인되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{userId}/reject")
    public String rejectUser(@PathVariable Long userId,
                             @AuthenticationPrincipal User user,
                             RedirectAttributes redirectAttributes) {
        try {
            authorizationService.requireAdmin(user);
            userService.rejectUser(userId);
            redirectAttributes.addFlashAttribute("message", "회원이 거절되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/admin/comments")
    public String comments(@RequestParam(required = false) Long postId,
                           @RequestParam(required = false) String author,
                           @RequestParam(required = false) Boolean deleted,
                           @RequestParam(required = false) String keyword,
                           @AuthenticationPrincipal User user,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (!requireAdmin(user, redirectAttributes)) {
            return "redirect:/login";
        }
        model.addAttribute("comments", commentService.getAllComments(user, postId, author, deleted, keyword));
        model.addAttribute("postId", postId);
        model.addAttribute("author", author);
        model.addAttribute("deleted", deleted);
        model.addAttribute("keyword", keyword);
        return "admin/comments";
    }

    @PostMapping("/admin/comments/{commentId}/delete")
    public String deleteComment(@PathVariable Long commentId,
                                @AuthenticationPrincipal User user,
                                RedirectAttributes redirectAttributes) {
        try {
            commentService.adminDelete(commentId, user);
            redirectAttributes.addFlashAttribute("message", "댓글이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/comments";
    }

    private boolean requireAdmin(User user, RedirectAttributes redirectAttributes) {
        try {
            authorizationService.requireAdmin(user);
            return true;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return false;
        }
    }
}
