package com.sacco.mvp.service;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.domain.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class LoanApprovalProgressTest {
    final UUID loan=UUID.randomUUID(),actor=UUID.randomUUID();
    final ResourceBundleMessageSource messages=new ResourceBundleMessageSource();
    final LoanPresentationService presentation;
    LoanApprovalProgressTest(){
        messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");LocaleContextHolder.setLocale(Locale.ENGLISH);
        presentation=new LoanPresentationService(new ObjectMapper(),null,null,null,null,null,null,null,messages,null);
    }
    @AfterEach void resetLocale(){LocaleContextHolder.resetLocaleContext();}
    GroupDecision decision(int step,UUID person,String result){return new GroupDecision(UUID.randomUUID(),step,person,"Staff",result,"Reviewed","",OffsetDateTime.now());}
    GroupReview round(List<GroupStep> plan,GroupDecision... decisions){return new GroupReview(
        new GroupRequest(UUID.randomUUID(),loan,UUID.randomUUID(),actor,2,"frozen","REJECTED"),plan,List.of(decisions),0,false,false,false);}
    List<Map<String,Object>> progress(LoanStatus status,GroupReview review){return presentation.buildProgressItems(
        LoanApplication.builder().id(loan).status(status).financialSnapshot("{\"recordingSource\":\"ACCOUNTANT_DIRECT\"}").build(),review);}

    @Test void completedLoanOfficerReviewRemainsVisibleAtDisbursement(){
        var items=progress(LoanStatus.READY_FOR_DISBURSEMENT,round(List.of(new GroupStep(Position.LOAN_OFFICER,1)),decision(0,actor,"APPROVE")));
        assertThat(items).extracting(i->i.get("state")).containsExactly("completed","completed","current");
        assertThat(items.get(1)).containsEntry("label","Loan Officer").containsEntry("approved",1L).containsEntry("required",1);
        assertThat(items.getLast()).containsEntry("current",true);
    }
    @Test void partialApprovalsCountDifferentPeopleAndKeepLaterGroupsPending(){
        var vote=decision(0,actor,"APPROVE");
        var items=progress(LoanStatus.SUBMITTED,round(List.of(new GroupStep(Position.LOAN_OFFICER,2),new GroupStep(Position.MANAGER,1)),vote,vote));
        assertThat(items).extracting(i->i.get("state")).containsExactly("completed","current","pending","pending");
        assertThat(items.get(1)).containsEntry("approved",1L).containsEntry("required",2);
    }
    @Test void rejectionClosesLaterStepsAndResubmissionStartsWithFreshCounts(){
        var plan=List.of(new GroupStep(Position.LOAN_OFFICER,1),new GroupStep(Position.MANAGER,1));
        var oldRound=round(plan,decision(0,actor,"REJECT"));
        assertThat(progress(LoanStatus.REJECTED,oldRound)).extracting(i->i.get("state")).containsExactly("completed","rejected","closed","closed");
        var newRound=round(plan);
        assertThat(progress(LoanStatus.SUBMITTED,newRound)).extracting(i->i.get("state")).containsExactly("completed","current","pending","pending");
        assertThat(progress(LoanStatus.SUBMITTED,newRound).get(1)).containsEntry("approved",0L);
    }
    @Test void statusAloneDoesNotManufactureReviewCompletionOrIntermediateArrears(){
        var review=round(List.of(new GroupStep(Position.LOAN_OFFICER,1)));
        assertThat(progress(LoanStatus.READY_FOR_DISBURSEMENT,review).get(1)).containsEntry("state","pending");
        var funded=progress(LoanStatus.DISBURSED,round(review.steps(),decision(0,actor,"APPROVE")));
        assertThat(funded).extracting(i->i.get("state")).containsExactly("completed","completed","completed","current");
        assertThat(funded).hasSize(4);
        assertThat(progress(LoanStatus.DEFAULTED,review)).hasSize(4);
    }
    @Test void withdrawnLoansKeepOnlyActualApprovalsCompleted(){
        var items=progress(LoanStatus.WITHDRAWN,round(List.of(new GroupStep(Position.LOAN_OFFICER,1),new GroupStep(Position.MANAGER,1)),decision(0,actor,"APPROVE")));
        assertThat(items).extracting(i->i.get("state")).containsExactly("completed","completed","closed","closed","closed");
    }
    @Test void frozenGroupsKeepTheirOrderAndHistoricalAliasesUseCurrentLocalizedNames(){
        var review=round(List.of(new GroupStep(Position.BOARD,1),new GroupStep(Position.LOAN_OFFICER,1)));
        LocaleContextHolder.setLocale(Locale.forLanguageTag("sw"));
        var items=progress(LoanStatus.SUBMITTED,review);
        assertThat(items.get(1).get("label")).isEqualTo(messages.getMessage("userGroup.CREDIT_OFFICER",null,Locale.forLanguageTag("sw")));
        assertThat(items.get(2).get("label")).isEqualTo(messages.getMessage("userGroup.LOAN_OFFICER",null,Locale.forLanguageTag("sw")));
        assertThat(review.steps().getFirst().group()).isEqualTo(Position.BOARD);
    }
}
