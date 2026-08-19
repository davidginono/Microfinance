package com.sacco.mvp.web;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.MemberDirectoryService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class StaffQueueViewSupport {
    private StaffQueueViewSupport() {
    }

    static String normalizeSearch(String searchId) {
        return searchId == null ? "" : searchId.trim();
    }

    static Map<UUID, LoanApplication> loadLoansById(LoanPresentationService loanPresentationService,
                                                    List<UUID> loanIds) {
        return loanPresentationService.loansById(loanIds);
    }

    static Map<UUID, String> loadApplicantNames(MemberDirectoryService memberDirectoryService, List<UUID> applicantIds) {
        return memberDirectoryService.fullNames(applicantIds);
    }
}
