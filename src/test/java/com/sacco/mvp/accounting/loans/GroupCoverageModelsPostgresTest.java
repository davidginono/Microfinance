package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupCoverageDtos.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.SharedLiability;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupCoverageModelsPostgresTest {
    final GroupSharedLoanPostgresTest w=new GroupSharedLoanPostgresTest();
    final GroupCyclePostgresTest g=w.s.s.g;final LoanRecordingPostgresTest f=w.f;
    GroupCoverageService coverage;UUID cycle;LocalDate day;
    @BeforeAll void start(){w.start();coverage=GroupCoveragePostgresTest.register(f);}
    @AfterAll void stop(){w.stop();}
    void advance(LocalDate date){day=date;when(f.context.getBean(ApplicationClock.class).today()).thenReturn(date);when(f.context.getBean(ApplicationClock.class).now()).thenReturn(date.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));}
    void shared(SharedLiability rule){w.s.s.firstPaymentDate=LoanRecordingPostgresTest.TODAY.plusDays(1);w.s.s.setup();w.s.s.rule(rule);f.context.getBean(SaccoRegistryService.class).findActiveSacco(f.institution).orElseThrow().setSaccoName("Synthetic loan institution");var terms=w.s.s.form();terms.setTenureValue(3);cycle=w.s.cycle=g.cycles.create(f.actor,g.group,terms);g.cycles.submit(f.actor,cycle,terms);w.s.client=g.client(f.client);for(var person:List.of(f.client,g.second)){GroupPaperFixture.record(f,cycle,"TERMS",person);f.jdbc.update("insert into member_payment_details(member_id,destination_type,provider,account_holder_name,account_identifier,created_at,updated_at) values(?,'BANK_ACCOUNT','Synthetic bank','Synthetic account holder',?,now(),now())",person,"QA-"+person);}w.loan=g.cycles.sharedFacility(f.actor,cycle).orElseThrow().loanId();w.s.open();GroupPaperFixture.record(f,cycle,"SHARED_PAYOUT",f.client);GroupPaperFixture.record(f,cycle,"SHARED_PAYOUT",g.second);f.context.getBean(MemberDirectoryService.class).find(f.actor.getMemberId()).orElseThrow().setSignatureText("Synthetic lender signature");f.jdbc.update("update members set signature_text='Synthetic lender signature' where id=?",f.actor.getMemberId());f.service.disburse(f.actor,w.funding());advance(w.s.s.firstPaymentDate.plusDays(16));}
    UUID call(Source source){var policy=new PolicyForm();policy.setNoticeDays(14);policy.setCaseDays(30);policy.setPromiseDays(7);policy.setEnabled(true);policy.setVerified(true);policy.setReference("Synthetic approved collection policy");var id=coverage.configure(f.actor,cycle,policy);var call=new CallForm();call.setSourceId(source.id());call.setSourceKind(source.kind());call.setPolicyId(id);call.setAmount(new BigDecimal("20.00"));call.setBorrowerNoticeDate(day.minusDays(15));call.setPersonNoticeDate(day.minusDays(15));call.setNoticesVerified(true);call.setNoticeLanguage("BOTH");call.setBorrowerNoticeEvidence("Synthetic borrower service record");call.setPersonNoticeEvidence("Synthetic named person service record");return coverage.call(f.actor,cycle,call,GroupOrganizationPostgresTest.documentFile());}
    Detail detail(UUID id){return coverage.detail(f.actor,id,0,0);}
    @Test void proportionalSharedAllocationsStayVoluntaryAndWholeCashStillPostsOnlyOnce(){shared(SharedLiability.PROPORTIONAL);var source=coverage.sources(f.actor,cycle,0).rows().stream().filter(s->s.client().equals(f.client)).findFirst().orElseThrow();assertThat(source.kind()).isEqualTo("VOLUNTARY");UUID id=call(source);assertThat(detail(id).header().state()).isEqualTo("REQUESTED");var form=w.payment("30.00");form.setEffectiveDate(day);UUID payment=f.service.repay(f.actor,form).id();var link=new LinkForm();link.setPostingId(payment);link.setPayerVerified(true);link.setPayerEvidence("Synthetic verified signed shared payer");long journals=f.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Long.class,f.institution);coverage.link(f.actor,id,link);assertThat(detail(id).header().received()).isEqualByComparingTo("30.00");assertThat(detail(id).header().requested()).isEqualByComparingTo("20.00");assertThat(f.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Long.class,f.institution)).isEqualTo(journals);assertThat(f.jdbc.queryForObject("select count(*) from loan_ledgers where sacco_id=?",Long.class,f.institution)).isEqualTo(1);}
    @Test void jointLiabilityKeepsTheSignedWholeLoanCapAndOldCuredCasesNeverRevive(){shared(SharedLiability.JOINT_AND_SEVERAL);var source=coverage.sources(f.actor,cycle,0).rows().getFirst();assertThat(source.kind()).isEqualTo("JOINT");assertThat(source.cap()).isGreaterThan(new BigDecimal("1000.00"));UUID id=call(source);var repayment=w.payment("400.00");repayment.setEffectiveDate(day);f.service.repay(f.actor,repayment);assertThat(detail(id).header().state()).isEqualTo("CURED_ELSEWHERE");advance(day.plusMonths(1));assertThat(f.jdbc.queryForObject("select lending_coverage_due(?,?::date)",BigDecimal.class,w.loan,day)).isPositive();assertThat(detail(id).header().remainingDue()).isZero();assertThat(detail(id).header().unpaid()).isZero();assertThat(detail(id).header().received()).isZero();}
    @Test void memberStatementShowsOnlyTheNamedPersonsCoverageAndNeverOtherParticipantsCases()throws Exception{
        shared(SharedLiability.PROPORTIONAL);var source=coverage.sources(f.actor,cycle,0).rows().stream().filter(x->x.client().equals(f.client)).findFirst().orElseThrow();UUID id=call(source);
        f.context.registerBean(com.sacco.mvp.grouplending.repository.GroupMemberStatementRepository.class,()->new com.sacco.mvp.grouplending.repository.GroupMemberStatementRepository(f.jdbc));
        f.context.registerBean(GroupMemberStatementService.class,()->new GroupMemberStatementService(f.context.getBean(com.sacco.mvp.grouplending.repository.GroupMemberStatementRepository.class),f.context.getBean(com.sacco.mvp.accounting.service.GeneralLedgerService.class),f.context.getBean(ApplicationClock.class),coverage));
        var statements=f.context.getBean(GroupMemberStatementService.class);var own=statements.staff(f.actor,cycle,f.client,null,null,0,0,true);
        assertThat(own.coverage().rows()).singleElement().satisfies(x->assertThat(x.id()).isEqualTo(id));assertThat(own.coverage().rows().getFirst().received()).isZero();
        assertThat(statements.staff(f.actor,cycle,g.second,null,null,0,0,false).coverage().rows()).isEmpty();
        assertThat(statements.staff(f.actor,cycle,g.second,null,null,0,0,false).coverage().rows()).isEmpty();
        var messages=new org.springframework.context.support.ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");var pdf=new GroupMemberStatementPdf(new com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService(new ReportExportLimiter(1)),messages);
        for(var locale:List.of(Locale.ENGLISH,Locale.of("sw")))try(var doc=org.apache.pdfbox.Loader.loadPDF(pdf.render(own,locale))){assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(doc).toLowerCase(locale).replaceAll("\\s+", "")).contains(messages.getMessage("coverage.title",null,locale).toLowerCase(locale).replaceAll("\\s+", ""));}
    }

}
