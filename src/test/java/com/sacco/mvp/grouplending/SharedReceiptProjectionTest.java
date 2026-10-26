package com.sacco.mvp.grouplending;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.grouplending.dto.GroupSharedLoanDtos.Balance;
import com.sacco.mvp.grouplending.repository.GroupSharedLoanRepository;
import com.sacco.mvp.grouplending.service.GroupSharedLoanService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SharedReceiptProjectionTest {
    @Test void fiftyLoansAndFiftyObligorsUseOneBatchAndRetainEveryCashShare(){
        var repo=mock(GroupSharedLoanRepository.class);var actor=mock(AppUserPrincipal.class);var service=new GroupSharedLoanService(repo,mock(GeneralLedgerService.class),mock(MemberDirectoryService.class),mock(ApplicationClock.class),mock(AuditService.class));
        var rows=new ArrayList<LoanReceiptRow>();var balances=new LinkedHashMap<UUID,List<Balance>>();var posted=new LinkedHashMap<UUID,List<SharedReceiptBenefit>>();var ids=new LinkedHashSet<UUID>();
        for(int l=0;l<50;l++){UUID loan=UUID.randomUUID(),posting=UUID.randomUUID();ids.add(loan);rows.add(new LoanReceiptRow(l+1,posting,loan,"L"+l,"Synthetic cash",new BigDecimal("1.00"),new BigDecimal("1.00"),new BigDecimal("0.00"),new BigDecimal("49.00"),"Cash","Principal","Interest","REF"));var people=new ArrayList<Balance>();var evidence=new ArrayList<SharedReceiptBenefit>();
            for(int m=0;m<50;m++){UUID client=UUID.randomUUID();people.add(new Balance(client,"C"+m,"Synthetic borrower",new BigDecimal("1.00"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,false));evidence.add(new SharedReceiptBenefit(client,"C"+m,"Synthetic borrower","Payer",new BigDecimal("0.02"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));}balances.put(loan,people);posted.put(posting,evidence);}
        when(repo.sharedLoans(eq(actor),anyList())).thenReturn(ids);when(repo.balances(eq(actor),anyList())).thenReturn(balances);when(repo.receiptBenefits(eq(actor),anyList())).thenReturn(posted);
        var preview=service.receiptRows(actor,rows,true,"Payer");var receipt=service.receiptRows(actor,rows,false,"");assertThat(preview).hasSize(50);assertThat(receipt).hasSize(50);
        for(var projection:List.of(preview,receipt)){assertThat(projection.stream().mapToInt(r->r.sharedBenefits().size()).sum()).isEqualTo(2500);assertThat(projection.stream().flatMap(r->r.sharedBenefits().stream()).map(SharedReceiptBenefit::amount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("50.00");}
        verify(repo,times(1)).balances(eq(actor),anyList());verify(repo,times(1)).receiptBenefits(eq(actor),anyList());
    }
    @Test void missingPostedAllocationEvidenceFailsInsteadOfReturningCleanCash(){
        var repo=mock(GroupSharedLoanRepository.class);var actor=mock(AppUserPrincipal.class);var service=new GroupSharedLoanService(repo,mock(GeneralLedgerService.class),mock(MemberDirectoryService.class),mock(ApplicationClock.class),mock(AuditService.class));UUID loan=UUID.randomUUID();
        var row=new LoanReceiptRow(1,UUID.randomUUID(),loan,"L1","Synthetic cash",BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,"Cash","Principal","Interest","REF");when(repo.sharedLoans(eq(actor),anyList())).thenReturn(Set.of(loan));when(repo.receiptBenefits(eq(actor),anyList())).thenReturn(Map.of());assertThatThrownBy(()->service.receiptRows(actor,List.of(row),false,"")).hasMessage("recording.error.sharedAttribution");
    }
}
