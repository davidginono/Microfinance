package com.sacco.mvp.web;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.security.WorkspaceLanding;
import com.sacco.mvp.service.StaffAccessService;
import com.sacco.mvp.service.UserClaimService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class StaffAccessController {
    private final StaffAccessService staffAccessService;
    private final UserClaimService userClaimService;

    @PostMapping("/app/staff-access/acknowledge")
    @PreAuthorize("hasRole('MEMBER')")
    public String acknowledge(@AuthenticationPrincipal AppUserPrincipal principal,
                              HttpServletRequest request,
                              RedirectAttributes ra) {
        try {
            Member member = staffAccessService.acknowledgeStaffAccess(principal.getMemberId());
            AppUserPrincipal staffPrincipal = new AppUserPrincipal(
                member,
                userClaimService.effectiveClaims(member.getId(), member.getActiveStaffRolesResolved(), member.isMemberAccess()),
                true
            );
            installSecurityContext(staffPrincipal, request);
            ra.addFlashAttribute("message", "Staff access activated. Staff Number " + member.getStaffNo() + " is now active.");
            return "redirect:" + WorkspaceLanding.staffDashboard(member);
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/app/dashboard";
        }
    }

    private void installSecurityContext(AppUserPrincipal principal, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true)
            .setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
