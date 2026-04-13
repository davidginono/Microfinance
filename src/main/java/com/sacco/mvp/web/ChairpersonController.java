package com.sacco.mvp.web;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/chairperson")
@PreAuthorize("hasRole('CHAIRPERSON') and @userClaims.has(principal, 'VIEW_CHAIRPERSON_PANEL')")
public class ChairpersonController {
    private final ManagerReviewRepository managerReviewRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final MemberRepository memberRepository;

    @GetMapping("/manager-decisions")
    public String managerDecisions(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<ManagerReview> reviews = managerReviewRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(review -> loanApplicationRepository.findById(review.getLoanApplicationId())
                .map(app -> app.getSaccoId().equals(principal.getSaccoId()))
                .orElse(false))
            .toList();

        Map<UUID, LoanApplication> applications = new LinkedHashMap<>();
        for (ManagerReview review : reviews) {
            loanApplicationRepository.findById(review.getLoanApplicationId())
                .ifPresent(app -> applications.put(app.getId(), app));
        }

        Set<UUID> applicantIds = applications.values().stream().map(LoanApplication::getApplicantMemberId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, String> applicantNames = new LinkedHashMap<>();
        for (Member member : memberRepository.findAllById(applicantIds)) {
            applicantNames.put(member.getId(), member.getFullName());
        }

        model.addAttribute("reviews", reviews);
        model.addAttribute("applications", applications);
        model.addAttribute("applicantNames", applicantNames);
        return "chairperson/manager-decisions";
    }
}
