package com.sacco.mvp.service;

import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanRepaymentReceiptPdfServiceTest {
    private final LoanRepaymentLedgerService ledger=mock(LoanRepaymentLedgerService.class);
    private final LoanApplicationRepository loans=mock(LoanApplicationRepository.class);
    private final MemberRepository members=mock(MemberRepository.class);
    private final RegisteredSaccoRepository institutions=mock(RegisteredSaccoRepository.class);
    private final SaccoStationRepository branches=mock(SaccoStationRepository.class);
    private final SaccoLogoStorageService logos=mock(SaccoLogoStorageService.class);
    private final UUID loanId=UUID.randomUUID(),receiptId=UUID.randomUUID(),clientId=UUID.randomUUID();
    private LoanRepaymentReceiptPdfService renderer(){
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        return new LoanRepaymentReceiptPdfService(ledger,loans,members,institutions,branches,logos,new LoanRecordingPdfService(new ReportExportLimiter(1)),messages);
    }
    private AppUserPrincipal actor(){var actor=mock(AppUserPrincipal.class);when(actor.getSaccoId()).thenReturn("I1");when(actor.getMemberId()).thenReturn(clientId);return actor;}
    private void receipt(){when(ledger.receipt(eq(loanId),eq(receiptId),any())).thenReturn(new LoanRepaymentLedgerService.Receipt(receiptId,loanId,"100001","R-2026-01","PAYMENT",LocalDate.of(2026,10,12),OffsetDateTime.parse("2026-10-12T12:30:00+03:00"),new BigDecimal("1100.35"),new BigDecimal("1000.25"),new BigDecimal("100.10"),"CASH","CASH-001",null,null,null));}
    @Test void borrowerReceiptPreservesAmountsChannelAndIdentityInBothLanguages()throws Exception {
        receipt();var actor=actor();
        var loan=new LoanApplication();loan.setSaccoId("I1");loan.setStationId("B1");loan.setApplicantMemberId(clientId);when(loans.findById(loanId)).thenReturn(Optional.of(loan));
        when(members.findById(clientId)).thenReturn(Optional.of(Member.builder().id(clientId).saccoId("I1").fullName("Synthetic borrower").memberNo("C001").build()));
        when(institutions.findById("I1")).thenReturn(Optional.of(RegisteredSacco.builder().saccoName("Example Microfinance Limited").build()));
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){
            byte[] bytes=renderer().render(loanId,receiptId,actor,locale);
            try(var doc=Loader.loadPDF(bytes)){
                assertThat(doc.getNumberOfPages()).isEqualTo(1);String text=new PDFTextStripper().getText(doc);
                assertThat(text).contains("Synthetic borrower","C001","100001","CASH-001","1,000.25","100.10","1,100.35").doesNotContain("APPROVALS","APPROVED BY");
                if(locale.equals(Locale.ENGLISH)){Files.write(Path.of("target/ledger-receipt-format.pdf"),bytes);javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,120),"png",Path.of("target/ledger-receipt-format.png").toFile());}
            }
        }
    }
    @Test void deniedReceiptDoesNotReadOtherRecordsOrBranding(){
        var actor=actor();when(ledger.receipt(loanId,receiptId,actor)).thenThrow(new AccessDeniedException("Outside scope"));
        assertThatThrownBy(()->renderer().render(loanId,receiptId,actor,Locale.ENGLISH)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(loans,members,institutions,branches,logos);
    }
    @Test void mismatchedLoanInstitutionFailsClosed(){
        receipt();var loan=new LoanApplication();loan.setSaccoId("FOREIGN");loan.setApplicantMemberId(clientId);when(loans.findById(loanId)).thenReturn(Optional.of(loan));
        assertThatThrownBy(()->renderer().render(loanId,receiptId,actor(),Locale.ENGLISH)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(members,institutions,branches,logos);
    }
}
