package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupPersonalGuaranteeDtos.*;
import com.sacco.mvp.grouplending.repository.GroupCycleRepository;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupSharedPersonalGuaranteePostgresTest {
    final GroupSharedLoanPostgresTest w=new GroupSharedLoanPostgresTest();final LoanRecordingPostgresTest f=w.f;
    GroupPersonalGuaranteeService guarantees;UUID cycle;AppUserPrincipal guarantor;
    @BeforeAll void start(){w.start();guarantees=GroupPersonalGuaranteePostgresTest.register(f);}
    @AfterAll void stop(){w.stop();}
    @BeforeEach void setup(){w.s.s.setup();reset(f.context.getBean(StationOtpSettingsService.class));f.context.getBean(SaccoRegistryService.class).findActiveSacco(f.institution).orElseThrow().setSaccoName("Synthetic loan institution");f.jdbc.update("update loan_product_settings set guarantors_required=1 where id=?",f.product);cycle=w.s.s.submit();w.s.cycle=cycle;w.loan=w.s.s.g.cycles.sharedFacility(f.actor,cycle).orElseThrow().loanId();w.s.client=w.s.s.g.client(f.client);for(var id:List.of(f.client,w.s.s.g.second)){assertThat(w.s.s.g.consents.accept(w.s.s.g.client(id),cycle,true,"")).isNull();f.jdbc.update("insert into member_payment_details(member_id,destination_type,provider,account_holder_name,account_identifier,created_at,updated_at) values(?,'BANK_ACCOUNT','Synthetic bank','Synthetic holder',?,now(),now())",id,"QA-SHARED-"+id);}String hash=w.s.open();assertThat(w.s.agreements.confirm(w.s.client,cycle,hash,true,"")).isNull();var second=w.s.s.g.client(w.s.s.g.second);w.s.agreements.exportClient(second,cycle,Locale.ENGLISH);assertThat(w.s.agreements.confirm(second,cycle,w.s.agreements.packet(second,cycle).row().hash(),true,"")).isNull();var staff=f.context.getBean(MemberDirectoryService.class).find(f.actor.getMemberId()).orElseThrow();staff.setSignatureText("Synthetic lender signature");f.jdbc.update("update members set signature_text='Synthetic lender signature' where id=?",f.actor.getMemberId());guarantor=person();}
    AppUserPrincipal person(){var id=f.operator("B1").getMemberId();f.jdbc.update("update members set is_member=true,signature_text='Synthetic personal signature' where id=?",id);return w.s.s.g.client(id);}
    Form form(AppUserPrincipal person){var x=new Form();x.setClientId(person.getMemberId());x.setCapacity(new BigDecimal("5000.00"));x.setCapacityEvidence("Synthetic independently verified guarantor capacity");x.setValidThrough(LocalDate.now(ZoneId.of("Africa/Dar_es_Salaam")).plusDays(30));x.setDocument(new GroupOrganizationPostgresTest().documentForm());return x;}
    UUID offer(AppUserPrincipal p){return guarantees.record(f.actor,cycle,form(p),GroupOrganizationPostgresTest.documentFile());}
    void accept(AppUserPrincipal p,UUID id){var packet=guarantees.packet(p,id);guarantees.clientDocument(p,id);assertThat(guarantees.accept(p,id,packet.offer().hash(),true,"")).isNull();}
    @Test void externalPersonalGuaranteePrecedesOneParentFundingWithoutCreatingAnotherDebt(){
        assertThatThrownBy(()->guarantees.startReview(f.actor,cycle)).hasMessage("recording.error.personalGuarantees");var id=offer(guarantor);assertThat(guarantees.packet(guarantor,id).offer().cap()).isEqualByComparingTo("1020.01");accept(guarantor,id);guarantees.startReview(f.actor,cycle);f.service.disburse(f.actor,w.funding());assertThat(w.count("loan_ledgers")).isEqualTo(1);assertThat(w.count("lending_group_guarantee")).isZero();assertThat(w.shared.balances(f.actor,w.loan)).hasSize(2);assertThat(f.context.getBean(GroupCycleRepository.class).exposures(f.actor,List.of(guarantor.getMemberId())).get(guarantor.getMemberId()).reserved()).isEqualByComparingTo("1020.01");f.service.repay(f.actor,w.payment("120.00"));assertThat(w.shared.balances(f.actor,w.loan).stream().map(b->b.remainingPrincipal()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("880.01");
        try{java.nio.file.Files.writeString(java.nio.file.Path.of("target/personal-guarantee-fixture.json"),"{\"institution\":\""+f.institution+"\",\"actor\":\""+f.actor.getMemberId()+"\",\"guarantor\":\""+guarantor.getMemberId()+"\",\"cycle\":\""+cycle+"\",\"loan\":\""+w.loan+"\",\"offer\":\""+id+"\"}");}catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    @Test void jointBorrowerSignatureNeverSubstitutesForASeparatePersonalGuarantee(){
        var own=form(w.s.client);assertThatThrownBy(()->guarantees.record(f.actor,cycle,own,GroupOrganizationPostgresTest.documentFile())).hasMessage("group.error.personalGuarantorOwnDebt");assertThatThrownBy(()->guarantees.startReview(f.actor,cycle)).hasMessage("recording.error.personalGuarantees");assertThatThrownBy(()->f.jdbc.update("update loan_applications set status='READY_FOR_DISBURSEMENT' where id=?",w.loan)).isInstanceOf(DataAccessException.class);assertThat(w.count("loan_ledgers")).isZero();
    }
    @Test void configuredMultipleGuaranteesRequireDistinctAcceptedPeople(){
        // Product requirements are frozen on origination, so use a new unfunded cycle.
        var stop=new com.sacco.mvp.grouplending.dto.GroupCycleDtos.TerminationForm();stop.setExpectedVersion(w.s.s.g.cycles.detail(f.actor,cycle).version());stop.setReason("Synthetic two-guarantor product fixture");w.s.s.g.cycles.withdraw(f.actor,cycle,stop);f.jdbc.update("update loan_product_settings set guarantors_required=2 where id=?",f.product);cycle=w.s.s.submit();var one=offer(guarantor);accept(guarantor,one);assertThatThrownBy(()->guarantees.startReview(f.actor,cycle)).hasMessage("recording.error.personalGuarantees");var other=person();var two=offer(other);accept(other,two);guarantees.startReview(f.actor,cycle);assertThat(w.s.s.g.cycles.sharedFacility(f.actor,cycle).orElseThrow().status()).isEqualTo("READY_FOR_DISBURSEMENT");
    }
}
