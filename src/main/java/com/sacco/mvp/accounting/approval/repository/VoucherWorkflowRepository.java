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
    public List<Workflow> register(AppUserPrincipal a,List<Type> types,String search,String category,String sort,int days,int page,int size,OffsetDateTime now) {
        String placeholders=String.join(",",Collections.nCopies(types.size(),"?"));
        var args=new ArrayList<Object>(List.<Object>of(a.getSaccoId(),a.getStationId()));types.forEach(t->args.add(t.name()));
        args.add(search.toLowerCase(Locale.ROOT));args.add(search.toLowerCase(Locale.ROOT));String dateFilter="true",categoryFilter=categorySql(category);
        if(days>0){dateFilter="updated_at>=?";args.add(now.minusDays(days));}
        if(category.equals("RECENT_CREATED") || category.equals("RECENT_UPDATED")){categoryFilter=category.equals("RECENT_CREATED")?"created_at>=?":"updated_at>=? and updated_at>created_at";args.add(now.minusDays(7));}
        args.add(size+1);args.add(page*size);
        return jdbc.query("select * from voucher_workflow where sacco_id=? and station_id=? and type in ("+placeholders+") and (strpos(lower(title),?)>0 or strpos(lower(number),?)>0) and "+dateFilter+" and "+categoryFilter+" order by "+sortSql(sort)+",id limit ? offset ?",this::workflow,args.toArray());
    }
    public Map<String,Long> registerCounts(AppUserPrincipal a,List<Type> types,OffsetDateTime now) {
        String placeholders=String.join(",",Collections.nCopies(types.size(),"?"));
        var args=new ArrayList<Object>(List.<Object>of(now.minusDays(7),now.minusDays(14),now.minusDays(7),now.minusDays(7),now.minusDays(7),now.minusDays(7),a.getSaccoId(),a.getStationId()));types.forEach(t->args.add(t.name()));
        return jdbc.query("select approval,validation,posting,count(*) n, count(*) filter(where validation<>'VALIDATED' and created_at>=?) age7, count(*) filter(where validation<>'VALIDATED' and created_at>=? and created_at<?) age14, count(*) filter(where created_at>=?) recent_created,count(*) filter(where updated_at>=? and updated_at>created_at) recent_updated,count(*) filter(where updated_at>=?) recent_total from voucher_workflow where sacco_id=? and station_id=? and type in ("+placeholders+") group by approval,validation,posting",r->{var out=new HashMap<String,Long>();while(r.next()){
            for(String key:List.of(r.getString("approval"),r.getString("validation"),r.getString("posting")))out.merge(key,r.getLong("n"),Long::sum);
            if(r.getString("validation").equals("VALIDATED") && Set.of("NOT_REQUIRED","WORKFLOW_APPROVED","MANUALLY_APPROVED").contains(r.getString("approval")) && r.getString("posting").equals("NOT_POSTED"))out.merge("READY",r.getLong("n"),Long::sum);
            out.merge("ALL",r.getLong("n"),Long::sum);if(!Set.of("INITIATED","REJECTED").contains(r.getString("approval")))out.merge("OTHERS",r.getLong("n"),Long::sum);
            for(String key:List.of("age7","age14","recent_created","recent_updated","recent_total"))out.merge(key,r.getLong(key),Long::sum);
            if(!r.getString("validation").equals("VALIDATED"))out.merge("unfinished",r.getLong("n"),Long::sum);
        }out.put("age15",out.getOrDefault("unfinished",0L)-out.getOrDefault("age7",0L)-out.getOrDefault("age14",0L));return out;},args.toArray());
    }
    public Map<String,Long> warningCounts(AppUserPrincipal a,List<Type> types) {
        var args=new ArrayList<Object>(List.<Object>of(a.getSaccoId(),a.getStationId()));types.forEach(t->args.add(t.name()));
        return jdbc.query("select count(distinct w.id) filter(where issue->>'code' like '%account%' or issue->>'code' like '%Account%' or issue->>'code' like '%Template%') account_errors,count(distinct w.id) filter(where issue->>'code' in ('approval.error.requiredField','voucher.error.date','voucher.error.amount')) missing_details,count(distinct w.id) filter(where lower(issue->>'code') like '%balanc%') unbalanced from voucher_workflow w join voucher_workflow_item i on i.workflow_id=w.id cross join lateral jsonb_array_elements(i.issues) issue where w.sacco_id=? and w.station_id=? and w.type in ("+String.join(",",Collections.nCopies(types.size(),"?"))+")",r->{var out=new HashMap<String,Long>();if(r.next()){out.put("ACCOUNT_ERRORS",r.getLong(1));out.put("MISSING_DETAILS",r.getLong(2));out.put("UNBALANCED",r.getLong(3));}return out;},args.toArray());
    }
    public Map<UUID,String> transactionDates(AppUserPrincipal a,List<Workflow> rows) {
        if(rows.isEmpty())return Map.of();var args=new ArrayList<Object>(List.<Object>of(a.getSaccoId(),a.getStationId()));rows.forEach(w->args.add(w.id()));
        return jdbc.query("select workflow_id,min(form_json->>'effectiveDate') d from voucher_workflow_item where sacco_id=? and station_id=? and workflow_id in ("+String.join(",",Collections.nCopies(rows.size(),"?"))+") group by workflow_id",r->{var out=new HashMap<UUID,String>();while(r.next())out.put(r.getObject(1,UUID.class),Objects.toString(r.getString(2),""));return out;},args.toArray());
    }
    public Set<UUID> approvedWorkflows(AppUserPrincipal a,List<Workflow> rows) {
        if(rows.isEmpty())return Set.of();var args=new ArrayList<Object>(List.<Object>of(a.getSaccoId(),a.getStationId(),a.getMemberId()));rows.forEach(w->args.add(w.id()));
        return new HashSet<>(jdbc.query("select distinct w.id from voucher_workflow w join voucher_workflow_history h on h.workflow_id=w.id and h.round=w.round where w.sacco_id=? and w.station_id=? and h.actor_id=? and h.action='APPROVE' and w.id in ("+String.join(",",Collections.nCopies(rows.size(),"?"))+")",(r,n)->r.getObject(1,UUID.class),args.toArray()));
    }
    private static String sortSql(String sort) {
        String[] parts=sort.split("_");
        String column=switch(parts[0]) {case "number"->"number";case "title"->"title";case "scope"->"scope";case "count"->"item_count";case "total"->"total";case "approval"->"approval";case "validation"->"validation";case "posting"->"posting";case "updated"->"updated_at";case "type"->"type";case "owner"->"(select m.full_name from members m where m.id=voucher_workflow.owner_id)";case "date"->"(select min(i.form_json->>'effectiveDate') from voucher_workflow_item i where i.workflow_id=voucher_workflow.id)";default->throw new Invalid("input");};
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
        if(c.equals("RECENT_CREATED"))return "created_at>=current_timestamp-interval '7 days'";
        if(c.equals("RECENT_UPDATED"))return "updated_at>=current_timestamp-interval '7 days' and updated_at>created_at";
        if(c.equals("READY"))return "validation='VALIDATED' and approval in ('NOT_REQUIRED','WORKFLOW_APPROVED','MANUALLY_APPROVED') and posting='NOT_POSTED'";
        if(Set.of("ACCOUNT_ERRORS","MISSING_DETAILS","UNBALANCED").contains(c)){
            String issue=c.equals("ACCOUNT_ERRORS")?"(issue->>'code' like '%account%' or issue->>'code' like '%Account%' or issue->>'code' like '%Template%')":c.equals("MISSING_DETAILS")?"issue->>'code' in ('approval.error.requiredField','voucher.error.date','voucher.error.amount')":"lower(issue->>'code') like '%balanc%'";
            return "exists(select 1 from voucher_workflow_item i cross join lateral jsonb_array_elements(i.issues) issue where i.workflow_id=voucher_workflow.id and "+issue+")";
        }
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
    public void invalidate(UUID workflow) { jdbc.update("update voucher_workflow_item set plan_hash=null,issues='[]'::jsonb where workflow_id=? and voucher_id is null",workflow); }
    public int nextLine(UUID workflow) {
        return jdbc.queryForObject("select min(n) from generate_series(1,50) n where not exists(select 1 from voucher_workflow_item i where i.workflow_id=? and i.line=n)",Integer.class,workflow);
    }
    public int removeItem(UUID workflow,UUID item) {
        return jdbc.update("delete from voucher_workflow_item where workflow_id=? and id=? and voucher_id is null",workflow,item);
    }
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
        jdbc.update("""
            insert into voucher_workflow_history(id,workflow_id,sacco_id,station_id,action,round,actor_id,comments,recorded_at,context)
            select ?,w.id,w.sacco_id,w.station_id,?,w.round,?,?,?,
             jsonb_build_object('configurationVersion',w.config_version,'assessmentAmount',w.assessment_amount,
              'approval',w.approval,'validation',w.validation,'posting',w.posting,'stages',w.stages,'stage',w.stage,
              'rule',w.rule_name,'autoPost',w.auto_post,
              'vouchers',case when ?='INITIATE' then
               (select jsonb_agg(jsonb_build_object('id',i.id,'form',i.form_json,'planHash',i.plan_hash) order by i.line)
                from voucher_workflow_item i where i.workflow_id=w.id) else null end,
              'documents',case when ?='INITIATE' then
               (select coalesce(jsonb_agg(d.id order by d.id),'[]'::jsonb) from voucher_workflow_document d where d.workflow_id=w.id) else null end)
             from voucher_workflow w where w.id=? and w.sacco_id=? and w.station_id=?
            """,UUID.randomUUID(),action,a.getMemberId(),comments,now,action,action,workflow,a.getSaccoId(),a.getStationId());
    }
    public void beginRound(UUID workflow) { jdbc.update("update voucher_workflow set round=round+1 where id=?",workflow); }
    public void route(UUID workflow,Rule rule) {
        jdbc.update("update voucher_workflow set auto_post=?,model_version=2,rule_name=? where id=?",rule.autoPost(),rule.name(),workflow);
    }
    public Map<Integer,Long> responses(Workflow w) {
        return jdbc.query("select decision_stage,count(*) n from voucher_workflow_history where workflow_id=? and round=? and action='APPROVE' and decision_stage is not null group by decision_stage",r->{var out=new HashMap<Integer,Long>();while(r.next())out.put(r.getInt(1),r.getLong(2));return out;},w.id(),w.round());
    }
    public void decisionStage(UUID workflow,int round,UUID actor,int stage,String comments) {
        // Decisions are inserted once; the stage is supplied by the locked workflow, never the request.
        jdbc.update("insert into voucher_workflow_history(id,workflow_id,sacco_id,station_id,action,round,actor_id,comments,recorded_at,context,decision_stage) select ?,id,sacco_id,station_id,'APPROVE',?,?,?,updated_at,jsonb_build_object('configurationVersion',config_version,'assessmentAmount',assessment_amount,'approval',approval,'validation',validation,'posting',posting,'stages',stages,'stage',stage,'rule',rule_name,'autoPost',auto_post),? from voucher_workflow where id=?",UUID.randomUUID(),round,actor,comments,stage,workflow);
    }
    public List<UUID> roleCandidates(AppUserPrincipal a,com.sacco.mvp.domain.Position role) {
        return jdbc.query("select m.id from members m where m.sacco_id=? and m.station_id=? and m.status='ACTIVE' and (m.position=? or exists(select 1 from member_staff_roles r where r.member_id=m.id and r.role_name=?)) order by m.id limit 501",(r,n)->r.getObject(1,UUID.class),a.getSaccoId(),a.getStationId(),role.name(),role.name());
    }
    public List<UUID> automaticCandidates() {
        return jdbc.query("select id from voucher_workflow where auto_post and approval='WORKFLOW_APPROVED' and validation='VALIDATED' and posting='NOT_POSTED' order by updated_at,id limit 10",(r,n)->r.getObject(1,UUID.class));
    }
    public Optional<AutomaticRequest> automaticRequest(UUID id) {
        return jdbc.query("select id,owner_id,sacco_id,station_id,version from voucher_workflow where id=? and auto_post and approval='WORKFLOW_APPROVED' and validation='VALIDATED' and posting='NOT_POSTED'",(r,n)->new AutomaticRequest(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getInt(5)),id).stream().findFirst();
    }
    public void automaticFailed(UUID id,int version,String error,OffsetDateTime now) {
        int changed=jdbc.update("update voucher_workflow set posting='FAILED',failure=?,version=version+1,updated_at=? where id=? and version=? and auto_post and approval='WORKFLOW_APPROVED' and posting='NOT_POSTED'",error,now,id,version);
        if(changed==1)jdbc.update("insert into voucher_workflow_history(id,workflow_id,sacco_id,station_id,action,round,actor_id,comments,recorded_at,context) select ?,id,sacco_id,station_id,'POSTING_FAILED',round,owner_id,'',?,jsonb_build_object('automatic',true,'failure',failure,'configurationVersion',config_version) from voucher_workflow where id=?",UUID.randomUUID(),now,id);
    }
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
        return new Workflow(r.getObject("id",UUID.class),r.getString("number"),Type.valueOf(r.getString("type")),r.getString("scope"),r.getString("title"),r.getObject("owner_id",UUID.class),r.getInt("version"),Approval.valueOf(r.getString("approval")),r.getString("validation"),r.getString("posting"),r.getBigDecimal("total"),r.getBigDecimal("assessment_amount"),r.getInt("item_count"),r.getInt("stage"),r.getInt("round"),r.getInt("config_version"),read(r.getString("stages"),new TypeReference<List<Stage>>(){}),r.getBoolean("force_allowed"),r.getString("failure"),r.getObject("created_at",OffsetDateTime.class),r.getObject("updated_at",OffsetDateTime.class),r.getBoolean("auto_post"),r.getInt("model_version"),r.getString("rule_name"));
    }
    private Item item(ResultSet r,int n)throws SQLException {
        return new Item(r.getObject("id",UUID.class),r.getObject("workflow_id",UUID.class),r.getInt("line"),r.getInt("version"),read(r.getString("form_json"),Form.class),read(r.getString("issues"),new TypeReference<List<Issue>>(){}),r.getString("plan_hash"),r.getObject("voucher_id",UUID.class));
    }
    public String write(Object value) { return json.writeValueAsString(value); }
    private <T>T read(String value,Class<T> type) { return json.readValue(value,type); }
    private <T>T read(String value,TypeReference<T> type) { return json.readValue(value,type); }
}
