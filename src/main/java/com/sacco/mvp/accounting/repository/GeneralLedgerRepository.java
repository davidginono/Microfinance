package com.sacco.mvp.accounting.repository;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GeneralLedgerRepository {
    private final JdbcTemplate jdbc;

    public boolean hasInstitutionHistory(String institution) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from gl_account where sacco_id=?) or exists(select 1 from accounting_period where sacco_id=?) or exists(select 1 from gl_journal where sacco_id=?)",Boolean.class,institution,institution,institution));
    }
    public boolean hasMemberHistory(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from gl_account where maker_id=?) or exists(select 1 from accounting_period where created_by=? or closed_by=?) or exists(select 1 from gl_journal where maker_id=? or checker_id=?) or exists(select 1 from accounting_archive.gl_source_cancellation where checker_id=?)",Boolean.class,id,id,id,id,id,id));
    }
    public void lockAccounts(String institution) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", "GL_SETUP/" + institution);
    }
    public List<Account> accounts(String institution, int offset, int limit) {
        return jdbc.query("select * from gl_account where sacco_id=? order by code,id limit ? offset ?", this::account, institution,limit,offset);
    }
    public List<AccountRow> chart(String institution,AccountFilter filter,int offset,int limit) {
        return chart(institution,filter,offset,limit,"code","asc");
    }
    public List<AccountRow> chart(String institution,AccountFilter filter,int offset,int limit,String sort,String direction) {
        String column=switch(sort) {
            case "code" -> "a.code"; case "name" -> "a.name"; case "parent" -> "p.name";
            case "type" -> "a.type"; case "kind" -> "a.kind"; case "normalBalance" -> "a.normal_balance";
            case "status" -> "a.active"; default -> throw new IllegalArgumentException("filter");
        };
        String order=switch(direction) { case "asc" -> " asc"; case "desc" -> " desc"; default -> throw new IllegalArgumentException("filter"); };
        var args=new java.util.ArrayList<Object>();args.add(institution);
        String where=" where a.sacco_id=?";
        if(filter.parentId()!=null){where+=" and a.parent_id=?";args.add(filter.parentId());}
        else if(filter.search().isEmpty() && filter.type().isEmpty() && filter.kind().isEmpty() && filter.state().isEmpty())where+=" and a.parent_id is null";
        if(!filter.search().isEmpty()) {
            where+=" and (strpos(lower(a.code),?)>0 or strpos(lower(a.name),?)>0 or strpos(lower(coalesce(a.name_sw,'')),?)>0)";
            String search=filter.search().toLowerCase(java.util.Locale.ROOT);args.add(search);args.add(search);args.add(search);
        }
        if(!filter.type().isEmpty()){where+=" and a.type=?";args.add(filter.type());}
        if(!filter.kind().isEmpty()){where+=" and a.kind=?";args.add(filter.kind());}
        if(!filter.state().isEmpty()){where+=" and a.active=?";args.add("ACTIVE".equals(filter.state()));}
        args.add(limit);args.add(offset);
        return jdbc.query("select a.*,p.code parent_code,p.name parent_name,p.name_sw parent_name_sw from gl_account a left join gl_account p on p.id=a.parent_id and p.sacco_id=a.sacco_id"+where+" order by "+column+order+",a.id"+order+" limit ? offset ?",
            (r,n)->new AccountRow(account(r,n),r.getString("parent_code"),r.getString("parent_name"),r.getString("parent_name_sw")),args.toArray());
    }
    public List<Account> chartParents(String institution,boolean groups,String search,int offset,int limit) {
        String pattern=groups?"^[1-5]([0-9]0000)$":"^[1-5][1-9][1-9]000$";
        String term=search.toLowerCase(java.util.Locale.ROOT);
        return jdbc.query("select * from gl_account where sacco_id=? and active and kind='HEADING' and code ~ ? and (strpos(lower(code),?)>0 or strpos(lower(name),?)>0 or strpos(lower(coalesce(name_sw,'')),?)>0) order by code,id limit ? offset ?",
            this::account,institution,pattern,term,term,term,limit,offset);
    }
    public List<Account> chartPath(String institution,UUID id) {
        return jdbc.query("with recursive path as (select a.*,0 depth from gl_account a where a.sacco_id=? and a.id=? union all select a.*,p.depth+1 from gl_account a join path p on a.id=p.parent_id and a.sacco_id=p.sacco_id where p.depth<16) select * from path order by depth desc limit 17",
            this::account,institution,id);
    }
    public Optional<String> availableChartCode(String institution,int start,int end,int step) {
        return jdbc.query("select lpad(n::text,6,'0') code from generate_series(?,?,?) n where not exists(select 1 from gl_account where sacco_id=? and code=lpad(n::text,6,'0')) order by n limit 1",
            (r,n)->r.getString("code"),start,end,step,institution).stream().findFirst();
    }
    public boolean activeChildren(String institution,UUID parent) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from gl_account where sacco_id=? and parent_id=? and active)",Boolean.class,institution,parent));
    }
    public void reactivate(String institution,UUID id) {jdbc.update("update gl_account set active=true where sacco_id=? and id=?",institution,id);}
    public Optional<Account> account(String institution, UUID id) {
        return jdbc.query("select * from gl_account where sacco_id=? and id=?",this::account,institution,id).stream().findFirst();
    }
    public Optional<Account> accountCode(String institution,String code) {
        return jdbc.query("select * from gl_account where sacco_id=? and code=?",this::account,institution,code).stream().findFirst();
    }
    public void createAccount(String institution, UUID id, AccountCommand c, UUID maker, OffsetDateTime now) {
        jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,parent_id,maker_id,created_at,name_sw,description) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id,institution,c.code(),c.name(),c.type(),c.normalBalance(),c.kind(),c.purpose(),c.parentId(),maker,now,c.nameSw(),c.description());
    }
    public void deactivate(String institution, UUID id) {
        jdbc.update("update gl_account set active=false where sacco_id=? and id=?",institution,id);
    }
    private Account account(ResultSet r,int n) throws SQLException {
        return new Account(r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("type"),r.getString("normal_balance"),r.getString("kind"),r.getString("purpose"),r.getObject("parent_id",UUID.class),r.getBoolean("active"),r.getString("name_sw"),r.getString("description"));
    }
}
