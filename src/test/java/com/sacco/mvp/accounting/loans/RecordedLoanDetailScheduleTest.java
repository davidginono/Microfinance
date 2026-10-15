package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.controller.LoanRecordingController;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RecordedLoanDetailScheduleTest {
    private final LoanRecordingRepository records=mock(LoanRecordingRepository.class);
    private final LoanApplicationRepository loans=mock(LoanApplicationRepository.class);
    private final RepaymentScheduleService schedules=mock(RepaymentScheduleService.class);
    private final GeneralLedgerService accounting=mock(GeneralLedgerService.class);
    private final ApplicationClock clock=mock(ApplicationClock.class);
    private final AppUserPrincipal actor=mock(AppUserPrincipal.class);
    private final ObjectMapper json=new ObjectMapper();
    private final LoanRecordingService service=new LoanRecordingService(records,loans,null,null,schedules,null,
        accounting,null,json,clock,null,null,null,null,null,null,null,null);
    private final LocalDate today=LocalDate.of(2026,10,15);

    @Test void reviewDetailsShowStoredInstalmentAmountsWithoutInventingDatesOrPosting() throws Exception {
        var loan=fixture(false);
        var detail=service.detail(actor,loan.getId());
        assertThat(detail.loan().status()).isEqualTo("READY_FOR_MANAGER");
        assertThat(detail.firstPaymentDate()).isNull();
        assertThat(detail.schedule()).containsExactly(new ScheduleRow(1,null,money("1000.00"),money("17.03"),money("1017.03")));
        assertThat(detail.contractualInterest()).isEqualByComparingTo("17.03");
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.READY_FOR_MANAGER);
        assertThat(loan.getFirstRepaymentDate()).isNull();
        verify(loans,never()).save(any());
        verify(accounting).requireActor(actor,"LOAN_RECORDING_VIEW");
    }

    @Test void disbursedDetailsRetainContractualDueDatesAndAmounts() throws Exception {
        var loan=fixture(true);
        var detail=service.detail(actor,loan.getId());
        assertThat(detail.schedule()).containsExactly(new ScheduleRow(1,today.plusMonths(1),money("1000.00"),money("17.03"),money("1017.03")));
        assertThat(detail.firstPaymentDate()).isEqualTo(today.plusMonths(1));
        verify(loans,never()).save(any());
    }

    @Test void reviewPdfAndPrintIncludeTheEstimatedScheduleAndStatus() throws Exception {
        var loan=fixture(false);
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        var controller=new LoanRecordingController(service,new LoanRecordingPdfService(new ReportExportLimiter(1)),clock,messages,mock(SaccoLogoStorageService.class));
        var request=new MockHttpServletRequest("GET","/finance/loan-records/"+loan.getId()+"/pdf");
        var response=(ResponseEntity<?>)controller.loanExport(actor,loan.getId(),request,Locale.ENGLISH,new ExtendedModelMap());
        try(var pdf=Loader.loadPDF((byte[])response.getBody())){
            assertThat(new PDFTextStripper().getText(pdf).replaceAll("\\s+"," "))
                .contains("ON REVIEW BY MANAGER","Estimated Repayment Schedule","Due dates are set at disbursement.","1 | — | 1000.00 | 17.03 | 1017.03")
                .doesNotContain("null");
        }
        request.setRequestURI("/finance/loan-records/"+loan.getId()+"/print");
        var model=new ExtendedModelMap();
        assertThat(controller.loanExport(actor,loan.getId(),request,Locale.ENGLISH,model)).isEqualTo("accounting/loans/print");
        var document=(LoanRecordingPdfService.Document)model.get("document");
        assertThat(document.lines()).contains("Estimated Repayment Schedule (TZS)","Due dates are set at disbursement.","1 | — | 1000.00 | 17.03 | 1017.03");
    }

    private LoanApplication fixture(boolean disbursed) throws Exception {
        var id=UUID.randomUUID();var loan=new LoanApplication();loan.setId(id);loan.setAmount(money("1000.00"));loan.setTenorMonths(1);
        loan.setStatus(disbursed?LoanStatus.DISBURSED:LoanStatus.READY_FOR_MANAGER);loan.setFinancialSnapshot("stored contract");loan.setRepaymentFrequency(RepaymentFrequency.MONTHLY);
        if(disbursed){loan.setDisbursementDate(today);loan.setFirstRepaymentDate(today.plusMonths(1));}
        var form=new RecordForm();form.setMonths(1);form.setTenureValue(1);form.setLoanMode("CUSTOM");form.setInterestEntry("AMOUNT");
        when(clock.today()).thenReturn(today);
        when(loans.findById(id)).thenReturn(Optional.of(loan));
        when(records.record(actor,id,false)).thenReturn(Map.of("metadata",json.writeValueAsString(form),"sacco_name","Test institution","branch_name","Branch 1","actor_name","Accountant","recorded_at",today.atStartOfDay().atOffset(ZoneOffset.UTC)));
        when(records.loan(actor,id,today)).thenReturn(new LoanRow(id,"100102",UUID.randomUUID(),"Client Three","CL10003","Customised loan",today,money("1000.00"),money("1000.00"),loan.getStatus().name(),money("0.00"),money("0.00"),money("0.00"),money("0.00"),money("0.00"),money("0.00")));
        when(schedules.parseSummary("stored contract")).thenReturn(Map.of("calculationVersion",LoanAmortizationCalculator.VERSION,"interestRate","0","interestMethod","FLAT_RATE","repaymentFrequency","MONTHLY","interestAmount","17.03","tenureUnit","MONTHS","tenureValue",1));
        return loan;
    }
    private static BigDecimal money(String value){return new BigDecimal(value);}
}
