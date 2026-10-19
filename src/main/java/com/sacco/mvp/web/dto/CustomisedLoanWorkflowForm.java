package com.sacco.mvp.web.dto;

import com.sacco.mvp.domain.LoanProductSetting;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.UUID;

@Getter @Setter
public class CustomisedLoanWorkflowForm {
    private UUID principalGlAccountId;
    private UUID interestGlAccountId;
    private boolean managerReviewRequired;
    private int managerPriority=1;
    private boolean loanOfficerReviewRequired;
    private int loanOfficerPriority=2;
    private boolean chairpersonReviewRequired;
    private int chairpersonPriority=3;
    private boolean boardReviewRequired;
    private int boardPriority=4;
    private List<UUID> boardReviewerIds=List.of();
    private boolean committeeReviewRequired;
    private int committeePriority=5;
    private List<UUID> creditCommitteeReviewerIds=List.of();
    private boolean accountantReviewRequired=true;
    private int accountantPriority=6;
    private List<UUID> disbursementOfficerIds=List.of();
    private boolean disbursementProofRequired=true;

    public static CustomisedLoanWorkflowForm from(LoanProductSetting product) {
        var f=new CustomisedLoanWorkflowForm();if(product==null)return f;
        f.managerReviewRequired=product.isManagerReviewRequired();f.managerPriority=product.getResolvedManagerPriority();
        f.loanOfficerReviewRequired=Boolean.TRUE.equals(product.getLoanOfficerReviewRequired());f.loanOfficerPriority=product.getResolvedLoanOfficerPriority();
        f.chairpersonReviewRequired=product.isChairpersonReviewRequired();f.chairpersonPriority=product.getResolvedChairpersonPriority();
        f.boardReviewRequired=product.isBoardReviewRequired();f.boardPriority=product.getResolvedBoardPriority();
        f.committeeReviewRequired=product.isCommitteeReviewRequired();f.committeePriority=product.getResolvedCommitteePriority();
        f.accountantReviewRequired=product.isAccountantReviewRequired();f.accountantPriority=product.getResolvedAccountantPriority();
        f.disbursementProofRequired=product.isDisbursementProofRequired();f.principalGlAccountId=product.getPrincipalGlAccountId();f.interestGlAccountId=product.getInterestGlAccountId();return f;
    }
}
