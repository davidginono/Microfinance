package com.sacco.mvp.web;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.MemberProfileImageService;
import com.sacco.mvp.service.ProfileContactService;
import com.sacco.mvp.service.StaffSignatureService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
    private final ProfileContactService profileContactService;
    private final StaffSignatureService staffSignatureService;
    private final MessageSource messageSource;
    private final com.sacco.mvp.service.EmployeeProfileService employeeProfileService;

    @PostMapping("/profile/employee-number")
    public String updateEmployeeNumber(@AuthenticationPrincipal AppUserPrincipal principal,
                                      @RequestParam(defaultValue = "") String employeeNumber,
                                      RedirectAttributes redirectAttributes) {
        try {
            employeeProfileService.update(principal, employeeNumber);
            redirectAttributes.addFlashAttribute("message", messageSource.getMessage("payroll.employeeNumber.saved", null, LocaleContextHolder.getLocale()));
        } catch (IllegalArgumentException failure) {
            redirectAttributes.addFlashAttribute("employeeNumberValue", employeeNumber.substring(0, Math.min(employeeNumber.length(), 50)));
            redirectAttributes.addFlashAttribute("employeeNumberError", messageSource.getMessage("payroll.employeeNumber.invalid", null, LocaleContextHolder.getLocale()));
        }
        return "redirect:/profile";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        Member member = memberDirectoryService.find(principal.getMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Member account not found"));
        model.addAttribute("profileMember", member);
        model.addAttribute("hasProfileImage", memberProfileImageService.hasImage(principal.getMemberId()));
        model.addAttribute("staffSignatureAvailable", staffSignatureService.canRegister(principal, member));
        if (!model.containsAttribute("signatureFullName")) model.addAttribute("signatureFullName", member.getFullName());
        return "profile";
    }

    @PostMapping("/profile/signature")
    public String registerStaffSignature(@AuthenticationPrincipal AppUserPrincipal principal,
                                         @RequestParam String fullName,
                                         @RequestParam String currentPassword,
                                         @RequestParam(defaultValue = "false") boolean confirmed,
                                         RedirectAttributes redirectAttributes) {
        try {
            staffSignatureService.register(principal, fullName, currentPassword, confirmed);
            redirectAttributes.addFlashAttribute("message", messageSource.getMessage("profile.signature.saved", null, LocaleContextHolder.getLocale()));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("signatureFullName", fullName.substring(0,Math.min(fullName.length(),120)));
            redirectAttributes.addFlashAttribute("signatureError", messageSource.getMessage(ex.getMessage(), null, LocaleContextHolder.getLocale()));
        }
        return "redirect:/profile";
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
            profileContactService.update(principal, email, phone, currentPassword);
            redirectAttributes.addFlashAttribute("message", messageSource.getMessage("profile.contact.saved", null, LocaleContextHolder.getLocale()));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("contactEmailValue", email.substring(0, Math.min(email.length(),160)));
            redirectAttributes.addFlashAttribute("contactPhoneValue", phone==null?"":phone.substring(0, Math.min(phone.length(),32)));
            redirectAttributes.addFlashAttribute("contactError", messageSource.getMessage(ex.getMessage(), null, LocaleContextHolder.getLocale()));
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("contactError", messageSource.getMessage("profile.contact.duplicate", null, LocaleContextHolder.getLocale()));
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/phone/request")
    public String requestPhoneVerification(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String currentPassword,
            jakarta.servlet.http.HttpSession session,RedirectAttributes redirect) {
        try {
            boolean sent=profileContactService.requestPhoneOtp(actor,currentPassword);
            if(sent)session.removeAttribute("profilePhoneAttempts:"+actor.getMemberId());
            redirect.addFlashAttribute("message",messageSource.getMessage(sent?"profile.contact.sent":"profile.contact.verified",null,LocaleContextHolder.getLocale()));
        } catch(IllegalArgumentException ex) {redirect.addFlashAttribute("phoneError",messageSource.getMessage(ex.getMessage(),null,LocaleContextHolder.getLocale()));}
        return "redirect:/profile";
    }

    @PostMapping("/profile/phone/verify")
    public String verifyPhone(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String code,@RequestParam String currentPassword,
            jakarta.servlet.http.HttpSession session,RedirectAttributes redirect) {
        String key="profilePhoneAttempts:"+actor.getMemberId();
        try {
            synchronized(session) {
                int attempts=session.getAttribute(key) instanceof Integer n?n:0;
                if(attempts>=5)throw new IllegalArgumentException("profile.contact.attempts");
                session.setAttribute(key,attempts+1);
            }
            profileContactService.verifyPhone(actor,code,currentPassword);session.removeAttribute(key);
            redirect.addFlashAttribute("message",messageSource.getMessage("profile.contact.verified",null,LocaleContextHolder.getLocale()));
        } catch(IllegalArgumentException ex) {redirect.addFlashAttribute("phoneError",messageSource.getMessage(ex.getMessage(),null,LocaleContextHolder.getLocale()));}
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
