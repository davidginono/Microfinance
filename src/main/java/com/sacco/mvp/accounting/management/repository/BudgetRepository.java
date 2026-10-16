package com.sacco.mvp.accounting.management.repository;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class BudgetRepository {
    private final JdbcTemplate jdbc;
    public void lockRequest(AppUserPrincipal a,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","BUDGET_REQUEST/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);}
    private static final String HEAD="""
        select b.*,a.code,a.name,m.full_name from account_budget b
        join gl_account a on a.id=b.account_id and a.sacco_id=b.sacco_id
        join members m on m.id=b.recorded_by
        """;
    public void lock(AppUserPrincipal a,UUID account,int year){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","BUDGET/"+a.getSaccoId()+"/"+a.getStationId()+"/"+account+"/"+year);}
    public Optional<Budget> find(AppUserPrincipal a,UUID id){return jdbc.query(HEAD+" where b.sacco_id=? and b.station_id=? and b.id=?",this::read,a.getSaccoId(),a.getStationId(),id).stream().findFirst();}
    public Optional<Budget> latest(AppUserPrincipal a,UUID account,int year){return jdbc.query(HEAD+" where b.sacco_id=? and b.station_id=? and b.account_id=? and b.budget_year=? order by b.revision desc limit 1",this::read,a.getSaccoId(),a.getStationId(),account,year).stream().findFirst();}
    public Optional<UUID> retry(AppUserPrincipal a,UUID key,String hash){return jdbc.query("select id,payload_hash=? and recorded_by=? same from account_budget where sacco_id=? and station_id=? and request_key=?",(r,n)->{if(!r.getBoolean("same"))throw new IllegalArgumentException("management.error.retry");return r.getObject("id",UUID.class);},hash,a.getMemberId(),a.getSaccoId(),a.getStationId(),key).stream().findFirst();}
    public List<Budget> list(AppUserPrincipal a,int year,int page,String sort,String direction){
        String column=Map.of("code","a.code","account","a.name","date","b.budget_date","year","b.budget_year","revision","b.revision","total","(select sum(n) from unnest(b.amounts) n)","recordedBy","m.full_name").get(sort);
        if(column==null || !Set.of("asc","desc").contains(direction))throw new IllegalArgumentException("management.error.parameters");
        return jdbc.query(HEAD+" where b.sacco_id=? and b.station_id=? and b.budget_year=? and not exists(select 1 from account_budget newer where newer.replaces_id=b.id) order by "+column+" "+direction+",b.id limit 26 offset ?",this::read,a.getSaccoId(),a.getStationId(),year,page*25);
    }
    /** Reports retain historical money accounts; new budgets require active accounts. */
    public List<Choice> accounts(AppUserPrincipal a,String search,int page,boolean money){return jdbc.query("select id,code,name from gl_account where sacco_id=? and (? or active) and kind='POSTING' and (?=false or purpose in ('CASH','BANK','MOBILE_MONEY')) and strpos(lower(code||' '||name),?)>0 order by code,id limit 26 offset ?",(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),a.getSaccoId(),money,money,search.toLowerCase(Locale.ROOT),page*25);}
    public Optional<Choice> account(AppUserPrincipal a,UUID id,boolean money){return jdbc.query("select id,code,name from gl_account where sacco_id=? and id=? and (? or active) and kind='POSTING' and (?=false or purpose in ('CASH','BANK','MOBILE_MONEY'))",(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),a.getSaccoId(),id,money,money).stream().findFirst();}
    public void insert(AppUserPrincipal a,UUID id,BudgetForm f,int revision,String hash,OffsetDateTime now){
        jdbc.update(connection->{var s=connection.prepareStatement("insert into account_budget(id,sacco_id,station_id,account_id,budget_year,budget_date,revision,request_key,payload_hash,amounts,recorded_by,recorded_at,replaces_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?)");
            Object[] values={id,a.getSaccoId(),a.getStationId(),f.getAccountId(),f.getYear(),f.getDate(),revision,f.getRequestKey(),hash,connection.createArrayOf("numeric",f.getAmounts().toArray()),a.getMemberId(),now,f.getReplacesId()};
            for(int n=0;n<values.length;n++)s.setObject(n+1,values[n]);return s;});
    }
    private Budget read(ResultSet r,int n)throws SQLException {
        var amounts=Arrays.stream((Object[])r.getArray("amounts").getArray()).map(v->(BigDecimal)v).toList();
        return new Budget(r.getObject("id",UUID.class),r.getObject("account_id",UUID.class),r.getString("code"),r.getString("name"),r.getObject("budget_date",LocalDate.class),r.getInt("budget_year"),r.getInt("revision"),amounts,amounts.stream().reduce(BigDecimal.ZERO,BigDecimal::add),r.getString("full_name"),r.getObject("replaces_id",UUID.class));
    }
}
