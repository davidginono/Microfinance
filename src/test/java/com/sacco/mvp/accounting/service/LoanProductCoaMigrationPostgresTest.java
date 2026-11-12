package com.sacco.mvp.accounting.service;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_COA_MIGRATION_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_product_coa_migration_test")
class LoanProductCoaMigrationPostgresTest {
    JdbcTemplate jdbc;
    record Fixture(String institution,UUID maker,UUID product,UUID principal,UUID principalVersion,UUID metadata,boolean automatic) {}
    @Test void upgradePreservesOriginalVersionsAndBackfillsFeesWithoutActivatingPolicies() {
        try(var ds=new HikariDataSource()) {
            ds.setJdbcUrl(System.getenv("MICROFINANCE_COA_MIGRATION_TEST_URL"));ds.setUsername("microfinance_test");ds.setPassword("");ds.setMaximumPoolSize(2);
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("155").load().migrate();jdbc=new JdbcTemplate(ds);
            var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
            var fixtures=new ArrayList<Fixture>();
            tx.executeWithoutResult(t->{fixtures.add(seed(true,true));fixtures.add(seed(false,false));fixtures.add(seed(false,true));});
            var evidence=new HashMap<UUID,String>();
            for(var f:fixtures)evidence.put(f.principalVersion(),jdbc.queryForObject("select snapshot::text from gl_account_version where id=?",String.class,f.principalVersion()));
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
            for(var f:fixtures) {
                assertThat(jdbc.queryForObject("select snapshot::text from gl_account_version where id=?",String.class,f.principalVersion())).isEqualTo(evidence.get(f.principalVersion()));
                var latest=jdbc.queryForMap("select state,snapshot->>'code' code from gl_account_version where account_id=? order by number desc limit 1",f.principal());
                assertThat(latest.get("code")).isEqualTo("112001");
                assertThat(latest.get("state")).isEqualTo("PENDING");
                // The automatically approved case has a pending metadata revision; its effective correction is a baseline.
                if(f.automatic() || f.metadata()!=null)assertThat(jdbc.queryForObject("select code from gl_account where id=?",String.class,f.principal())).isEqualTo("112001");
                else assertThat(jdbc.queryForObject("select count(*) from gl_account where id=?",Integer.class,f.principal())).isZero();
                var fees=jdbc.queryForList("select g.component,v.state,v.snapshot->>'type' type,v.snapshot->>'code' code from loan_product_fee_account_provision g join gl_account_version v on v.id=g.version_id where g.product_id=? order by component",f.product());
                assertThat(fees).hasSize(2).allSatisfy(row->{assertThat(row.get("type")).isEqualTo("INCOME");assertThat(row.get("state")).isEqualTo(f.automatic()?"APPROVED":"PENDING");});
                assertThat(fees).extracting(row->row.get("code")).containsExactly("512001","513001");
                assertThat(jdbc.queryForObject("select count(*) from loan_charge_policy where product_id=?",Integer.class,f.product())).isZero();
                assertThat(jdbc.queryForObject("select count(*) from gl_account_version_event where sacco_id=? and action='SYSTEM_FEE_GENERATED'",Integer.class,f.institution())).isEqualTo(2);
                if(f.metadata()!=null)assertThat(jdbc.queryForObject("select state from gl_account_version where id=?",String.class,f.metadata())).isEqualTo("WITHDRAWN");
            }
        }
    }
    Fixture seed(boolean automatic,boolean effectivePrincipal) {
        String institution="UPGRADE-"+UUID.randomUUID();UUID maker=UUID.randomUUID(),product=UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Synthetic migration institution',true,now(),now())",institution);
        jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at) values(?,?,'B1',true,now(),now())",UUID.randomUUID(),institution);
        jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,'B1',?,'Synthetic maker','ACTIVE','ACCOUNTANT',now(),false,'test-only')",maker,institution,"TEST-"+maker);
        jdbc.update("insert into loan_product_settings(id,sacco_id,loan_type,active,product_status,product_code,product_name,form_schema,guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,created_at,updated_at) values(?,?,'DEVELOPMENT_LOAN',true,'ACTIVE',?,'Migration loan','{}',0,0.24,'FLAT_RATE','MONTHLY',1,12,now(),now())",product,institution,"P-"+product);
        UUID assets=heading(institution,maker,"100000",null,"ASSET");heading(institution,maker,"110000",assets,"ASSET");
        UUID loans=heading(institution,maker,"130000",assets,"ASSET"),principalFamily=heading(institution,maker,"131000",loans,"ASSET");
        UUID income=heading(institution,maker,"500000",null,"INCOME"),loanIncome=heading(institution,maker,"510000",income,"INCOME"),interestFamily=heading(institution,maker,"511000",loanIncome,"INCOME");
        if(!automatic)jdbc.update("insert into gl_account_approval_policy(sacco_id,version,create_automatic,update_automatic,deactivate_automatic,reactivate_automatic,reason,created_by,created_at) values(?,1,false,true,true,true,'Synthetic independent account policy',?,now())",institution,maker);
        UUID principal=UUID.randomUUID(),interest=UUID.randomUUID();
        UUID pv=creation(institution,maker,principal,"131001",principalFamily,"ASSET",effectivePrincipal),iv=creation(institution,maker,interest,"511001",interestFamily,"INCOME",automatic);
        jdbc.update("insert into loan_product_account_provision values(?,?,'B1',?,?,?,?,?,now())",product,institution,principal,interest,pv,iv,maker);
        UUID metadata=null;
        if(effectivePrincipal){metadata=UUID.randomUUID();
            jdbc.update("insert into gl_account_version(id,sacco_id,account_id,number,base_id,operation,state,before_snapshot,snapshot,creator_id,station_id,reason,policy_version,mode,created_at,submitted_at,request_key,request_hash) select ?,sacco_id,account_id,2,id,'UPDATE','PENDING',snapshot,snapshot||jsonb_build_object('name','Proposed principal name'),creator_id,station_id,'Synthetic metadata review',policy_version,'INDEPENDENT',now(),now(),?,repeat('0',64) from gl_account_version where id=?",metadata,UUID.randomUUID(),pv);
            event(metadata,"SUBMITTED");
        }
        return new Fixture(institution,maker,product,principal,pv,metadata,automatic);
    }
    UUID heading(String institution,UUID maker,String code,UUID parent,String type){
        UUID id=UUID.randomUUID();jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,parent_id,maker_id,created_at) values(?,?,?,? ,?,?,'HEADING','OTHER',?,?,now())",id,institution,code,"Heading "+code,type,type.equals("ASSET")?"DEBIT":"CREDIT",parent,maker);return id;
    }
    UUID creation(String institution,UUID maker,UUID account,String code,UUID parent,String type,boolean effective){
        UUID version=UUID.randomUUID();String balance=type.equals("ASSET")?"DEBIT":"CREDIT",purpose=type.equals("ASSET")?"OTHER":"INCOME";
        var values=new LinkedHashMap<String,Object>(Map.of("id",account,"code",code,"name","Generated "+code,"type",type,"normalBalance",balance,"kind","POSTING","purpose",purpose,"parentId",parent,"active",true));
        values.put("nameSw",null);values.put("description",null);
        String snapshot=JsonMapper.builder().build().writeValueAsString(values);
        jdbc.update("insert into gl_account_version(id,sacco_id,account_id,number,operation,state,snapshot,creator_id,station_id,reason,policy_version,mode,created_at,submitted_at,decided_at,request_key,request_hash) values(?,?,?,1,'CREATE',?,?::jsonb,?,'B1','Synthetic product creation',0,?,now(),now(),case when ? then now() end,?,repeat('0',64))",version,institution,account,effective?"APPROVED":"PENDING",snapshot,maker,effective?"AUTOMATIC":"INDEPENDENT",effective,UUID.randomUUID());
        event(version,effective?"AUTOMATIC_APPROVAL":"SUBMITTED");
        if(effective)jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,parent_id,maker_id,created_at,current_version_id) values(?,?,?,? ,?,?,'POSTING',?,?,?,now(),?)",account,institution,code,"Generated "+code,type,balance,purpose,parent,maker,version);
        return version;
    }
    void event(UUID version,String action){jdbc.update("insert into gl_account_version_event(id,version_id,sacco_id,action,actor_id,station_id,reason,state,policy_version,mode,revision,before_snapshot,snapshot,recorded_at) select ?,id,sacco_id,?,creator_id,station_id,reason,state,policy_version,mode,revision,before_snapshot,snapshot,now() from gl_account_version where id=?",UUID.randomUUID(),action,version);}
}
