package com.sacco.mvp.grouplending;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.grouplending.dto.GroupMemberStatementDtos.*;
import com.sacco.mvp.grouplending.dto.GroupSharedLoanDtos.Balance;
import com.sacco.mvp.grouplending.repository.GroupMemberStatementRepository;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupMemberStatementBoundsTest {
    final LocalDate today=LocalDate.of(2026,10,8);final UUID cycle=UUID.randomUUID(),client=UUID.randomUUID();
    @Test void exportsRejectTheSentinelInsteadOfSilentlyTruncating(){
        var repo=mock(GroupMemberStatementRepository.class);var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(today);var actor=mock(AppUserPrincipal.class);when(actor.getMemberId()).thenReturn(client);
        var service=new GroupMemberStatementService(repo,mock(GroupConsentService.class),mock(GeneralLedgerService.class),clock,mock(GroupCoverageService.class));
        when(repo.credits(actor,cycle,client,null,today,5001,0)).thenReturn(Collections.nCopies(5001,mock(Credit.class)));when(repo.schedule(actor,cycle,client,601,0)).thenReturn(List.of());assertThatThrownBy(()->service.client(actor,cycle,null,null,0,0,true)).hasMessage("group.error.statementRange");
        when(repo.credits(actor,cycle,client,null,today,5001,0)).thenReturn(List.of());when(repo.schedule(actor,cycle,client,601,0)).thenReturn(Collections.nCopies(601,mock(Due.class)));assertThatThrownBy(()->service.client(actor,cycle,null,null,0,0,true)).hasMessage("group.error.statementRange");
        assertThatThrownBy(()->service.client(actor,cycle,today.plusDays(1),today,0,0,false)).hasMessage("group.error.query");assertThatThrownBy(()->service.client(actor,cycle,null,today.plusDays(1),0,0,false)).hasMessage("group.error.query");
    }
    @Test void originalScheduleHasItsOwnPageAndFullPdfRetainsLastEntryInBothLanguages()throws Exception{
        var zero=new BigDecimal("0.00");var share=new Balance(client,"CL-OWN","Synthetic participant",new BigDecimal("600.00"),zero,zero,zero,zero,zero,zero,false);
        var source=new Source(cycle,UUID.randomUUID(),"LN-OWN","Synthetic institution","Branch","G-OWN","Synthetic group","DISBURSED","PROPORTIONAL",share.principal(),zero,share.principal(),share.principal(),share,zero,zero,zero,zero,true,true);
        var schedule=IntStream.rangeClosed(1,600).mapToObj(n->new Due(n,today.plusDays(n),BigDecimal.ONE,zero)).toList();var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");var pdf=new GroupMemberStatementPdf(new LoanRecordingPdfService(new ReportExportLimiter(1)),messages);
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){var data=new Statement(source,today,today.atStartOfDay().atOffset(ZoneOffset.UTC),null,today,schedule,List.of(),0,false,0,false,false,false);try(var document=Loader.loadPDF(pdf.render(data,locale))){assertThat(document.getNumberOfPages()).isGreaterThan(10);assertThat(new PDFTextStripper().getText(document)).contains("600",today.plusDays(600).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")));}}
        var repo=mock(GroupMemberStatementRepository.class);var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(today);var actor=mock(AppUserPrincipal.class);when(actor.getMemberId()).thenReturn(client);when(repo.source(actor,cycle,client,today)).thenReturn(source);when(repo.schedule(actor,cycle,client,26,25)).thenReturn(schedule.subList(25,51));when(repo.credits(actor,cycle,client,null,today,26,0)).thenReturn(List.of());var service=new GroupMemberStatementService(repo,mock(GroupConsentService.class),mock(GeneralLedgerService.class),clock,mock(GroupCoverageService.class));var page=service.client(actor,cycle,null,null,0,1,false);assertThat(page.schedule()).hasSize(25);assertThat(page.schedule().getFirst().number()).isEqualTo(26);assertThat(page.scheduleNext()).isTrue();
    }
}
