package com.sacco.mvp.web;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.MemberProfileImageService;
import com.sacco.mvp.service.PlatformAdminProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ProfileController {
    private final MemberDirectoryService memberDirectoryService;
    private final MemberProfileImageService memberProfileImageService;
    private final PlatformAdminProfileService platformAdminProfileService;

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        Member member = memberDirectoryService.find(principal.getMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Member account not found"));
        model.addAttribute("profileMember", member);
        model.addAttribute("hasProfileImage", memberProfileImageService.hasImage(principal.getMemberId()));
        return "profile";
    }

    @PostMapping("/profile/image")
    public String updateProfileImage(@AuthenticationPrincipal AppUserPrincipal principal,
                                     @RequestParam("profileImage") MultipartFile profileImage,
                                     RedirectAttributes redirectAttributes) {
        try {
            memberProfileImageService.store(principal.getMemberId(), profileImage);
            redirectAttributes.addFlashAttribute("message", "Profile image updated.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/image/delete")
    public String deleteProfileImage(@AuthenticationPrincipal AppUserPrincipal principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            memberProfileImageService.delete(principal.getMemberId());
            redirectAttributes.addFlashAttribute("message", "Profile image removed.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/contact")
    public String updateContact(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam String email,
                                @RequestParam(required = false) String phone,
                                @RequestParam String currentPassword,
                                RedirectAttributes redirectAttributes) {
        try {
            if (principal == null || !principal.isPlatformIdentity()) {
                throw new IllegalStateException("Only the System Admin can update this contact profile.");
            }
            platformAdminProfileService.updateContact(principal.getMemberId(), email, phone, currentPassword);
            redirectAttributes.addFlashAttribute("message", "Contact details updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/profile";
    }

    @GetMapping("/profile/image/me")
    public ResponseEntity<byte[]> myProfileImage(@AuthenticationPrincipal AppUserPrincipal principal) {
        return profileImage(principal.getMemberId(), principal);
    }

    @GetMapping("/profile/image/members/{memberId}")
    public ResponseEntity<byte[]> profileImage(@PathVariable UUID memberId,
                                               @AuthenticationPrincipal AppUserPrincipal principal) {
        if (!canViewProfileImage(memberId, principal)) {
            return ResponseEntity.notFound().build();
        }
        try {
            MemberProfileImageService.ProfileImageResource resource = memberProfileImageService.load(memberId);
            return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(resource.contentType())
                .body(resource.content());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    private boolean canViewProfileImage(UUID memberId, AppUserPrincipal principal) {
        if (principal == null || memberId == null) {
            return false;
        }
        if (memberId.equals(principal.getMemberId())) {
            return true;
        }
        if (!isStaffOrAdmin(principal)) {
            return false;
        }
        return memberDirectoryService.find(memberId)
            .map(member -> member.getSaccoId() != null && member.getSaccoId().equals(principal.getSaccoId()))
            .orElse(false);
    }

    private boolean isStaffOrAdmin(AppUserPrincipal principal) {
        return principal.isPlatformIdentity()
            || principal.isWorkspaceAdminScope()
            || !principal.getStaffRoles().isEmpty();
    }
}
