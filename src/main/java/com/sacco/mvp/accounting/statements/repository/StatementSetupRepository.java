package com.sacco.mvp.accounting.statements.repository;

import com.sacco.mvp.accounting.statements.dto.StatementModels.Parameters;
import com.sacco.mvp.accounting.reports.PostingDescription;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository @RequiredArgsConstructor
public class StatementSetupRepository {
    private final JdbcTemplate jdbc;
    public Optional<StoredSetup> latest(String institution){return jdbc.query("select version,payload from statement_setup_version where sacco_id=? order by version desc limit 1",(r,n)->new StoredSetup(r.getInt(1),r.getString(2)),institution).stream().findFirst();}
    public Optional<StoredSetup> version(String institution,int version){return jdbc.query("select version,payload from statement_setup_version where sacco_id=? and version=?",(r,n)->new StoredSetup(r.getInt(1),r.getString(2)),institution,version).stream().findFirst();}
    public Optional<String> request(String institution,UUID key){return jdbc.query("select request_hash from statement_setup_version where sacco_id=? and request_key=?",(r,n)->r.getString(1),institution,key).stream().findFirst();}
    public void lock(String key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","STATEMENT_SETUP/"+key);}
    public void save(AppUserPrincipal a,Setup setup,String payload,UUID key,String hash,String reason){jdbc.update("insert into statement_setup_version(sacco_id,version,payload,request_key,request_hash,actor_id,reason) values(?,?,?,?,?,?,?)",a.getSaccoId(),setup.version(),payload,key,hash,a.getMemberId(),reason);}
    public List<AccountChoice> accounts(String institution){return jdbc.query("select id,code,name,type from gl_account where sacco_id=? and kind<>'HEADING' order by code,id limit 2001",(r,n)->new AccountChoice(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4)),institution);}
    public List<Journal> journals(AppUserPrincipal a,Parameters p,int page){return jdbc.query("""
        select j.id,j.effective_date,d.number reference,j.source_reference,j.source_type,j.reason,totals.debit,totals.credit,coalesce(c.category,'UNCLASSIFIED') category,coalesce(c.version,0) version,
            c.movement_row_id,c.movement_name,c.movement_name_sw
        from gl_journal j
        left join accounting_posted_voucher_document d on d.journal_id=j.id and d.sacco_id=j.sacco_id and d.station_id=j.station_id
        join lateral(select sum(l.debit) debit,sum(l.credit) credit from gl_journal_line l
          join gl_account a on a.id=l.account_id and a.sacco_id=l.sacco_id
          where l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id and a.type='EQUITY'
          having count(*)>0) totals on true
        left join lateral(select category,version,movement_row_id,movement_name,movement_name_sw from statement_equity_classification c where c.journal_id=j.id
          and c.sacco_id=j.sacco_id and c.station_id=j.station_id order by version desc limit 1)c on true
        where j.sacco_id=? and j.station_id=? and j.state='POSTED' and j.effective_date between ? and ?
        order by j.effective_date desc,j.id limit 31 offset ?
        """,(r,n)->new Journal(r.getObject("id",UUID.class),r.getObject("effective_date",java.time.LocalDate.class),r.getString("reference"),
            PostingDescription.resolve(r.getString("source_type"),r.getString("source_reference"),r.getString("reference"),r.getString("reason")),
            Category.valueOf(r.getString("category")),r.getInt("version"),r.getBigDecimal("debit"),r.getBigDecimal("credit"),movementRow(r)),a.getSaccoId(),a.getStationId(),p.from(),p.through(),page*30);}
    public List<Movement> journal(AppUserPrincipal a,UUID id){return jdbc.query("""
        select l.account_id,a.type,sum(l.debit) debit,sum(l.credit) credit from gl_journal j
        join gl_journal_line l on l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id
        join gl_account a on a.id=l.account_id and a.sacco_id=l.sacco_id
        where j.id=? and j.sacco_id=? and j.station_id=? and j.state='POSTED'
        group by l.account_id,a.type order by l.account_id limit 2001
        """,(r,n)->new Movement(r.getObject("account_id",UUID.class),Category.UNCLASSIFIED,false,r.getBigDecimal("debit"),r.getBigDecimal("credit")),id,a.getSaccoId(),a.getStationId());}
    public int classificationVersion(AppUserPrincipal a,UUID id){return jdbc.queryForObject("select coalesce(max(version),0) from statement_equity_classification where sacco_id=? and station_id=? and journal_id=?",Integer.class,a.getSaccoId(),a.getStationId(),id);}
    public Optional<Classification> latestClassification(AppUserPrincipal a,UUID id){
        return jdbc.query("""
            select category,movement_row_id,movement_name,movement_name_sw,movement_setup_version
            from statement_equity_classification where sacco_id=? and station_id=? and journal_id=?
            order by version desc limit 1
            """,(r,n)->classification(r),a.getSaccoId(),a.getStationId(),id).stream().findFirst();
    }
    public Optional<Classification> reversalClassification(AppUserPrincipal a,com.sacco.mvp.accounting.dto.VoucherDtos.Type type,UUID original,UUID reversal){return jdbc.query("""
        select c.category,c.movement_row_id,c.movement_name,c.movement_name_sw,c.movement_setup_version from gl_journal r
        join accounting_voucher v on v.journal_id=r.id and v.sacco_id=r.sacco_id and v.station_id=r.station_id
        join statement_equity_classification c on c.journal_id=r.reverses_id and c.sacco_id=r.sacco_id and c.station_id=r.station_id
        where r.id=? and r.reverses_id=? and r.sacco_id=? and r.station_id=? and r.state='POSTED'
          and r.source_type='CORE_REVERSAL' and r.maker_id=? and v.type=?
        order by c.version desc limit 1
        """,(r,n)->classification(r),reversal,original,a.getSaccoId(),a.getStationId(),a.getMemberId(),type.name()).stream().findFirst();}
    public boolean sameClassification(AppUserPrincipal a,UUID id,Category category,String reason){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from statement_equity_classification where sacco_id=? and station_id=? and journal_id=? and version=(select max(version) from statement_equity_classification where sacco_id=? and station_id=? and journal_id=?) and category=? and actor_id=? and reason=?)",Boolean.class,a.getSaccoId(),a.getStationId(),id,a.getSaccoId(),a.getStationId(),id,category.name(),a.getMemberId(),reason));}
    public void classify(AppUserPrincipal a,UUID id,int version,Classification classification,String reason){
        var row=classification.row();
        jdbc.update("""
            insert into statement_equity_classification(sacco_id,station_id,journal_id,version,category,actor_id,reason,
                movement_setup_version,movement_row_id,movement_name,movement_name_sw) values(?,?,?,?,?,?,?,?,?,?,?)
            """,a.getSaccoId(),a.getStationId(),id,version,classification.category().name(),a.getMemberId(),reason,
            classification.setupVersion(),row==null?null:row.id(),row==null?null:row.name(),row==null?null:row.nameSw());
    }
    public List<Movement> movements(UUID run,Parameters p){return jdbc.query("""
        select l.account_id,coalesce(c.category,'UNCLASSIFIED') category,
          period.previous,sum(l.debit) debit,sum(l.credit) credit,c.movement_row_id,c.movement_name,c.movement_name_sw
        from statement_run_journal r join gl_journal j on j.id=r.journal_id
        cross join (values(false),(true)) period(previous)
        join gl_journal_line l on l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id
        join gl_account a on a.id=l.account_id and a.sacco_id=l.sacco_id
        left join lateral(select category,movement_row_id,movement_name,movement_name_sw from statement_equity_classification c where c.journal_id=j.id
          and c.sacco_id=j.sacco_id and c.station_id=j.station_id order by version desc limit 1)c on true
        where r.run_id=? and ((not period.previous and j.effective_date between ? and ?) or (period.previous and j.effective_date between ? and ?)) and (a.type='EQUITY' or c.category='EARNINGS_TRANSFER' and a.type in ('INCOME','EXPENSE'))
        group by l.account_id,c.category,previous,c.movement_row_id,c.movement_name,c.movement_name_sw
        order by l.account_id,c.category,previous,c.movement_row_id,c.movement_name,c.movement_name_sw limit 32001
        """,(r,n)->new Movement(r.getObject("account_id",UUID.class),Category.valueOf(r.getString("category")),r.getBoolean("previous"),r.getBigDecimal("debit"),r.getBigDecimal("credit"),movementRow(r)),run,p.from(),p.through(),p.previousFrom(),p.previousThrough());}
    private static Classification classification(ResultSet r) throws SQLException {
        return new Classification(Category.valueOf(r.getString("category")),movementRow(r),r.getObject("movement_setup_version",Integer.class));
    }
    private static MovementRow movementRow(ResultSet r) throws SQLException {
        UUID id=r.getObject("movement_row_id",UUID.class);
        return id==null?null:new MovementRow(id,Category.valueOf(r.getString("category")),r.getString("movement_name"),r.getString("movement_name_sw"));
    }
}
