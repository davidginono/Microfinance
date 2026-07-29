package com.sacco.mvp.web;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

final class StaffQueueViewSupport {
    private StaffQueueViewSupport() {
    }

    static String normalizeSearch(String searchId) {
        return searchId == null ? "" : searchId.trim();
    }

    static Map<UUID, LoanApplication> loadLoansById(LoanApplicationRepository loanApplicationRepository,
                                                    List<UUID> loanIds) {
        return loanIds.isEmpty()
            ? Collections.emptyMap()
            : loanApplicationRepository.findAllById(loanIds).stream()
                .collect(Collectors.toMap(LoanApplication::getId, loan -> loan, (left, right) -> left, LinkedHashMap::new));
    }

    static Map<UUID, String> loadApplicantNames(MemberRepository memberRepository, List<UUID> applicantIds) {
        return applicantIds.isEmpty()
            ? Collections.emptyMap()
            : memberRepository.findAllById(applicantIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getFullName, (left, right) -> left, LinkedHashMap::new));
    }
}
