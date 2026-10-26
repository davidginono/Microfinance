package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.service.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.repository.GroupMemberStatementRepository;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupMemberStatementPostgresTest {
    final GroupSharedLoanPostgresTest s=new GroupSharedLoanPostgresTest();final LoanRecordingPostgresTest f=s.f;
    GroupMemberStatementService statements;
    @BeforeAll void start(){s.s.s.g.start(Config.class);s.s.agreements=f.context.getBean(GroupSharedAgreementService.class);s.shared=f.context.getBean(GroupSharedLoanService.class);statements=f.context.getBean(GroupMemberStatementService.class);}
    @AfterAll void stop(){s.stop();}
    @BeforeEach void setup(){s.s.s.firstPaymentDate=LoanRecordingPostgresTest.TODAY.plusDays(1);s.setup();}
    com.sacco.mvp.grouplending.dto.GroupMemberStatementDtos.Statement own(){return statements.client(s.s.client,s.s.cycle,null,null,0,0,false);}
    @Test void ownScheduleAndCreditsNeverContainAnotherParticipantsAllocation()throws Exception{
        assertThat(own().source().funded()).isFalse();f.service.disburse(f.actor,s.funding());var payment=f.service.repay(f.actor,s.payment("120.00"));
        var row=own();assertThat(row.source().member().client()).isEqualTo(f.client);assertThat(row.source().member().principal()).isEqualByComparingTo("123.45");
        assertThat(row.schedule().stream().map(d->d.principal()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("123.45");assertThat(row.credits()).hasSize(1);assertThat(row.credits().getFirst().principal()).isEqualByComparingTo("14.81");assertThat(row.credits().getFirst().excludedInterest()).isPositive();assertThat(row.credits().getFirst().kind()).isEqualTo("PAYMENT");
        assertThat(row.credits().getFirst().receipt()).isEqualTo(payment.id());assertThat(f.receiptService().find(f.actor,row.credits().getFirst().receipt())).isPresent();assertThat(row.source().duePrincipal()).isZero();assertThat(row.source().dueInterest()).isZero();assertThat(row.source().known()).isTrue();
        var renderer=new GroupMemberStatementPdf(new LoanRecordingPdfService(new ReportExportLimiter(1)),f.context.getBean(org.springframework.context.MessageSource.class));
        for(var lang:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){byte[] bytes=renderer.render(statements.client(s.s.client,s.s.cycle,null,null,0,0,true),lang);try(var pdf=Loader.loadPDF(bytes)){assertThat(new PDFTextStripper().getText(pdf)).contains("123.45","14.81").doesNotContain("876.56","105.19");}Files.write(Path.of("target/member-statement-"+lang.getLanguage()+".pdf"),bytes);}
        assertThat(s.count("gl_journal")).isEqualTo(2);assertThat(s.count("lending_group_shared_movement")).isEqualTo(2);
        Files.writeString(Path.of("target/member-statement-fixture.json"),"{\"institution\":\""+f.institution+"\",\"client\":\""+f.client+"\",\"actor\":\""+f.actor.getMemberId()+"\",\"cycle\":\""+s.s.cycle+"\"}");
    }
    @Test void independentCorrectionRestoresOwnCreditsAndDateFiltersDoNotRepriceBalances(){
        f.service.disburse(f.actor,s.funding());var payment=f.service.repay(f.actor,s.payment("120.00"));var next=LoanRecordingPostgresTest.TODAY.plusDays(1);var clock=f.context.getBean(ApplicationClock.class);when(clock.today()).thenReturn(next);when(clock.now()).thenReturn(next.atTime(12,0).atOffset(java.time.ZoneOffset.ofHours(3)));
        f.service.reverse(f.operator("B1"),payment.id(),UUID.randomUUID(),next,"Synthetic personal statement correction");
        var row=statements.client(s.s.client,s.s.cycle,next,next,0,0,false);assertThat(row.credits()).hasSize(1);assertThat(row.credits().getFirst().kind()).isEqualTo("REVERSAL");assertThat(row.credits().getFirst().reverses()).isNotBlank();assertThat(row.source().member().remainingPrincipal()).isEqualByComparingTo("123.45");assertThat(own().credits()).hasSize(2).anySatisfy(c->assertThat(c.reversedBy()).isNotBlank());assertThat(row.source().duePrincipal()).isPositive();
    }
    @Test void scopeAndFreshClientOrStaffPermissionsAreEnforced(){
        assertThatThrownBy(()->statements.staff(f.operator("B2"),s.s.cycle,f.client,null,null,0,0,false)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->statements.staff(f.actor,s.s.cycle,UUID.randomUUID(),null,null,0,0,false)).isInstanceOf(AccessDeniedException.class);
        var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.MEMBER_LOANS_VIEW);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.client),anyCollection(),anyBoolean())).thenReturn(claims);assertThatThrownBy(this::own).isInstanceOf(AccessDeniedException.class);
        claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_EXPORT);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);assertThat(statements.staff(f.actor,s.s.cycle,f.client,null,null,0,0,false).staff()).isTrue();assertThatThrownBy(()->statements.staff(f.actor,s.s.cycle,f.client,null,null,0,0,true)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void conservedWaiverKeepsPersonalBalancesKnown(){
        f.service.disburse(f.actor,s.funding());var clock=f.context.getBean(ApplicationClock.class);when(clock.today()).thenReturn(LoanRecordingPostgresTest.TODAY.plusDays(15));f.context.getBean(LoanRepaymentLedgerService.class).waiveInterest(s.loan,f.actor,UUID.randomUUID(),clock.today(),new BigDecimal("1.00"),"Synthetic statement waiver needing allocation",null);
        assertThat(own().source().known()).isTrue();assertThat(own().source().member().waivedInterest()).isEqualByComparingTo("0.12");assertThat(own().credits()).anySatisfy(c->{assertThat(c.kind()).isEqualTo("INTEREST_WAIVER");assertThat(c.amount()).isZero();assertThat(c.waivedInterest()).isEqualByComparingTo("0.12");});assertThat(own().source().dueInterest()).isNotNull();
    }
    @Test void fullSettlementAndFundingCorrectionRemainDistinct(){
        f.service.disburse(f.actor,s.funding());var payment=f.service.repay(f.actor,s.payment("1000.01"));assertThat(own().source().member().remainingPrincipal()).isZero();assertThat(own().source().maximumLiability()).isZero();assertThat(own().source().member().excludedInterest()).isPositive();
        var second=f.operator("B1");f.service.reverse(second,payment.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic settlement correction");assertThat(own().source().maximumLiability()).isEqualByComparingTo("125.91");
    }
    @Test void returnedFundingIsNotShownAsPersonalDebt(){
        f.service.disburse(f.actor,s.funding());var second=f.operator("B1");var request=f.adjustments().request(f.actor,f.adjustment(s.loan,"DISBURSEMENT_REVERSAL"));f.adjustments().decide(second,request,UUID.randomUUID(),true,"Synthetic funding correction");assertThat(own().source().status()).isEqualTo("DISBURSEMENT_REVERSED");assertThat(own().source().maximumLiability()).isZero();assertThat(own().source().member().remainingPrincipal()).isZero();
    }
    @Test void postedCreditsArePagedWithoutDroppingHistory(){
        f.service.disburse(f.actor,s.funding());for(int i=0;i<26;i++)f.service.repay(f.actor,s.payment("1.00"));var first=own();var second=statements.client(s.s.client,s.s.cycle,null,null,1,0,false);assertThat(first.credits()).hasSize(25);assertThat(first.hasNext()).isTrue();assertThat(second.credits()).hasSize(1);assertThat(second.hasNext()).isFalse();assertThat(first.credits()).extracting(c->c.posting()).doesNotContain(second.credits().getFirst().posting());assertThat(statements.client(s.s.client,s.s.cycle,null,null,0,0,true).credits()).hasSize(26);
    }
    @Test void jointLiabilityUsesTheWholeRemainingLoanWithinTheSignedLimit(){
        var g=s.s.s.g;var stop=new com.sacco.mvp.grouplending.dto.GroupCycleDtos.TerminationForm();stop.setExpectedVersion(1);stop.setReason("Synthetic statement joint liability fixture");g.cycles.withdraw(f.actor,s.s.cycle,stop);s.s.s.rule(com.sacco.mvp.grouplending.dto.GroupCycleDtos.SharedLiability.JOINT_AND_SEVERAL);s.s.cycle=s.s.s.submit();s.loan=g.cycles.sharedFacility(f.actor,s.s.cycle).orElseThrow().loanId();for(var id:List.of(f.client,g.second))assertThat(g.consents.accept(g.client(id),s.s.cycle,true,"")).isNull();String hash=s.s.open();assertThat(s.s.agreements.confirm(s.s.client,s.s.cycle,hash,true,"")).isNull();var other=g.client(g.second);s.s.agreements.exportClient(other,s.s.cycle,Locale.ENGLISH);assertThat(s.s.agreements.confirm(other,s.s.cycle,s.s.agreements.packet(other,s.s.cycle).row().hash(),true,"")).isNull();f.service.disburse(f.actor,s.funding());assertThat(own().source().maximumLiability()).isEqualByComparingTo("1020.01");f.service.repay(f.actor,s.payment("120.00"));assertThat(own().source().maximumLiability()).isEqualByComparingTo("897.61");assertThat(own().source().member().remainingPrincipal()).isLessThan(own().source().maximumLiability());
    }
    @Configuration(proxyBeanMethods=false) @Import(GroupSharedAgreementPostgresTest.Config.class) static class Config {
        @Bean GroupMemberStatementRepository memberStatements(JdbcTemplate j){return new GroupMemberStatementRepository(j);}
        @Bean GroupMemberStatementService statementService(GroupMemberStatementRepository r,GroupConsentService c,GeneralLedgerService a,ApplicationClock clock){return new GroupMemberStatementService(r,c,a,clock);}
    }
}
