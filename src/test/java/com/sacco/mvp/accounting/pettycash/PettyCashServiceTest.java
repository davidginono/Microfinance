package com.sacco.mvp.accounting.pettycash;

import com.sacco.mvp.accounting.pettycash.service.PettyCashService;
import com.sacco.mvp.accounting.pettycash.repository.PettyCashRepository;
import com.sacco.mvp.accounting.pettycash.dto.PettyCashDtos.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PettyCashServiceTest {
    final PettyCashRepository repository=mock(PettyCashRepository.class);
    final GeneralLedgerService ledger=mock(GeneralLedgerService.class);
    final ApplicationClock clock=mock(ApplicationClock.class);
    final AppUserPrincipal actor=mock(AppUserPrincipal.class);
    final PettyCashService service=new PettyCashService(repository,ledger,clock,mock(AuditService.class));
    @Test void revokedAccessStopsBeforeReadingFinancialRecords(){
        doThrow(new AccessDeniedException("Revoked")).when(ledger).requireActor(actor,"ACCOUNTING_BUSINESS_VIEW");
        assertThatThrownBy(()->service.workspace(actor,null)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(repository);
    }
    @Test void unknownSetupDoesNotInventZeroBalances(){
        var day=LocalDate.of(2026,11,19);when(clock.today()).thenReturn(day);when(clock.now()).thenReturn(day.atStartOfDay().atOffset(ZoneOffset.ofHours(3)));
        when(repository.fund(actor)).thenReturn(Optional.empty());
        assertThat(service.workspace(actor,new Query(day,day,"expenses","","date_asc",0,10)).balances()).isNull();
    }
    @Test void validatesFiltersAndSeparatelyProtectsPendingPayments(){
        var day=LocalDate.of(2026,11,19);when(clock.today()).thenReturn(day);
        assertThatThrownBy(()->service.workspace(actor,new Query(day,day.plusDays(1),"expenses","","date_asc",0,10))).hasMessage("petty.error.filter");
        assertThatThrownBy(()->service.workspace(actor,new Query(day,day,"expenses","","date_asc; drop table gl_journal",0,10))).hasMessage("petty.error.filter");
        doThrow(new AccessDeniedException("Missing approval workspace claim")).when(ledger).requireActor(actor,"VOUCHER_WORKSPACE_VIEW");
        assertThatThrownBy(()->service.pending(actor,0,30,"number_desc","")).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(repository);
    }
}
