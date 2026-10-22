package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.AccountFilter;
import com.sacco.mvp.accounting.loans.repository.LoanProductAccountRepository;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_POSTING_KIND_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_posting_kind_test")
class PostingAccountCompatibilityPostgresTest {
    @Test void upgradePreservesLegacyAccountAndVersionEvidenceWhileMakingTheAccountSelectable(){
        var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_POSTING_KIND_TEST_URL"),"microfinance_test","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("87").load().migrate();
        var jdbc=new JdbcTemplate(ds);String institution="POSTING-"+UUID.randomUUID();UUID actor=UUID.randomUUID(),legacy=UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Posting compatibility',true,now(),now())",institution);
        jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,staff_access_status,created_at,is_member,password_hash) values(?,?,'B1',?,'Posting fixture','ACTIVE','ACCOUNTANT','ACTIVE',now(),false,'test-only')",actor,institution,actor.toString());
        jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,maker_id,created_at) values(?,?,'111002','Existing loan principal','ASSET','DEBIT','CONTROL','LOAN_PRINCIPAL',?,now())",legacy,institution,actor);
        var before=jdbc.queryForMap("select current_version_id,kind from gl_account where id=?",legacy);
        String snapshot=jdbc.queryForObject("select snapshot::text from gl_account_version where id=?",String.class,before.get("current_version_id"));
        var flyway=Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();flyway.migrate();flyway.validate();
        assertThat(jdbc.queryForMap("select current_version_id,kind from gl_account where id=?",legacy)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select snapshot::text from gl_account_version where id=?",String.class,before.get("current_version_id"))).isEqualTo(snapshot);
        var products=new LoanProductAccountRepository(jdbc);assertThat(products.valid(institution,legacy)).isTrue();assertThat(products.valid("FOREIGN",legacy)).isFalse();
        assertThat(products.choices(institution,"",0,legacy)).hasSize(1);
        assertThat(new GeneralLedgerRepository(jdbc).chart(institution,new AccountFilter("","","POSTING",""),0,31)).extracting(row->row.account().id()).containsExactly(legacy);
        assertThat(jdbc.queryForObject("select gl_account_requires_loan_source(?)",Boolean.class,legacy)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution)).isZero();
    }
}
