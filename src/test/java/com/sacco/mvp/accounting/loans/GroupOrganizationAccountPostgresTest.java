package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupAuthorityDtos.SignerForm;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupOrganizationAccountPostgresTest {
    final GroupOrganizationFundingPostgresTest w=new GroupOrganizationFundingPostgresTest();
    final GroupOrganizationAgreementPostgresTest s=w.s;
    final GroupOrganizationPostgresTest o=w.o;
    final LoanRecordingPostgresTest f=w.f;
    GroupOrganizationAccountService service;
    @BeforeAll void start(){w.start();GroupCoveragePostgresTest.register(f);f.context.registerBean(GroupOrganizationAccountRepository.class,()->new GroupOrganizationAccountRepository(f.jdbc));f.context.registerBean(GroupCycleStatementRepository.class,()->new GroupCycleStatementRepository(new NamedParameterJdbcTemplate(f.jdbc)));f.context.registerBean(GroupOrganizationAccountService.class,()->new GroupOrganizationAccountService(f.context.getBean(GroupOrganizationAccountRepository.class),f.context.getBean(GroupCycleStatementRepository.class),f.context.getBean(GroupConsentService.class),f.context.getBean(ApplicationClock.class),new ReportExportLimiter(2),f.context.getBean(GroupCoverageService.class)));service=f.context.getBean(GroupOrganizationAccountService.class);}
    @AfterAll void stop(){w.stop();}
    @BeforeEach void fixture(){w.fixture();}
    UUID grant(){var form=o.authorityForm();form.getSigners().getFirst().setMayViewAccount(true);return o.authorities.record(f.actor,o.group,form);}
    com.sacco.mvp.grouplending.dto.GroupCycleStatementDtos.Statement read(){return service.statement(s.signer,s.cycle,null,null,0,false);}
    @Test void signingReceivingAndMembershipNeverImplyAccountViewing(){
        assertThat(service.list(s.signer,0).rows()).isEmpty();assertThatThrownBy(this::read).isInstanceOf(AccessDeniedException.class);
        var mandate=grant();var row=read();assertThat(row.context().model()).isEqualTo("ORGANIZATION");assertThat(row.allocations()).isEmpty();assertThat(row.loanLinks()).isFalse();assertThat(row.receiptLinks()).isFalse();assertThat(row.totals().funded()).isZero();assertThat(service.list(s.signer,0).rows()).singleElement().satisfies(a->assertThat(a.cycle()).isEqualTo(s.cycle));
        assertThat(o.authorities.detail(f.actor,o.group,mandate).signers().getFirst().mayViewAccount()).isTrue();assertThat(o.authorities.detail(f.actor,o.group,o.authority).signers().getFirst().mayViewAccount()).isFalse();assertThat(o.count("loan_ledgers")).isZero();assertThat(o.count("lending_group_guarantee")).isZero();
    }
    @Test void fundedCompanyStatementRetainsPayerAndIndependentCorrectionWithoutPersonalDebt()throws Exception{
        f.service.disburse(f.actor,w.funding());grant();assertThat(read().totals().principal()).isEqualByComparingTo("1000.00");var form=f.receiptForm(s.loan);form.setClientId(f.service.detail(f.actor,s.loan).loan().clientId());form.setLoanPayer("Synthetic organization treasurer");var receipt=f.receiptService().post(f.actor,form);assertThat(read().totals().principal()).isEqualByComparingTo("880.00");assertThat(read().movements()).anySatisfy(m->{assertThat(m.payer()).isEqualTo("Synthetic organization treasurer");assertThat(m.receiptId()).isEqualTo(receipt.header().id());});
        f.receiptService().reverse(f.operator("B1"),receipt.header().id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic organization correction");assertThat(read().totals().principal()).isEqualByComparingTo("1000.00");assertThat(read().movements()).anySatisfy(m->assertThat(m.reverses()).isNotBlank());assertThat(read().loans()).hasSize(1);assertThat(read().contributions()).isEmpty();assertThat(f.jdbc.queryForObject("select applicant_member_id from loan_ledgers where loan_application_id=?",UUID.class,s.loan)).isNull();assertThat(o.count("loan_ledgers")).isEqualTo(1);
        var messages=new org.springframework.context.support.ReloadableResourceBundleMessageSource();messages.setBasename("classpath:messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);var rendered=new GroupCycleStatementPdf(new com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService(new ReportExportLimiter(1)),messages);for(var language:List.of(Locale.ENGLISH,Locale.of("sw"))){byte[] pdf=rendered.render(service.statement(s.signer,s.cycle,null,null,0,true),language);assertThat(pdf).startsWith((byte)'%',(byte)'P',(byte)'D',(byte)'F');java.nio.file.Files.write(java.nio.file.Path.of("target/organization-account-"+language.getLanguage()+".pdf"),pdf);}java.nio.file.Files.writeString(java.nio.file.Path.of("target/organization-account-fixture.json"),"{\"institution\":\""+f.institution+"\",\"actor\":\""+f.actor.getMemberId()+"\",\"viewer\":\""+f.client+"\",\"cycle\":\""+s.cycle+"\",\"loan\":\""+s.loan+"\"}");
    }
    @Test void viewOnlyNamedPersonNeedsNoSigningOrReceivingPower(){
        var viewer=f.operator("B1").getMemberId();f.jdbc.update("update members set is_member=true where id=?",viewer);var person=s.client(viewer);var form=o.authorityForm();var extra=new SignerForm();extra.setClientId(viewer);extra.setMayViewAccount(true);form.getSigners().add(extra);o.authorities.record(f.actor,o.group,form);assertThat(service.list(person,0).rows()).hasSize(1);assertThat(service.statement(person,s.cycle,null,null,0,false).totals().funded()).isZero();assertThatThrownBy(()->s.service.packet(person,s.cycle,0)).isInstanceOf(AccessDeniedException.class);assertThat(o.count("lending_group_member")).isZero();
    }
    @Test void replacementRevocationAndExpiryNeverReviveOlderViewingPowers(){
        var first=grant();assertThat(read().context().cycle()).isEqualTo(s.cycle);var replacement=o.authorities.record(f.actor,o.group,o.authorityForm());assertThatThrownBy(this::read).isInstanceOf(AccessDeniedException.class);assertThat(service.list(s.signer,0).rows()).isEmpty();o.authorities.revoke(f.actor,o.group,replacement,"Synthetic replacement withdrawn");assertThatThrownBy(this::read).isInstanceOf(AccessDeniedException.class);
        var current=grant();assertThat(read().context().cycle()).isEqualTo(s.cycle);o.authorities.revoke(f.actor,o.group,current,"Synthetic viewing mandate revoked");assertThatThrownBy(this::read).isInstanceOf(AccessDeniedException.class);assertThat(o.authorities.detail(f.actor,o.group,first).state()).isEqualTo("SUPERSEDED");
    }
    @Test void inclusiveValidityAndInactiveGroupBlockCurrentReads(){
        var form=o.authorityForm();form.getSigners().getFirst().setMayViewAccount(true);form.setValidFrom(LoanRecordingPostgresTest.TODAY);form.setValidThrough(LoanRecordingPostgresTest.TODAY);o.authorities.record(f.actor,o.group,form);assertThat(read().context().cycle()).isEqualTo(s.cycle);var clock=f.context.getBean(ApplicationClock.class);when(clock.today()).thenReturn(LoanRecordingPostgresTest.TODAY.plusDays(1));assertThatThrownBy(this::read).isInstanceOf(AccessDeniedException.class);when(clock.today()).thenReturn(LoanRecordingPostgresTest.TODAY.minusDays(1));assertThat(service.list(s.signer,0).rows()).isEmpty();when(clock.today()).thenReturn(LoanRecordingPostgresTest.TODAY);o.groups.status(f.actor,o.group,o.groups.detail(f.actor,o.group).version(),false);assertThatThrownBy(this::read).isInstanceOf(AccessDeniedException.class);
    }
    @Test void currentClaimsBranchTenantAndClientSessionAreAllRequired(){
        grant();assertThatThrownBy(()->service.statement(f.actor,s.cycle,null,null,0,false)).isInstanceOf(AccessDeniedException.class);var member=f.context.getBean(MemberDirectoryService.class).find(f.client).orElseThrow();var limited=new AppUserPrincipal(member,EnumSet.of(UserClaim.MEMBER_LOANS_VIEW),false);assertThat(service.statement(limited,s.cycle,null,null,0,false).loanLinks()).isFalse();assertThat(service.canExport(limited)).isFalse();assertThatThrownBy(()->service.statement(limited,s.cycle,null,null,0,true)).isInstanceOf(AccessDeniedException.class);
        when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.client),anyCollection(),anyBoolean())).thenReturn(Set.of());assertThatThrownBy(this::read).isInstanceOf(AccessDeniedException.class);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.client),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
        member.setStationId("B2");var moved=new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class),false);assertThat(service.list(moved,0).rows()).isEmpty();assertThatThrownBy(()->service.statement(moved,s.cycle,null,null,0,false)).isInstanceOf(AccessDeniedException.class);member.setStationId("B1");member.setSaccoId("FOREIGN");var foreign=new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class),false);assertThatThrownBy(()->service.statement(foreign,s.cycle,null,null,0,false)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void viewingEvidenceIsImmutableAndRetriesCannotExpandPowers(){
        var form=o.authorityForm();form.getSigners().getFirst().setMayViewAccount(true);var id=o.authorities.record(f.actor,o.group,form);assertThat(o.authorities.record(f.actor,o.group,form)).isEqualTo(id);form.getSigners().getFirst().setMayViewAccount(false);assertThatThrownBy(()->o.authorities.record(f.actor,o.group,form)).hasMessage("group.error.retry");assertThatThrownBy(()->f.jdbc.update("update lending_group_authority_signer set may_view_account=false where authority_id=?",id)).isInstanceOf(DataAccessException.class);
    }
}
