package com.sacco.mvp.accounting.batch.repository;

import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.*;
import com.sacco.mvp.accounting.dto.VoucherDtos.Form;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class ReceiptBatchRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public boolean payrollLinked(UUID batch){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from payroll_remittance where receipt_batch_id=?)",Boolean.class,batch));}
    public UUID create(AppUserPrincipal a,String title,OffsetDateTime now){UUID id=UUID.randomUUID();jdbc.update("insert into receipt_batch(id,sacco_id,station_id,created_by,title,created_at,updated_at) values(?,?,?,?,?,?,?)",id,a.getSaccoId(),a.getStationId(),a.getMemberId(),title,now,now);return id;}
    public List<Batch> list(AppUserPrincipal a,String search,int page){return jdbc.query("select * from receipt_batch where sacco_id=? and station_id=? and strpos(lower(title),?)>0 order by created_at desc,id limit 26 offset ?",this::batch,a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);}
    public Optional<Batch> find(AppUserPrincipal a,UUID id,boolean lock){return jdbc.query("select * from receipt_batch where id=? and sacco_id=? and station_id=?"+(lock?" for update":""),this::batch,id,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public Summary summary(Batch b){return jdbc.query("select count(*) receipts,count(*) filter(where state='POSTED') posted,count(*) filter(where state='READY') ready,count(*) filter(where state='FAILED') failed,coalesce(sum(total),0) total,coalesce(sum(total) filter(where state='POSTED'),0) posted_total from receipt_batch_item where batch_id=?",(r,n)->new Summary(b,r.getInt("receipts"),r.getInt("posted"),r.getInt("ready"),r.getInt("failed"),r.getBigDecimal("total"),r.getBigDecimal("posted_total")),b.id()).getFirst();}
    public List<Item> items(UUID batch,int page,String state){return jdbc.query("select * from receipt_batch_item where batch_id=? and (?='' or state=?) order by line_no,id limit 26 offset ?",this::item,batch,state,state,page*25);}
    public Optional<Item> item(UUID batch,UUID id){return jdbc.query("select * from receipt_batch_item where batch_id=? and id=?",this::item,batch,id).stream().findFirst();}
    public int transactions(UUID batch){return jdbc.queryForObject("select coalesce(sum(jsonb_array_length(form_json->'rows')),0) from receipt_batch_item where batch_id=?",Integer.class,batch);}
    public void save(Batch b,UUID id,int line,int version,Form f,String client,Map<UUID,String> labels,BigDecimal total,boolean fresh){
        if(fresh)jdbc.update("insert into receipt_batch_item(id,batch_id,sacco_id,station_id,line_no,form_json,payment_date,total,client_label,loan_labels) values(?,?,?,?,?,?::jsonb,?,?,?,?::jsonb)",id,b.id(),b.institution(),b.branch(),line,write(f),f.getEffectiveDate(),total,client,write(labels));
        else jdbc.update("update receipt_batch_item set form_json=?::jsonb,payment_date=?,total=?,client_label=?,loan_labels=?::jsonb,version=version+1 where id=? and batch_id=? and version=? and state<>'POSTED'",write(f),f.getEffectiveDate(),total,client,write(labels),id,b.id(),version);
    }
    public int nextLine(UUID batch){return jdbc.queryForObject("select coalesce(max(line_no),0)+1 from receipt_batch_item where batch_id=?",Integer.class,batch);}
    public void delete(UUID batch,UUID id){jdbc.update("delete from receipt_batch_item where batch_id=? and id=? and state<>'POSTED'",batch,id);}
    public void invalidate(UUID batch){jdbc.update("update receipt_batch_item set state='DRAFT',preview_json=null,failure_key=null,attempts=0 where batch_id=? and state<>'POSTED'",batch);}
    public void state(UUID batch,String state,OffsetDateTime now){jdbc.update("update receipt_batch set state=?,version=version+1,worker_token=null,lease_until=null,cancel_requested=false,updated_at=? where id=?",state,now,batch);}
    public void cancel(UUID batch){jdbc.update("update receipt_batch set cancel_requested=true where id=?",batch);}
    public Optional<Batch> claim(OffsetDateTime now){
        UUID token=UUID.randomUUID();return jdbc.query("with candidate as (select id from receipt_batch where state in('VALIDATING','POSTING') and (lease_until is null or lease_until<?) order by updated_at,id limit 1 for update skip locked) update receipt_batch b set worker_token=?,lease_until=? from candidate c where b.id=c.id returning b.*",this::batch,now,token,now.plusSeconds(45)).stream().findFirst();
    }
    public Optional<Batch> workerLock(UUID id,UUID token){return jdbc.query("select * from receipt_batch where id=? and worker_token=? and state in('VALIDATING','POSTING') for update",this::batch,id,token).stream().findFirst();}
    public Optional<Item> next(UUID batch,boolean posting){return jdbc.query("select * from receipt_batch_item where batch_id=? and state=? order by payment_date nulls last,line_no,id limit 1",this::item,batch,posting?"READY":"DRAFT").stream().findFirst();}
    public List<Plan> priorPlans(UUID batch){return jdbc.query("select preview_json::text from receipt_batch_item where batch_id=? and state='READY' order by payment_date,line_no limit 1000",(r,n)->json.readValue(r.getString(1),Plan.class),batch);}
    public boolean duplicateReference(UUID batch,UUID id,UUID money,String reference){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from receipt_batch_item where batch_id=? and id<>? and form_json->>'moneyAccountId'=? and btrim(form_json->>'reference')=?)",Boolean.class,batch,id,Objects.toString(money,""),reference));}
    public void ready(UUID batch,UUID id,Plan plan){jdbc.update("update receipt_batch_item set state='READY',preview_json=?::jsonb,failure_key=null where batch_id=? and id=? and state<>'POSTED'",write(plan),batch,id);}
    public void posted(UUID batch,UUID id,UUID receipt,OffsetDateTime now){jdbc.update("update receipt_batch_item set state='POSTED',receipt_id=?,posted_at=?,failure_key=null where batch_id=? and id=? and state='READY'",receipt,now,batch,id);}
    public void failed(UUID batch,UUID id,String failure){jdbc.update("update receipt_batch_item set state='FAILED',failure_key=? where batch_id=? and id=? and state<>'POSTED'",failure,batch,id);}
    public int attempt(UUID batch,UUID id){return jdbc.queryForObject("update receipt_batch_item set attempts=attempts+1 where batch_id=? and id=? and state<>'POSTED' returning attempts",Integer.class,batch,id);}
    public void defer(UUID batch,UUID token,OffsetDateTime now){jdbc.update("update receipt_batch set lease_until=? where id=? and worker_token=?",now.plusSeconds(10),batch,token);}
    public void release(UUID batch,UUID token,OffsetDateTime now){jdbc.update("update receipt_batch set worker_token=null,lease_until=null,updated_at=? where id=? and worker_token=?",now,batch,token);}
    private Batch batch(ResultSet r,int n)throws SQLException{return new Batch(r.getObject("id",UUID.class),r.getString("sacco_id"),r.getString("station_id"),r.getObject("created_by",UUID.class),r.getString("title"),r.getInt("version"),r.getString("state"),r.getBoolean("cancel_requested"),r.getObject("worker_token",UUID.class),r.getObject("created_at",OffsetDateTime.class));}
    private Item item(ResultSet r,int n)throws SQLException{return new Item(r.getObject("id",UUID.class),r.getInt("line_no"),r.getInt("version"),r.getString("state"),json.readValue(r.getString("form_json"),Form.class),r.getString("client_label"),json.readValue(r.getString("loan_labels"),new TypeReference<Map<UUID,String>>(){}),r.getString("preview_json")==null?null:json.readValue(r.getString("preview_json"),Plan.class),r.getString("failure_key"),r.getObject("receipt_id",UUID.class),r.getBigDecimal("total"),null);}
    private String write(Object value){return json.writeValueAsString(value);}
}
