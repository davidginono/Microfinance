package com.sacco.mvp.accounting.approval.repository;

import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.*;
import com.sacco.mvp.accounting.dto.VoucherDtos.Form;
import com.sacco.mvp.accounting.dto.VoucherDtos.Type;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class VoucherWorkflowRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public void policyLock(AppUserPrincipal a, boolean exclusive) {
        jdbc.queryForList("select pg_advisory_xact_lock"+(exclusive?"":"_shared")+"(hashtextextended(?,0))", "voucher-approval:"+a.getSaccoId()+":"+a.getStationId());
    }
    public Configuration configuration(AppUserPrincipal a, Type type) {
        return jdbc.query("select definition from voucher_approval_configuration where sacco_id=? and station_id=? and type=?",
            (r,n)->read(r.getString(1),Configuration.class),a.getSaccoId(),a.getStationId(),type.name()).stream().findFirst()
            .orElse(new Configuration(type,0,false,false,false,List.of()));
    }
    public void configure(AppUserPrincipal a, Configuration c, OffsetDateTime now) {
        jdbc.update("""
            insert into voucher_approval_configuration(sacco_id,station_id,type,version,definition,updated_by,updated_at)
            values(?,?,?,?,?::jsonb,?,?) on conflict(sacco_id,station_id,type) do update set version=excluded.version,
            definition=excluded.definition,updated_by=excluded.updated_by,updated_at=excluded.updated_at
            """,a.getSaccoId(),a.getStationId(),c.type().name(),c.version(),write(c),a.getMemberId(),now);
    }
    public Optional<Workflow> find(AppUserPrincipal a, UUID id, boolean lock) {
        return jdbc.query("select * from voucher_workflow where id=? and sacco_id=? and station_id=?"+(lock?" for update":""),this::workflow,id,a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public void requestLock(AppUserPrincipal a,UUID key) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","voucher-workflow-request:"+a.getSaccoId()+":"+a.getStationId()+":"+key);
    }
    public Optional<Workflow> request(AppUserPrincipal a, UUID key) {
        return jdbc.query("select * from voucher_workflow where request_key=? and sacco_id=? and station_id=?",this::workflow,key,a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public Workflow create(AppUserPrincipal a, UUID key, Type type, String scope, String title, OffsetDateTime now) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into voucher_workflow(id,number,sacco_id,station_id,type,scope,title,owner_id,request_key,created_at,updated_at)
            values(?,'AW-'||lpad(nextval('voucher_workflow_number_seq')::text,10,'0'),?,?,?,?,?,?,?,?,?)
            """,id,a.getSaccoId(),a.getStationId(),type.name(),scope,title,a.getMemberId(),key,now,now);
        return find(a,id,false).orElseThrow();
    }
    public List<Workflow> list(AppUserPrincipal a, Type type, String search, String category, String sort, int page, int size) {
        String where=categorySql(category);
        String order=sortSql(sort);
        return jdbc.query("select * from voucher_workflow where sacco_id=? and station_id=? and type=? and (strpos(lower(title),?)>0 or strpos(lower(number),?)>0) and "+where+" order by "+order+",id limit ? offset ?",
            this::workflow,a.getSaccoId(),a.getStationId(),type.name(),search.toLowerCase(Locale.ROOT),search.toLowerCase(Locale.ROOT),size+1,page*size);
    }
    private static String sortSql(String sort) {
        String[] parts=sort.split("_");
        String column=switch(parts[0]) {case "number"->"number";case "title"->"title";case "scope"->"scope";case "count"->"item_count";case "total"->"total";case "approval"->"approval";case "validation"->"validation";case "posting"->"posting";case "updated"->"updated_at";default->throw new Invalid("input");};
        if(parts.length!=2 || !Set.of("asc","desc").contains(parts[1]))throw new Invalid("input");
        return column+" "+parts[1];
    }
    public Map<String,Long> counts(AppUserPrincipal a, Type type) {
        return jdbc.query("""
            select approval,validation,posting,count(*) n from voucher_workflow where sacco_id=? and station_id=? and type=?
            group by approval,validation,posting
            """,r->{var result=new HashMap<String,Long>();while(r.next()) {
                for(String key:List.of(r.getString("approval"),r.getString("validation"),r.getString("posting"))) result.merge(key,r.getLong("n"),Long::sum);
                result.merge("ALL",r.getLong("n"),Long::sum);
                if(!Set.of("INITIATED","REJECTED").contains(r.getString("approval")))result.merge("OTHERS",r.getLong("n"),Long::sum);
            }return result;},a.getSaccoId(),a.getStationId(),type.name());
    }
    private static String categorySql(String c) {
        if(c.isEmpty())return "true";
        if(c.equals("OTHERS"))return "approval not in ('INITIATED','REJECTED')";
        if(Set.of("INCOMPLETE","NOT_VALIDATED","VALIDATED","ERROR").contains(c))return "validation='"+c+"'";
        if(Set.of("NOT_POSTED","FAILED","POSTED").contains(c))return "posting='"+c+"'";
        return "approval='"+Approval.valueOf(c).name()+"'";
    }
    public List<Item> items(UUID workflow) {
        return jdbc.query("select * from voucher_workflow_item where workflow_id=? order by line,id limit 51",this::item,workflow);
    }
    public Optional<Item> item(UUID workflow, UUID id) {
        return jdbc.query("select * from voucher_workflow_item where workflow_id=? and id=?",this::item,workflow,id).stream().findFirst();
    }
    public Optional<UUID> workflowForItem(AppUserPrincipal a, UUID key) {
        return jdbc.query("select workflow_id from voucher_workflow_item where id=? and sacco_id=? and station_id=?",(r,n)->r.getObject(1,UUID.class),key,a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public void save(Workflow w, AppUserPrincipal a, UUID id, int line, Form form, boolean fresh) {
        if(fresh)jdbc.update("insert into voucher_workflow_item(id,workflow_id,sacco_id,station_id,line,form_json) values(?,?,?,?,?,?::jsonb)",id,w.id(),a.getSaccoId(),a.getStationId(),line,write(form));
        else jdbc.update("update voucher_workflow_item set form_json=?::jsonb,version=version+1 where workflow_id=? and id=? and voucher_id is null",write(form),w.id(),id);
    }
    public void delete(UUID workflow,UUID item) { jdbc.update("delete from voucher_workflow_item where workflow_id=? and id=? and voucher_id is null",workflow,item); }
    public void invalidate(UUID workflow) { jdbc.update("update voucher_workflow_item set plan_hash=null,issues='[]'::jsonb where workflow_id=? and voucher_id is null",workflow); }
    public void issues(UUID workflow, UUID item, List<Issue> issues, String hash) { jdbc.update("update voucher_workflow_item set issues=?::jsonb,plan_hash=? where workflow_id=? and id=?",write(issues),hash,workflow,item); }
    public void update(Workflow w, Approval approval, String validation, String posting, BigDecimal total, BigDecimal amount,
                       int count, int stage, int configVersion, List<Stage> stages, boolean force, String failure, OffsetDateTime now) {
        jdbc.update("""
            update voucher_workflow set approval=?,validation=?,posting=?,total=?,assessment_amount=?,item_count=?,stage=?,
            config_version=?,stages=?::jsonb,force_allowed=?,failure=?,version=version+1,updated_at=? where id=?
            """,approval.name(),validation,posting,total,amount,count,stage,configVersion,write(stages),force,failure,now,w.id());
    }
    public void link(UUID workflow,UUID item,UUID voucher) { jdbc.update("update voucher_workflow_item set voucher_id=? where workflow_id=? and id=?",voucher,workflow,item); }
    public void history(AppUserPrincipal a,UUID workflow,String action,String comments,OffsetDateTime now) {
        jdbc.update("insert into voucher_workflow_history(id,workflow_id,sacco_id,station_id,action,round,actor_id,comments,recorded_at,context) select ?,id,sacco_id,station_id,?,round,?,?,?,jsonb_build_object('configurationVersion',config_version,'assessmentAmount',assessment_amount,'approval',approval,'validation',validation,'posting',posting,'stages',stages,'stage',stage) from voucher_workflow where id=? and sacco_id=? and station_id=?",UUID.randomUUID(),action,a.getMemberId(),comments,now,workflow,a.getSaccoId(),a.getStationId());
    }
    public void beginRound(UUID workflow) { jdbc.update("update voucher_workflow set round=round+1 where id=?",workflow); }
    public boolean alreadyApproved(Workflow w,UUID actor) {return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from voucher_workflow_history where workflow_id=? and round=? and actor_id=? and action='APPROVE')",Boolean.class,w.id(),w.round(),actor));}
    public List<History> history(UUID workflow) {
        return jdbc.query("select h.*,m.full_name from voucher_workflow_history h join members m on m.id=h.actor_id where workflow_id=? order by recorded_at desc,h.id limit 100",
            (r,n)->new History(r.getObject("id",UUID.class),r.getString("action"),r.getString("full_name"),r.getString("comments"),r.getObject("recorded_at",OffsetDateTime.class)),workflow);
    }
    public List<Document> documents(UUID workflow) {
        return jdbc.query("select id,filename,media_type from voucher_workflow_document where workflow_id=? order by uploaded_at,id limit 10",
            (r,n)->new Document(r.getObject(1,UUID.class),r.getString(2),r.getString(3),null),workflow);
    }
    public Optional<Document> document(UUID workflow,UUID id) {
        return jdbc.query("select id,filename,media_type,content from voucher_workflow_document where workflow_id=? and id=?",
            (r,n)->new Document(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getBytes(4)),workflow,id).stream().findFirst();
    }
    public void document(UUID workflow,AppUserPrincipal a,UUID id,String filename,String type,byte[] bytes,OffsetDateTime now) {
        jdbc.update("insert into voucher_workflow_document(id,workflow_id,sacco_id,station_id,filename,media_type,content,uploaded_by,uploaded_at) values(?,?,?,?,?,?,?,?,?)",id,workflow,a.getSaccoId(),a.getStationId(),filename,type,bytes,a.getMemberId(),now);
    }
    private Workflow workflow(ResultSet r,int n)throws SQLException {
        return new Workflow(r.getObject("id",UUID.class),r.getString("number"),Type.valueOf(r.getString("type")),r.getString("scope"),r.getString("title"),r.getObject("owner_id",UUID.class),r.getInt("version"),Approval.valueOf(r.getString("approval")),r.getString("validation"),r.getString("posting"),r.getBigDecimal("total"),r.getBigDecimal("assessment_amount"),r.getInt("item_count"),r.getInt("stage"),r.getInt("round"),r.getInt("config_version"),read(r.getString("stages"),new TypeReference<List<Stage>>(){}),r.getBoolean("force_allowed"),r.getString("failure"),r.getObject("created_at",OffsetDateTime.class),r.getObject("updated_at",OffsetDateTime.class));
    }
    private Item item(ResultSet r,int n)throws SQLException {
        return new Item(r.getObject("id",UUID.class),r.getObject("workflow_id",UUID.class),r.getInt("line"),r.getInt("version"),read(r.getString("form_json"),Form.class),read(r.getString("issues"),new TypeReference<List<Issue>>(){}),r.getString("plan_hash"),r.getObject("voucher_id",UUID.class));
    }
    public String write(Object value) { return json.writeValueAsString(value); }
    private <T>T read(String value,Class<T> type) { return json.readValue(value,type); }
    private <T>T read(String value,TypeReference<T> type) { return json.readValue(value,type); }
}
