package com.sacco.mvp.accounting.repository;

import com.sacco.mvp.accounting.dto.AccountingLibraryDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class AccountingCodeLibraryRepository {
    private final JdbcTemplate jdbc;
    private static final String ACTIVITY="select id,code,name,name_sw,description,active from gl_activity";
    private static final String TRANSACTION="select t.id,t.activity_id,a.code activity_code,a.name activity_name,a.name_sw activity_name_sw,t.code,t.name,t.name_sw,t.description,t.source_event,t.active,t.revision,t.current_template_id from gl_transaction_code t join gl_activity a on a.id=t.activity_id and a.sacco_id=t.sacco_id";
    public List<Activity> activities(String institution,String search,String state,int offset) {
        return jdbc.query(ACTIVITY+" where sacco_id=? and (strpos(lower(code),?)>0 or strpos(lower(name),?)>0 or strpos(lower(coalesce(name_sw,'')),?)>0) and (?='' or active=(?='ACTIVE')) order by code,id limit 26 offset ?",
            this::activity,institution,search,search,search,state,state,offset);
    }
    public Optional<Activity> activity(String institution,UUID id,boolean lock) {
        return jdbc.query(ACTIVITY+" where sacco_id=? and id=?"+(lock?" for update":""),this::activity,institution,id).stream().findFirst();
    }
    public Optional<Activity> activityByCode(String institution,String code) {
        return jdbc.query(ACTIVITY+" where sacco_id=? and code=?",this::activity,institution,code).stream().findFirst();
    }
    public List<TransactionCode> transactionRegister(String institution,UUID activity,String search,String state,String sourceEvent,int offset) {
        String parent=activity==null?"":" and t.activity_id=?";
        var args=new ArrayList<Object>();args.add(institution);if(activity!=null)args.add(activity);
        Collections.addAll(args,search,search,search,state,state,sourceEvent,sourceEvent,offset);
        return jdbc.query(TRANSACTION+" where t.sacco_id=?"+parent+" and (strpos(lower(t.code),?)>0 or strpos(lower(t.name),?)>0 or strpos(lower(coalesce(t.name_sw,'')),?)>0) and (?='' or t.active=(?='ACTIVE')) and (?='' or t.source_event=?) order by t.code,t.id limit 26 offset ?",this::transaction,args.toArray());
    }
    public UUID createActivity(String institution,CodeForm form,UUID actor,OffsetDateTime now) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into gl_activity(id,sacco_id,code,name,name_sw,description,created_by,created_at) values(?,?,?,?,?,?,?,?)",id,institution,form.getCode(),form.getName(),form.getNameSw(),form.getDescription(),actor,now);return id;
    }
    public boolean activeTransactions(String institution,UUID activity) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from gl_transaction_code where sacco_id=? and activity_id=? and active)",Boolean.class,institution,activity));
    }
    public void activityState(String institution,UUID id,boolean active) {jdbc.update("update gl_activity set active=? where sacco_id=? and id=?",active,institution,id);}
    public List<TransactionCode> transactions(String institution,UUID activity,String search,String state,int offset) {
        return jdbc.query(TRANSACTION+" where t.sacco_id=? and t.activity_id=? and (strpos(lower(t.code),?)>0 or strpos(lower(t.name),?)>0 or strpos(lower(coalesce(t.name_sw,'')),?)>0) and (?='' or t.active=(?='ACTIVE')) order by t.code,t.id limit 26 offset ?",
            this::transaction,institution,activity,search,search,search,state,state,offset);
    }
    public Optional<TransactionCode> transaction(String institution,UUID id,boolean lock) {
        return jdbc.query(TRANSACTION+" where t.sacco_id=? and t.id=?"+(lock?" for update of t":""),this::transaction,institution,id).stream().findFirst();
    }
    public UUID createTransaction(String institution,CodeForm form,UUID actor,OffsetDateTime now) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into gl_transaction_code(id,sacco_id,activity_id,code,name,name_sw,description,source_event,created_by,created_at) values(?,?,?,?,?,?,?,?,?,?)",id,institution,form.getActivityId(),form.getCode(),form.getName(),form.getNameSw(),form.getDescription(),form.getSourceEvent(),actor,now);return id;
    }
    public void transactionState(String institution,UUID id,boolean active) {jdbc.update("update gl_transaction_code set active=? where sacco_id=? and id=?",active,institution,id);}
    public List<AccountChoice> accounts(String institution,String search,int offset) {
        return jdbc.query("select code,name,name_sw,kind from gl_account where sacco_id=? and active and kind<>'HEADING' and (strpos(lower(code),?)>0 or strpos(lower(name),?)>0 or strpos(lower(coalesce(name_sw,'')),?)>0) order by code,id limit 26 offset ?",
            (r,n)->new AccountChoice(r.getString("code"),r.getString("name"),r.getString("name_sw"),r.getString("kind")),institution,search,search,search,offset);
    }
    public List<TemplateAccount> lockAccounts(String institution,List<String> codes) {
        String slots=String.join(",",Collections.nCopies(codes.size(),"?"));var args=new ArrayList<Object>();args.add(institution);args.addAll(codes);
        return jdbc.query("select id,code,kind,active from gl_account where sacco_id=? and code in ("+slots+") order by id for share",
            (r,n)->new TemplateAccount(r.getObject("id",UUID.class),r.getString("code"),r.getString("kind"),r.getBoolean("active")),args.toArray());
    }
    public record TemplateAccount(UUID id,String code,String kind,boolean active) { }
    public Optional<SavedRequest> request(String institution,UUID transaction,UUID key) {
        return jdbc.query("select id,payload_hash,created_by from gl_template_version where sacco_id=? and transaction_id=? and request_key=?",
            (r,n)->new SavedRequest(r.getObject("id",UUID.class),r.getString("payload_hash"),r.getObject("created_by",UUID.class)),institution,transaction,key).stream().findFirst();
    }
    public record SavedRequest(UUID id,String hash,UUID actor) { }
    public UUID saveTemplate(String institution,TransactionCode transaction,TemplateForm form,String hash,List<TemplateAccount> accounts,UUID actor,OffsetDateTime now) {
        UUID id=UUID.randomUUID();int version=transaction.revision()+1;
        jdbc.update("insert into gl_template_version(id,sacco_id,transaction_id,version,source_event,reason,request_key,payload_hash,created_by,created_at) values(?,?,?,?,?,?,?,?,?,?)",
            id,institution,transaction.id(),version,transaction.sourceEvent(),form.getReason(),form.getRequestKey(),hash,actor,now);
        var ids=new HashMap<String,UUID>();accounts.forEach(a->ids.put(a.code(),a.id()));
        var lines=new ArrayList<Object[]>();
        for(var rule:form.getRules()) {
            lines.add(new Object[]{id,institution,rule.getComponent(),"DEBIT",ids.get(rule.getDebitCode())});
            lines.add(new Object[]{id,institution,rule.getComponent(),"CREDIT",ids.get(rule.getCreditCode())});
        }
        jdbc.batchUpdate("insert into gl_template_line(template_id,sacco_id,component,side,account_id) values(?,?,?,?,?)",lines);
        jdbc.update("update gl_transaction_code set revision=?,current_template_id=? where sacco_id=? and id=?",version,id,institution,transaction.id());return id;
    }
    public Optional<Template> template(String institution,UUID transaction,UUID id) {
        return jdbc.query("select id,transaction_id,version,source_event,reason,created_at from gl_template_version where sacco_id=? and transaction_id=? and id=?",
            (r,n)->new Template(r.getObject("id",UUID.class),r.getObject("transaction_id",UUID.class),r.getInt("version"),r.getString("source_event"),r.getString("reason"),r.getObject("created_at",OffsetDateTime.class),List.of()),institution,transaction,id).stream().findFirst();
    }
    public List<Rule> rules(String institution,UUID template) {
        return jdbc.query("select d.component,da.code debit_code,da.name debit_name,ca.code credit_code,ca.name credit_name from gl_template_line d join gl_template_line c on c.template_id=d.template_id and c.sacco_id=d.sacco_id and c.component=d.component and c.side='CREDIT' join gl_account da on da.id=d.account_id and da.sacco_id=d.sacco_id join gl_account ca on ca.id=c.account_id and ca.sacco_id=c.sacco_id where d.sacco_id=? and d.template_id=? and d.side='DEBIT' order by d.component limit 5",
            (r,n)->new Rule(r.getString("component"),r.getString("debit_code"),r.getString("debit_name"),r.getString("credit_code"),r.getString("credit_name")),institution,template);
    }
    public List<Version> versions(String institution,UUID transaction,int offset) {
        return jdbc.query("select id,version,reason,created_at from gl_template_version where sacco_id=? and transaction_id=? order by version desc limit 26 offset ?",
            (r,n)->new Version(r.getObject("id",UUID.class),r.getInt("version"),r.getString("reason"),r.getObject("created_at",OffsetDateTime.class)),institution,transaction,offset);
    }
    private Activity activity(ResultSet r,int n) throws SQLException {
        return new Activity(r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("name_sw"),r.getString("description"),r.getBoolean("active"));
    }
    private TransactionCode transaction(ResultSet r,int n) throws SQLException {
        return new TransactionCode(r.getObject("id",UUID.class),r.getObject("activity_id",UUID.class),r.getString("activity_code"),r.getString("activity_name"),r.getString("activity_name_sw"),r.getString("code"),r.getString("name"),r.getString("name_sw"),r.getString("description"),r.getString("source_event"),r.getBoolean("active"),r.getInt("revision"),r.getObject("current_template_id",UUID.class));
    }
}
