package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.service.UserClaimService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupSharedReceiptPostgresTest {
    final GroupSharedLoanPostgresTest s=new GroupSharedLoanPostgresTest();final LoanRecordingPostgresTest f=s.f;
    @BeforeAll void start(){s.start();}
    @AfterAll void stop(){s.stop();}
    @BeforeEach void setup(){s.setup();f.service.disburse(f.actor,s.funding());}
    Form form(){var x=f.receiptForm(s.loan);x.setClientId(f.service.detail(f.actor,s.loan).loan().clientId());x.setLoanPayer("Synthetic actual shared remitter");return x;}
    BigDecimal cash(List<LoanReceiptRow> rows){return rows.stream().flatMap(r->r.sharedBenefits().stream()).map(SharedReceiptBenefit::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    @Test void previewAndPostedReceiptPartitionOnePaymentAndKeepImmutableHistory(){
        var x=form();var preview=f.receiptService().preview(f.actor,x);assertThat(cash(preview)).isEqualByComparingTo("120.00");assertThat(preview.getFirst().sharedBenefits()).hasSize(2).allSatisfy(b->assertThat(b.excludedInterest()).isNull());assertThat(s.count("gl_journal")).isEqualTo(1);
        var saved=f.receiptService().post(f.actor,x);assertThat(cash(saved.rows())).isEqualByComparingTo(saved.header().total());assertThat(saved.rows().getFirst().sharedBenefits()).hasSize(2).allSatisfy(b->assertThat(b.payer()).isEqualTo(x.getLoanPayer()));
        assertThat(saved.rows().getFirst().sharedBenefits().stream().map(SharedReceiptBenefit::excludedInterest).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("2.40");
        assertThat(f.receiptService().post(f.actor,x).header().id()).isEqualTo(saved.header().id());assertThat(s.count("gl_journal")).isEqualTo(2);
        f.service.repay(f.actor,s.payment("1.00"));assertThat(f.receiptService().find(f.actor,saved.header().id()).orElseThrow().rows().getFirst().sharedBenefits()).isEqualTo(saved.rows().getFirst().sharedBenefits());
    }
    @Test void independentEnvelopeCorrectionRetainsPayerAndMemberComponents(){
        var saved=f.receiptService().post(f.actor,form());var correction=f.receiptService().reverse(f.operator("B1"),saved.header().id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic returned shared receipt cash");
        assertThat(correction.header().reversesId()).isEqualTo(saved.header().id());assertThat(correction.payer()).isEqualTo(saved.payer());assertThat(correction.rows().getFirst().sharedBenefits()).isEqualTo(saved.rows().getFirst().sharedBenefits());
        assertThat(s.shared.balances(f.actor,s.loan)).allSatisfy(b->{assertThat(b.remainingPrincipal()).isEqualByComparingTo(b.principal());assertThat(b.remainingInterest()).isEqualByComparingTo(b.interest());});assertThat(s.count("gl_journal")).isEqualTo(3);
    }
    @Test void legacySinglePostingReceiptsRecoverActualPayerAndFrozenBenefits(){
        var posted=f.service.repay(f.actor,s.payment("120.00"));var receipt=f.receiptService().find(f.actor,posted.id()).orElseThrow();assertThat(receipt.payer()).isEqualTo("Synthetic member A covering shared obligations");assertThat(cash(receipt.rows())).isEqualByComparingTo("120.00");
        var correction=f.receiptService().reverse(f.operator("B1"),posted.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic direct posting correction");assertThat(correction.rows().getFirst().sharedBenefits()).isEqualTo(receipt.rows().getFirst().sharedBenefits());assertThat(correction.payer()).isEqualTo(receipt.payer());
    }
    @Test void sharedRosterPreviewReadRegisterExportAndCorrectionRequireCurrentClaims(){
        var x=form();var receipt=f.receiptService().post(f.actor,x);var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_VIEW);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);
        assertThatThrownBy(()->f.receiptService().find(f.actor,receipt.header().id())).isInstanceOf(AccessDeniedException.class);assertThat(f.receiptService().list(f.actor,new Filter("",null,null,"","newest",0,25)).rows()).isEmpty();
        claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_POST);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);assertThatThrownBy(()->f.receiptService().preview(f.actor,form())).isInstanceOf(AccessDeniedException.class);
        claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_EXPORT);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);assertThatThrownBy(()->f.receiptService().export(f.actor,receipt.header().id())).isInstanceOf(AccessDeniedException.class);
        claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_REVERSE);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);assertThat(f.receiptService().canReverse(f.actor,receipt)).isFalse();assertThat(f.receiptService().find(f.operator("B2"),receipt.header().id())).isEmpty();assertThat(s.count("gl_journal")).isEqualTo(2);
    }
}
