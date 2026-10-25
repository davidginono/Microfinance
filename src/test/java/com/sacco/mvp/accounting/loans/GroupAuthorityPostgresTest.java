package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupAuthorityDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupAuthorityPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();GroupAuthorityService service;GroupLendingService groups;UUID group;
    @BeforeAll void start(){f.context=new AnnotationConfigApplicationContext(Config.class);f.jdbc=f.context.getBean(JdbcTemplate.class);f.service=f.context.getBean(com.sacco.mvp.accounting.loans.service.LoanRecordingService.class);service=f.context.getBean(GroupAuthorityService.class);groups=f.context.getBean(GroupLendingService.class);}
    @AfterAll void stop(){f.stop();}
    @BeforeEach void fixture(){f.fixture();var clock=f.context.getBean(ApplicationClock.class);reset(clock);when(clock.today()).thenReturn(LoanRecordingPostgresTest.TODAY);when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-08T12:00:00+03:00"));var form=new GroupForm();form.setName("Synthetic organization <Company>");form.setLegalForm("ORGANIZATION");form.setEvidence("Synthetic organization identity source");group=groups.create(f.actor,form);}
    AuthorityForm form(){var form=service.editor(f.actor,group,null).form();form.setValidFrom(LocalDate.of(2026,10,1));form.setValidThrough(LocalDate.of(2027,10,1));form.setMaxPrincipal(new BigDecimal("100000.00"));form.setRequiredSignatures(1);form.setCapacityEvidence("Synthetic verified legal capacity");form.setResolutionEvidence("Synthetic governing resolution");form.setSignatureEvidence("Synthetic checked original signatures");form.setPayoutType(PaymentDestinationType.BANK_ACCOUNT);form.setPayoutProvider("Synthetic bank");form.setPayoutHolder("Synthetic organization");form.setPayoutIdentifier("QA-ACCOUNT");form.setPayoutEvidence("Synthetic account verification");form.setVerified(true);var signer=form.getSigners().getFirst();signer.setClientId(f.client);signer.setMayApply(true);signer.setMaySign(true);signer.setMayReceive(true);return form;}
    int count(String table){return f.jdbc.queryForObject("select count(*) from "+table+" where sacco_id=?",Integer.class,f.institution);}
    @Test void authorityNamesExplicitNonMemberPowersWithoutCreatingPersonalDebtOrGuarantees(){
        UUID id=service.record(f.actor,group,form());var authority=service.detail(f.actor,group,id);
        assertThat(authority.state()).isEqualTo("CURRENT");assertThat(authority.revision()).isEqualTo(1);assertThat(authority.groupName()).isEqualTo("Synthetic organization <Company>");assertThat(authority.signers()).singleElement().satisfies(s->{assertThat(s.clientId()).isEqualTo(f.client);assertThat(s.maySign()).isTrue();assertThat(s.mayApply()).isTrue();assertThat(s.mayReceive()).isTrue();});
        assertThat(authority.recordedBy()).isEqualTo(f.actor.getMemberId());assertThat(authority.payoutIdentifier()).isEqualTo("QA-ACCOUNT");assertThat(groups.detail(f.actor,group).members()).isZero();
        for(String table:List.of("loan_applications","loan_ledgers","gl_journal","lending_group_guarantee"))assertThat(count(table)).as(table).isZero();
    }
    @Test void retriesAreIdempotentAndCannotChangeTheRecordedEvidenceOrActor(){
        var form=form();var id=service.record(f.actor,group,form);assertThat(service.record(f.actor,group,form)).isEqualTo(id);assertThat(count("lending_group_authority")).isEqualTo(1);
        assertThatThrownBy(()->service.record(f.operator("B1"),group,form)).hasMessage("group.error.retry");form.setPayoutIdentifier("DIFFERENT-ACCOUNT");assertThatThrownBy(()->service.record(f.actor,group,form)).hasMessage("group.error.retry");
    }
    @Test void revisionsPreserveHistoryAndRevokingTheLatestNeverRevivesOlderAuthority(){
        var first=service.record(f.actor,group,form());var next=form();assertThat(next.getExpectedPreviousId()).isEqualTo(first);var second=service.record(f.actor,group,next);
        assertThat(service.detail(f.actor,group,first).state()).isEqualTo("SUPERSEDED");service.revoke(f.actor,group,second,"Synthetic document withdrawn");service.revoke(f.actor,group,second,"Synthetic document withdrawn");
        assertThat(service.detail(f.actor,group,second).state()).isEqualTo("REVOKED");assertThat(service.detail(f.actor,group,first).state()).isEqualTo("SUPERSEDED");assertThat(service.editor(f.actor,group,null).form().getExpectedPreviousId()).isEqualTo(second);
        assertThatThrownBy(()->service.revoke(f.actor,group,second,"Changed reason")).hasMessage("group.error.changed");assertThat(count("lending_group_authority_revocation")).isEqualTo(1);
    }
    @Test void simultaneousNewVersionsCannotSilentlyReplaceEachOther()throws Exception{
        var original=service.record(f.actor,group,form());var one=form();var two=form();
        try(var executor=Executors.newFixedThreadPool(2)){var tasks=executor.invokeAll(List.<Callable<String>>of(()->recordResult(one),()->recordResult(two)));assertThat(List.of(tasks.get(0).get(),tasks.get(1).get())).containsExactlyInAnyOrder("RECORDED","group.error.changed");}
        assertThat(count("lending_group_authority")).isEqualTo(2);assertThat(service.detail(f.actor,group,original).state()).isEqualTo("SUPERSEDED");
    }
    String recordResult(AuthorityForm form){try{service.record(f.actor,group,form);return "RECORDED";}catch(IllegalArgumentException e){return e.getMessage();}}
    @Test void validationRejectsMissingVerificationPowersAndRepeatedPeople(){
        var form=form();form.setVerified(false);assertThatThrownBy(()->service.record(f.actor,group,form)).hasMessage("group.error.authorityVerification");form.setVerified(true);form.getSigners().getFirst().setMayReceive(false);assertThatThrownBy(()->service.record(f.actor,group,form)).hasMessage("group.error.authoritySigners");
        form.getSigners().getFirst().setMayReceive(true);form.setRequiredSignatures(2);assertThatThrownBy(()->service.record(f.actor,group,form)).hasMessage("group.error.authoritySigners");form.setRequiredSignatures(1);form.getSigners().add(form.getSigners().getFirst());assertThatThrownBy(()->service.record(f.actor,group,form)).hasMessage("group.error.authoritySigners");assertThat(count("lending_group_authority")).isZero();
    }
    @Test void staleInactiveAndForeignBranchPeopleCannotBeNamed(){
        var form=form();f.jdbc.update("update members set station_id='B2' where id=?",f.client);assertThatThrownBy(()->service.record(f.actor,group,form)).hasMessage("group.error.client");f.jdbc.update("update members set station_id='B1',status='INACTIVE' where id=?",f.client);assertThatThrownBy(()->service.record(f.actor,group,form)).hasMessage("group.error.client");assertThat(count("lending_group_authority")).isZero();
    }
    @Test void historiesAndSignatoryChoicesAreScopedAndCurrentClaimRevocationIsEnforced(){
        var id=service.record(f.actor,group,form());var foreign=f.operator("B2");assertThatThrownBy(()->service.detail(foreign,group,id)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.clients(foreign,group,"",0)).isInstanceOf(AccessDeniedException.class);
        var remaining=EnumSet.allOf(UserClaim.class);remaining.remove(UserClaim.GROUP_AUTHORITY_VIEW);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(remaining);
        assertThatThrownBy(()->service.detail(f.actor,group,id)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.history(f.actor,group,0,"revision_desc")).isInstanceOf(AccessDeniedException.class);
    }
    @Test void identityAndPayoutSnapshotsSurviveClientProfileAndBranchChanges(){
        var id=service.record(f.actor,group,form());var original=service.detail(f.actor,group,id);f.jdbc.update("update members set full_name='Changed name',member_no=?,station_id='B2' where id=?",UUID.randomUUID().toString(),f.client);
        var read=service.detail(f.actor,group,id);assertThat(read.signers()).isEqualTo(original.signers());assertThat(read.payoutIdentifier()).isEqualTo(original.payoutIdentifier());
        assertThatThrownBy(()->f.jdbc.update("delete from members where id=?",f.client)).isInstanceOf(DataAccessException.class);
    }
    @Test void verifiedAndRevokedRecordsCannotBeEditedDeletedOrGivenMoreSigners(){
        var id=service.record(f.actor,group,form());service.revoke(f.actor,group,id,"Synthetic revocation");
        for(var sql:List.of("update lending_group_authority set payout_identifier='Changed' where id=?","delete from lending_group_authority where id=?","update lending_group_authority_signer set may_sign=false where authority_id=?","delete from lending_group_authority_signer where authority_id=?","delete from lending_group_authority_revocation where authority_id=?"))assertThatThrownBy(()->f.jdbc.update(sql,id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("insert into lending_group_authority_signer(authority_id,ordinal,client_id,client_number,name,may_apply,may_sign,may_receive) select ?,2,id,member_no,full_name,true,true,true from members where id=?",id,f.actor.getMemberId())).isInstanceOf(DataAccessException.class);
    }
    @Test void databaseRejectsPartialAuthorityRecordsEvenIfTheServiceIsBypassed(){
        var id=service.record(f.actor,group,form());assertThatThrownBy(()->f.jdbc.update("""
            insert into lending_group_authority select (jsonb_populate_record(null::lending_group_authority,to_jsonb(a)||
              jsonb_build_object('id',?::uuid,'revision',2,'previous_id',a.id,'request_key',?::uuid))).*
            from lending_group_authority a where a.id=?
            """,UUID.randomUUID(),UUID.randomUUID(),id)).isInstanceOf(DataAccessException.class);
        assertThat(count("lending_group_authority")).isEqualTo(1);
    }
    @Test void failedAuditRollsBackAuthorityAndAllSignatories(){
        doThrow(new IllegalStateException("Synthetic authority audit failure")).when(f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),any(),eq("GROUP_AUTHORITY_RECORDED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->service.record(f.actor,group,form())).hasMessage("Synthetic authority audit failure");assertThat(count("lending_group_authority")).isZero();assertThat(f.jdbc.queryForObject("select count(*) from lending_group_authority_signer s join members m on m.id=s.client_id where m.sacco_id=?",Integer.class,f.institution)).isZero();
    }
    @Test void validityDatesAreInclusiveAndFutureAndExpiredDocumentsAreDistinct(){
        var form=form();form.setValidFrom(LoanRecordingPostgresTest.TODAY);form.setValidThrough(LoanRecordingPostgresTest.TODAY);var id=service.record(f.actor,group,form);assertThat(service.detail(f.actor,group,id).state()).isEqualTo("CURRENT");
        var tomorrow=LoanRecordingPostgresTest.TODAY.plusDays(1);when(f.context.getBean(ApplicationClock.class).today()).thenReturn(tomorrow);
        assertThat(f.context.getBean(ApplicationClock.class).today()).isEqualTo(tomorrow);
        assertThat(service.detail(f.actor,group,id).state()).isEqualTo("EXPIRED");
        when(f.context.getBean(ApplicationClock.class).today()).thenReturn(LoanRecordingPostgresTest.TODAY.minusDays(1));assertThat(service.detail(f.actor,group,id).state()).isEqualTo("NOT_YET_VALID");
    }
    @Test void authorityPermissionsAreNeverAutomaticallyGrantedWithAnyRole(){for(var role:Position.values())assertThat(UserClaim.defaultClaims(List.of(role),false)).noneMatch(c->c.getFeature()==AccessFeature.GROUP_AUTHORITY);}
    @Test void malformedLimitsDatesCollectionsAndSortsFailBeforeCreatingRecords(){
        var form=form();form.setMaxPrincipal(new BigDecimal("1.001"));assertThatThrownBy(()->service.record(f.actor,group,form)).hasMessage("group.error.amount");form.setMaxPrincipal(BigDecimal.TEN);form.setValidThrough(form.getValidFrom().minusDays(1));assertThatThrownBy(()->service.record(f.actor,group,form)).hasMessage("group.error.date");
        form.setValidThrough(form.getValidFrom());form.getSigners().addAll(Collections.nCopies(50,form.getSigners().getFirst()));assertThatThrownBy(()->service.record(f.actor,group,form)).hasMessage("group.error.request");assertThatThrownBy(()->service.history(f.actor,group,0,"revision;delete")).hasMessage("group.error.query");assertThat(count("lending_group_authority")).isZero();
    }
    @Test void historyPaginationAndEveryDisplayedColumnHaveStableScopedOrdering(){
        var ids=new ArrayList<UUID>();for(int n=0;n<27;n++){var form=form();form.setMaxPrincipal(new BigDecimal("100000.00").add(BigDecimal.valueOf(n)));form.setValidFrom(LocalDate.of(2026,9,1).plusDays(n));form.setValidThrough(LocalDate.of(2027,9,1).plusDays(n));ids.add(service.record(f.actor,group,form));}
        var first=service.history(f.actor,group,0,"revision_desc").page();var second=service.history(f.actor,group,1,"revision_desc").page();assertThat(first.rows()).hasSize(25);assertThat(first.hasNext()).isTrue();assertThat(second.rows()).hasSize(2);assertThat(second.hasNext()).isFalse();
        var returned=new ArrayList<UUID>();first.rows().forEach(r->returned.add(r.id()));second.rows().forEach(r->returned.add(r.id()));var reversed=new ArrayList<>(ids);Collections.reverse(reversed);assertThat(returned).containsExactlyElementsOf(reversed);
        for(String sort:List.of("revision","from","through","principal")){assertThat(service.history(f.actor,group,0,sort).page().rows().getFirst().id()).as(sort).isEqualTo(ids.getFirst());assertThat(service.history(f.actor,group,0,sort+"_desc").page().rows().getFirst().id()).as(sort).isEqualTo(ids.getLast());}
        var states=service.history(f.actor,group,0,"state").page().rows();assertThat(states.getFirst().state()).isEqualTo("CURRENT");assertThat(service.history(f.actor,group,0,"state_desc").page().rows().getFirst().state()).isEqualTo("SUPERSEDED");
    }
    @Configuration(proxyBeanMethods=false) @Import(GroupLendingPostgresTest.Config.class) static class Config {
        @Bean GroupAuthorityRepository authorities(JdbcTemplate jdbc){return new GroupAuthorityRepository(jdbc);}
        @Bean GroupAuthorityService authorityService(GroupAuthorityRepository r,GroupLendingRepository g,GeneralLedgerService access,ApplicationClock clock,AuditService audit,ObjectMapper json){return new GroupAuthorityService(r,g,access,clock,audit,json);}
    }
}
