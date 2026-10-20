package com.sacco.mvp.accounting.repayment.repository;

import com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class RepaymentPolicyRepository {
    private final JdbcTemplate jdbc;
    public Optional<Policy> effective(String institution,LocalDate date){
        return jdbc.query("select p.*,coalesce(a.code||' - '||a.name,'') account from repayment_accounting_policy p left join gl_account a on a.id=p.interest_account_id and a.sacco_id=p.sacco_id where p.sacco_id=? and p.effective_from<=? order by p.effective_from desc limit 1",
            (r,n)->new Policy(r.getObject("id",UUID.class),r.getInt("version"),r.getObject("effective_from",LocalDate.class),AllocationOrder.valueOf(r.getString("allocation_order")),r.getObject("interest_account_id",UUID.class),r.getString("account"),r.getString("reason"),r.getObject("created_at",OffsetDateTime.class)),institution,date).stream().findFirst();
    }
    public boolean standards(String institution){return Integer.valueOf(2).equals(jdbc.queryForObject("select count(*) from gl_transaction_code t join gl_activity a on a.id=t.activity_id and a.sacco_id=t.sacco_id where a.sacco_id=? and a.system_key='LOAN_REPAYMENT' and a.active and t.active and t.source_event='REPAYMENT' and t.system_key in('REPAYMENT_PRINCIPAL','REPAYMENT_INTEREST')",Integer.class,institution));}
}
