package com.sacco.mvp.accounting.loans.repository;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class LoanProductAccountRepository {
    private final JdbcTemplate jdbc;
    private static final String ELIGIBLE = """
        ((?='PRINCIPAL' and kind='CONTROL' and type='ASSET' and normal_balance='DEBIT' and purpose='LOAN_PRINCIPAL')
         or (?='INTEREST' and kind='POSTING' and ((type='INCOME' and normal_balance='CREDIT' and purpose='INCOME') or purpose='CLEARING')))
        """;
    public List<Choice> choices(String institution,String category,String search,int page,UUID selected){
        return jdbc.query("select id,code||' - '||name label,purpose from gl_account where sacco_id=? and active and "+ELIGIBLE+
            " and (?::uuid is null or id=?) and strpos(lower(code||' '||name),?)>0 order by code,id limit 26 offset ?",
            (r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),r.getString("purpose")),institution,category,category,selected,selected,search.toLowerCase(Locale.ROOT),page*25);
    }
    public boolean valid(String institution,UUID id,String category){
        return !jdbc.queryForList("select id from gl_account where sacco_id=? and id=? and active and "+ELIGIBLE+" for share",institution,id,category,category).isEmpty();
    }
    public Optional<Map<String,Object>> product(String institution,UUID product){
        return jdbc.queryForList("select id,principal_gl_account_id,interest_gl_account_id from loan_product_settings where sacco_id=? and id=? for share",institution,product).stream().findFirst();
    }
    public Optional<Map<String,Object>> binding(AppUserPrincipal a,UUID loan){
        return jdbc.queryForList("select * from loan_product_account_binding where loan_id=? and sacco_id=? and station_id=?",loan,a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public void bind(AppUserPrincipal a,UUID loan,UUID product,UUID principal,UUID interest,String source,OffsetDateTime now){
        jdbc.update("insert into loan_product_account_binding(loan_id,sacco_id,station_id,product_id,principal_account_id,interest_account_id,source,linked_by,linked_at) values(?,?,?,?,?,?,?,?,?)",
            loan,a.getSaccoId(),a.getStationId(),product,principal,interest,source,a.getMemberId(),now);
    }
}
