package com.sacco.mvp.accounting.service;

import com.sacco.mvp.domain.UserClaim;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNT_VERSION_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_account_versions_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfficeAccountPostgresTest {
    final AccountVersionPostgresTest f=new AccountVersionPostgresTest();
    @BeforeAll void start(){f.start();}
    @AfterAll void stop(){f.stop();}
    @BeforeEach void fixture(){f.setup();}
    @Test void accountReviewsStayInTheBranchAndExcludeTheMaker() {
        f.ledger.configureAccountApproval(f.maker,f.policy(false,false,false,false));
        var current=f.ledger.accountCurrent(f.maker,f.family);
        UUID id=f.ledger.proposeAccountVersion(f.maker,f.family,current.id(),"UPDATE",UUID.randomUUID(),f.change(current.proposed(),"Pending update"),true);
        var service=new AccountOfficeWorkService(f.books,f.ledger);
        var reviewer=f.operator("B1",EnumSet.allOf(UserClaim.class));
        assertThat(service.read(reviewer,true,"","number",0).rows()).extracting(com.sacco.mvp.staffoffice.dto.OfficeWork.Task::href)
            .containsExactly("/finance/accounts/versions/"+id);
        assertThat(service.read(f.maker,true,"","number",0).rows()).isEmpty();
        assertThat(service.read(f.operator("B2",EnumSet.allOf(UserClaim.class)),true,"","number",0).rows()).isEmpty();
        f.ledger.accountVersionAction(reviewer,id,f.version(id).revision(),"APPROVE","Independent review");
        assertThat(service.read(reviewer,true,"","number",0).rows()).isEmpty();
    }
    @Test void accountDraftsBelongToTheirRecorderAndHaveNoApprovalEffect() {
        var current=f.ledger.accountCurrent(f.maker,f.family);
        UUID id=f.ledger.proposeAccountVersion(f.maker,f.family,current.id(),"UPDATE",UUID.randomUUID(),f.change(current.proposed(),"Draft update"),false);
        var service=new AccountOfficeWorkService(f.books,f.ledger);
        assertThat(service.read(f.maker,false,"","number",0).rows()).hasSize(1);
        assertThat(service.read(f.operator("B1",EnumSet.allOf(UserClaim.class)),false,"","number",0).rows()).isEmpty();
        assertThat(f.version(id).state()).isEqualTo("DRAFT");
    }
}
