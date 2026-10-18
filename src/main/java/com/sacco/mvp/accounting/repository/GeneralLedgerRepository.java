package com.sacco.mvp.accounting.repository;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.dto.AccountVersionDtos.*;
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
    private static final tools.jackson.databind.ObjectMapper JSON=tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();

    public boolean hasInstitutionHistory(String institution) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from gl_account where sacco_id=?) or exists(select 1 from accounting_period where sacco_id=?) or exists(select 1 from gl_journal where sacco_id=?) or exists(select 1 from gl_account_version where sacco_id=?)",Boolean.class,institution,institution,institution,institution));
    }
    public boolean hasMemberHistory(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from gl_account where maker_id=?) or exists(select 1 from accounting_period where created_by=? or closed_by=?) or exists(select 1 from gl_journal where maker_id=? or checker_id=?) or exists(select 1 from accounting_archive.gl_source_cancellation where checker_id=?) or exists(select 1 from gl_account_version where creator_id=? or reviewer_id=?) or exists(select 1 from gl_account_approval_policy where created_by=?) or exists(select 1 from gl_account_version_event where actor_id=?)",Boolean.class,id,id,id,id,id,id,id,id,id,id));
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
        return jdbc.query("select lpad(n::text,6,'0') code from generate_series(?,?,?) n where not exists(select 1 from gl_account where sacco_id=? and code=lpad(n::text,6,'0')) and not exists(select 1 from gl_account_version where sacco_id=? and operation='CREATE' and state in ('DRAFT','PENDING') and snapshot->>'code'=lpad(n::text,6,'0')) order by n limit 1",
            (r,n)->r.getString("code"),start,end,step,institution,institution).stream().findFirst();
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
    public Policy accountPolicy(String institution) {
        return jdbc.query("select * from gl_account_approval_policy where sacco_id=? order by version desc limit 1",
            (r,n)->new Policy(r.getInt("version"),r.getBoolean("create_automatic"),r.getBoolean("update_automatic"),r.getBoolean("deactivate_automatic"),r.getBoolean("reactivate_automatic")),institution)
            .stream().findFirst().orElse(new Policy(0,true,true,true,true));
    }
    public void saveAccountPolicy(String institution,Policy p,String reason,UUID actor,OffsetDateTime now) {
        jdbc.update("insert into gl_account_approval_policy values(?,?,?,?,?,?,?,?,?)",institution,p.version(),p.createAutomatic(),p.updateAutomatic(),p.deactivateAutomatic(),p.reactivateAutomatic(),reason,actor,now);
    }
    private static final String VERSION_SELECT="select v.*,m.full_name creator_name,r.full_name reviewer_name from gl_account_version v join members m on m.id=v.creator_id left join members r on r.id=v.reviewer_id";
    public Optional<Version> version(String institution,UUID id) {
        return jdbc.query(VERSION_SELECT+" where v.sacco_id=? and v.id=?",this::version,institution,id).stream().findFirst();
    }
    public Optional<Version> versionRequest(String institution,UUID key) {
        return jdbc.query(VERSION_SELECT+" where v.sacco_id=? and v.request_key=?",this::version,institution,key).stream().findFirst();
    }
    public Optional<Version> currentVersion(String institution,UUID account) {
        return jdbc.query(VERSION_SELECT+" join gl_account a on a.current_version_id=v.id and a.sacco_id=v.sacco_id where a.sacco_id=? and a.id=?",this::version,institution,account).stream().findFirst();
    }
    public boolean openVersion(String institution,UUID account) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from gl_account_version where sacco_id=? and account_id=? and state in ('DRAFT','PENDING'))",Boolean.class,institution,account));
    }
    public int nextVersion(String institution,UUID account) {
        return jdbc.queryForObject("select coalesce(max(number),0)+1 from gl_account_version where sacco_id=? and account_id=?",Integer.class,institution,account);
    }
    public void createVersion(String institution,Version v) {
        jdbc.update("""
            insert into gl_account_version(id,sacco_id,account_id,number,base_id,operation,state,before_snapshot,snapshot,
              creator_id,station_id,reason,created_at,request_key,request_hash) values(?,?,?,?,?,?,'DRAFT',?::jsonb,?::jsonb,?,?,?,?,?,?)
            """,v.id(),institution,v.accountId(),v.number(),v.baseId(),v.operation(),json(v.before()),json(v.proposed()),v.creator(),v.branch(),v.reason(),v.createdAt(),v.requestKey(),v.requestHash());
    }
    public void saveVersionDraft(String institution,Version v,Account proposed,String reason) {
        jdbc.update("update gl_account_version set snapshot=?::jsonb,reason=?,revision=revision+1 where sacco_id=? and id=? and state='DRAFT'",json(proposed),reason,institution,v.id());
    }
    public void decideVersion(String institution,Version v,String state,int policy,String mode,UUID reviewer,String reason,OffsetDateTime now) {
        jdbc.update("""
            update gl_account_version set state=?,policy_version=?,mode=?,reviewer_id=?,decision_reason=?,
             submitted_at=case when ? in ('APPROVED','PENDING','REJECTED') then coalesce(submitted_at,?) else submitted_at end,
             decided_at=case when ? in ('APPROVED','REJECTED','WITHDRAWN') then ? else null end,revision=revision+1 where sacco_id=? and id=?
            """,state,policy,mode,reviewer,reason,state,now,state,now,institution,v.id());
    }
    public void applyAccountVersion(String institution,Version v) {
        var a=v.proposed();
        if(v.operation().equals("CREATE"))jdbc.update("""
            insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,parent_id,active,maker_id,created_at,name_sw,description,current_version_id)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,a.id(),institution,a.code(),a.name(),a.type(),a.normalBalance(),a.kind(),a.purpose(),a.parentId(),a.active(),v.creator(),v.createdAt(),a.nameSw(),a.description(),v.id());
        else jdbc.update("update gl_account set name=?,name_sw=?,description=?,active=?,current_version_id=? where sacco_id=? and id=?",
            a.name(),a.nameSw(),a.description(),a.active(),v.id(),institution,a.id());
    }
    public List<Version> accountVersions(String institution,UUID account,int page,UUID owner,String branch,boolean all) {
        return jdbc.query(VERSION_SELECT+" where v.sacco_id=? and v.account_id=? and (? or v.state in ('BASELINE','APPROVED') or (v.creator_id=? and v.station_id=?)) order by v.number desc limit 26 offset ?",this::version,institution,account,all,owner,branch,page*25);
    }
    public List<Version> versionRegister(String institution,String branch,UUID owner,boolean all,String state,String search,String sort,String direction,int page,int size) {
        String column=switch(sort){case "code"->"v.snapshot->>'code'";case "name"->"v.snapshot->>'name'";case "number"->"v.number";case "operation"->"v.operation";case "state"->"v.state";case "creator"->"m.full_name";case "created"->"v.created_at";default->throw new IllegalArgumentException("accounting.error.versionInput");};
        String order=switch(direction){case "asc"->" asc";case "desc"->" desc";default->throw new IllegalArgumentException("accounting.error.versionInput");};
        return jdbc.query(VERSION_SELECT+" where v.sacco_id=? and (? or (v.station_id=? and v.creator_id=?)) and v.operation<>'BASELINE' and (?='' or v.state=?) and (strpos(lower(v.snapshot->>'code'),?)>0 or strpos(lower(v.snapshot->>'name'),?)>0) order by "+column+order+",v.id"+order+" limit ? offset ?",this::version,institution,all,branch,owner,state,state,search.toLowerCase(java.util.Locale.ROOT),search.toLowerCase(java.util.Locale.ROOT),size+1,page*size);
    }
    public void accountVersionEvent(String institution,Version v,String action,UUID actor,String branch,String reason,OffsetDateTime now) {
        jdbc.update("insert into gl_account_version_event values(?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?)",UUID.randomUUID(),v.id(),institution,action,actor,branch,reason,v.state(),v.policyVersion(),v.mode(),v.revision(),json(v.before()),json(v.proposed()),now);
    }
    public List<Event> accountVersionEvents(String institution,UUID id,int page) {
        return jdbc.query("select e.*,m.full_name from gl_account_version_event e join members m on m.id=e.actor_id where e.sacco_id=? and e.version_id=? order by e.revision,e.id limit 26 offset ?",
            (r,n)->new Event(r.getObject("id",UUID.class),r.getString("action"),r.getString("full_name"),r.getString("reason"),r.getString("state"),r.getInt("policy_version"),r.getString("mode"),r.getObject("recorded_at",OffsetDateTime.class)),institution,id,page*25);
    }
    public static String json(Object value) {return value==null?null:JSON.writeValueAsString(value);}
    private Version version(ResultSet r,int n) throws SQLException {
        return new Version(r.getObject("id",UUID.class),r.getObject("account_id",UUID.class),r.getInt("number"),r.getObject("base_id",UUID.class),r.getString("operation"),r.getString("state"),
            r.getString("before_snapshot")==null?null:JSON.readValue(r.getString("before_snapshot"),Account.class),JSON.readValue(r.getString("snapshot"),Account.class),
            r.getObject("creator_id",UUID.class),r.getString("creator_name"),r.getString("station_id"),r.getString("reason"),r.getInt("revision"),r.getInt("policy_version"),r.getString("mode"),
            r.getObject("reviewer_id",UUID.class),r.getString("reviewer_name"),r.getString("decision_reason"),r.getObject("created_at",OffsetDateTime.class),r.getObject("submitted_at",OffsetDateTime.class),r.getObject("decided_at",OffsetDateTime.class),r.getObject("request_key",UUID.class),r.getString("request_hash"));
    }
    private Account account(ResultSet r,int n) throws SQLException {
        return new Account(r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("type"),r.getString("normal_balance"),r.getString("kind"),r.getString("purpose"),r.getObject("parent_id",UUID.class),r.getBoolean("active"),r.getString("name_sw"),r.getString("description"));
    }
}
