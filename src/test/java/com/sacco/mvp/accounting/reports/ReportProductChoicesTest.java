package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.management.service.ManagementReportService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportProductChoicesTest {
    final ManagementReportRepository repository=mock(ManagementReportRepository.class);
    final GeneralLedgerService guard=mock(GeneralLedgerService.class);
    final AppUserPrincipal actor=mock(AppUserPrincipal.class);
    final ManagementReportService service=new ManagementReportService(repository,guard,null,null);
    @Test void reportReadersUseTheScopedPagedProductLookup(){
        var choice=new Choice(UUID.randomUUID(),"","Education Loan");
        when(repository.products(actor,"Education",2,null)).thenReturn(List.of(choice));
        assertThat(service.choices(actor,Kind.DISBURSED_LOANS_AUDIT,"products","Education",2)).containsExactly(choice);
        verify(guard).requireActor(actor,"FINANCIAL_REPORTS_VIEW");
        verify(guard).requireActor(actor,"LOAN_REPORTS_VIEW");
        verifyNoMoreInteractions(guard);
    }
    @Test void invalidSearchAndNonLoanReportsCannotUseProductChoices(){
        assertThatThrownBy(()->service.choices(actor,Kind.CASHBOOK,"products","",0)).hasMessage("management.error.parameters");
        assertThatThrownBy(()->service.choices(actor,Kind.DISBURSED_LOANS_AUDIT,"products","",-1)).hasMessage("management.error.parameters");
        assertThatThrownBy(()->service.choices(actor,Kind.DISBURSED_LOANS_AUDIT,"products","x".repeat(101),0)).hasMessage("management.error.parameters");
        verifyNoInteractions(repository);
    }
    @Test void unknownOrOtherInstitutionSelectedProductIsRejected(){
        UUID id=UUID.randomUUID();when(repository.products(actor,"",0,id)).thenReturn(List.of());
        assertThatThrownBy(()->service.selectedProduct(actor,Kind.EXPECTED_REPAYMENTS,id)).hasMessage("management.error.product");
    }
}
