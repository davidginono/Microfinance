package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupPaperDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupPaperRepository {
    private final JdbcTemplate jdbc;
    public void cycle(AppUserPrincipal a,UUID cycle,boolean lock){if(jdbc.queryForList("select id from lending_group_cycle where id=? and sacco_id=? and station_id=?"+(lock?" for update":""),cycle,a.getSaccoId(),a.getStationId()).isEmpty())throw new AccessDeniedException("Group cycle unavailable");}
    public List<PaperTarget> targets(AppUserPrincipal a,UUID cycle){return jdbc.query("""
        with targets as (
         select 'TERMS' kind,x.client_id,null::uuid offer_id,l.amount
         from lending_group_participant x left join loan_applications l on l.id=x.loan_id where x.cycle_id=?
         union all select 'SHARED_PAYOUT',s.client_id,null,s.net from lending_group_shared_allocation s where s.cycle_id=?
         union all select 'ORG_AGREEMENT',s.client_id,null,l.amount from lending_group_org_agreement p
          join lending_group_authority_signer s on s.authority_id=p.authority_id and s.may_sign join loan_applications l on l.id=p.loan_id where p.cycle_id=?
         union all select 'ORG_PAYOUT',s.client_id,null,(p.snapshot->'financial'->>'recordedNetCashEstimate')::numeric from lending_group_org_agreement p
          join lending_group_authority_signer s on s.authority_id=p.authority_id and s.may_receive where p.cycle_id=?
         union all select 'PERSONAL',g.client_id,g.id,g.amount_cap from lending_group_personal_guarantee g where g.cycle_id=?
        ) select t.*,m.full_name,m.member_no from targets t join members m on m.id=t.client_id
        where m.sacco_id=? and m.station_id=? order by kind,m.member_no,t.offer_id limit 251
        """,(r,n)->new PaperTarget(r.getString("kind")+"/"+r.getObject("client_id")+"/"+Objects.toString(r.getObject("offer_id"),""),r.getString("kind"),r.getObject("client_id",UUID.class),r.getObject("offer_id",UUID.class),r.getString("full_name"),r.getString("member_no"),r.getBigDecimal("amount")),cycle,cycle,cycle,cycle,cycle,a.getSaccoId(),a.getStationId());}
    public PaperProposal proposal(AppUserPrincipal a,UUID cycle,PaperTarget target,PaperForm f,String destination,OffsetDateTime signed){
        return jdbc.queryForObject("""
            with contract as(select lending_group_paper_contract(?,?,?,?,?::jsonb) data), proposal as(
             select data,jsonb_populate_record(null::lending_group_signed_paper,jsonb_build_object('cycle_id',?::uuid,'sacco_id',?::text,'station_id',?::text,
              'kind',?::text,'client_id',?::uuid,'offer_id',?::uuid,'contract',data,'reference',?::text,'signed_at',?::timestamptz,'recorded_by',?::uuid,
              'expected_row',jsonb_build_object('preferred_language',?::text))) document from contract)
            select data::text,lending_group_paper_expected(document)::text,encode(sha256(convert_to(jsonb_build_array(data,lending_group_paper_expected(document))::text,'UTF8')),'hex') from proposal
            """,(r,n)->new PaperProposal(r.getString(1),r.getString(2),r.getString(3)),cycle,target.kind(),target.client(),target.offer(),destination,
            cycle,a.getSaccoId(),a.getStationId(),target.kind(),target.client(),target.offer(),f.getReference().strip(),signed,a.getMemberId(),f.getLanguage());
    }
    public Optional<UUID> retry(AppUserPrincipal a,UUID cycle,PaperForm form,String destination,String filename,String checksum,OffsetDateTime signed){return jdbc.query("""
        select id,recorded_by,reference,filename,sha256,signed_at,encode(sha256(convert_to(jsonb_build_array(contract,expected_row)::text,'UTF8')),'hex') hash,
         kind||'/'||client_id::text||'/'||coalesce(offer_id::text,'') target,expected_row->>'preferred_language' language,
         kind<>'SHARED_PAYOUT' or contract->'destination'=?::jsonb destination_matches
        from lending_group_signed_paper where id=? and cycle_id=? and sacco_id=? and station_id=?
        """,(r,n)->{if(!a.getMemberId().equals(r.getObject("recorded_by",UUID.class))||!Objects.equals(form.getExpectedHash(),r.getString("hash"))
          ||!form.getTarget().equals(r.getString("target"))||!r.getBoolean("destination_matches")
          ||r.getString("language")!=null&&!form.getLanguage().equals(r.getString("language"))
          ||!form.getReference().strip().equals(r.getString("reference"))||!filename.equals(r.getString("filename"))
          ||!checksum.equals(r.getString("sha256"))||!signed.toInstant().equals(r.getObject("signed_at",OffsetDateTime.class).toInstant()))throw new IllegalArgumentException("group.error.retry");return r.getObject("id",UUID.class);},destination,form.getRequestKey(),cycle,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public void record(AppUserPrincipal a,UUID cycle,PaperTarget t,PaperForm f,PaperProposal p,OffsetDateTime signed,OffsetDateTime at,String filename,String checksum,byte[] bytes){
        jdbc.update("""
            insert into lending_group_signed_paper(id,cycle_id,sacco_id,station_id,kind,client_id,offer_id,contract,expected_row,reference,signed_at,recorded_by,recorded_at,filename,sha256,body)
            values(?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?,?,?)
            """,f.getRequestKey(),cycle,a.getSaccoId(),a.getStationId(),t.kind(),t.client(),t.offer(),p.contract(),p.row(),f.getReference().strip(),signed,a.getMemberId(),at,filename,checksum,bytes);
        if(t.kind().equals("TERMS"))jdbc.update("insert into lending_group_consent(cycle_id,sacco_id,station_id,client_id,accepted_at) values(?,?,?,?,?)",cycle,a.getSaccoId(),a.getStationId(),t.client(),signed);
        String table=switch(t.kind()){case "TERMS"->"lending_group_consent_evidence";case "SHARED_PAYOUT"->"lending_group_shared_payout";case "ORG_AGREEMENT"->"lending_group_org_signature";case "ORG_PAYOUT"->"lending_group_org_payout";case "PERSONAL"->"lending_group_personal_decision";default->throw new IllegalArgumentException("group.error.request");};
        jdbc.update("insert into "+table+" select (jsonb_populate_record(null::"+table+",?::jsonb||jsonb_build_object('paper_id',?::uuid))).*",p.row(),f.getRequestKey());
    }
    public List<Paper> papers(AppUserPrincipal a,UUID cycle,int page){return jdbc.query("""
        select p.id,p.kind,m.full_name,p.reference,p.signed_at,s.full_name actor,p.recorded_at from lending_group_signed_paper p
        join members m on m.id=p.client_id join members s on s.id=p.recorded_by
        where p.cycle_id=? and p.sacco_id=? and p.station_id=? order by p.recorded_at desc,p.id desc limit 26 offset ?
        """,(r,n)->new Paper(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getObject(5,OffsetDateTime.class),r.getString(6),r.getObject(7,OffsetDateTime.class)),cycle,a.getSaccoId(),a.getStationId(),page*25);}
    public byte[] body(AppUserPrincipal a,UUID cycle,UUID id){return jdbc.query("select body from lending_group_signed_paper where id=? and cycle_id=? and sacco_id=? and station_id=?",(r,n)->r.getBytes(1),id,cycle,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Signed document unavailable"));}
    public Optional<UUID> guaranteePaper(AppUserPrincipal a,UUID cycle,UUID offer){return jdbc.query("select id from lending_group_signed_paper where cycle_id=? and offer_id=? and kind='PERSONAL' and sacco_id=? and station_id=?",(r,n)->r.getObject(1,UUID.class),cycle,offer,a.getSaccoId(),a.getStationId()).stream().findFirst();}
}
