package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupOrganizationAgreementDtos.*;
import com.sacco.mvp.grouplending.dto.GroupConsentDtos.Delivery;
import com.sacco.mvp.grouplending.repository.GroupOrganizationAgreementRepository;
import com.sacco.mvp.grouplending.repository.GroupOrganizationDocumentRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupOrganizationAgreementService {
    private final GroupOrganizationAgreementRepository agreements;
    private final GroupOrganizationDocumentRepository documents;
    private final MemberDirectoryService members;
    private final AccessControlService access;
    private final UserClaimService claims;
    private final SaccoRegistryService institutions;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final StationOtpSettingsService otpSettings;
    private final PasswordEncoder passwords;
    private final org.springframework.core.env.Environment configuration;
    private static final java.security.SecureRandom RANDOM=new java.security.SecureRandom();
    private static final Set<String> CLOSED=Set.of("WITHDRAWN","DISBURSED","PAR","DEFAULTED","WRITTEN_OFF","PAID","DISBURSEMENT_REVERSED",
        "MANAGER_REJECTED","LOAN_OFFICER_REJECTED","CHAIRPERSON_REJECTED","BOARD_REJECTED","CREDIT_COMMITTEE_REJECTED","ACCOUNTANT_REJECTED","REJECTED");
    private Member authorize(AppUserPrincipal a,boolean write){
        if(a==null || a.isStaffSession() || !a.isMemberAccess())throw new AccessDeniedException("Sign in as the named person");
        var current=members.find(a.getMemberId()).orElseThrow(()->new AccessDeniedException("Signatory unavailable"));
        if(current.getStatus()!=MemberStatus.ACTIVE || !current.isMemberAccess() || !Objects.equals(current.getSaccoId(),a.getSaccoId())
            || !Objects.equals(current.getStationId(),a.getStationId()))throw new AccessDeniedException("Signatory unavailable");
        var effective=claims.effectiveClaims(current.getId(),current.getActiveStaffRolesResolved(),current.isMemberAccess());
        for(var claim:write?List.of(UserClaim.MEMBER_LOANS_VIEW,UserClaim.MEMBER_LOANS_UPDATE):List.of(UserClaim.MEMBER_LOANS_VIEW))
            if(!access.has(a,claim) || !effective.contains(claim))throw new AccessDeniedException("Signatory permission required");
        if(institutions.findActiveSacco(a.getSaccoId()).isEmpty() || institutions.findStation(a.getSaccoId(),a.getStationId())
            .filter(s->s.isActive() && s.getAccessStatus()==SaccoAccessStatus.ACTIVE).isEmpty())throw new AccessDeniedException("Institution or branch unavailable");
        return current;
    }
    @Transactional(readOnly=true) public Packet packet(AppUserPrincipal a,UUID cycle,int page){
        var person=authorize(a,false);check(page>=0 && page<=23,"query");var row=agreements.own(a,cycle,clock.today());var snapshot=row.snapshot();
        var schedule=schedule(snapshot);
        return new Packet(cycle,snapshot,row.hash(),row.state(),row.loanStatus(),row.signedCount(),row.signed(),row.signedAt(),open(row),signatureReady(person),
            otpSettings.requiresApprovalOtp(a.getMemberId(),a.getSaccoId(),a.getStationId()),schedule.stream().skip(page*25L).limit(25).toList(),page,schedule.size()>(page+1)*25,
            snapshot.document()!=null&&documents.opened(a,cycle,snapshot.document().id(),row.hash()));
    }
    static List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow> schedule(Snapshot snapshot){
        var f=snapshot.financial();check(f!=null && LoanAmortizationCalculator.VERSION.equals(f.calculationVersion()) && f.interestRate()!=null
            && f.interestMethod()!=null && f.repaymentFrequency()!=null && f.tenureUnit()!=null && f.tenureValue()!=null
            && f.interestAmount()!=null && f.recordedCharges()!=null && f.recordedTotalPayable()!=null,"terms");
        var values=new LinkedHashMap<String,Object>();values.put("calculationVersion",f.calculationVersion());values.put("interestRate",f.interestRate());values.put("interestMethod",f.interestMethod());
        values.put("repaymentFrequency",f.repaymentFrequency());values.put("tenureUnit",f.tenureUnit());values.put("tenureValue",f.tenureValue());
        var calculation=LoanAmortizationCalculator.estimate(snapshot.loan().principal(),snapshot.loan().tenorMonths(),values);
        var tenure=LoanTenure.fromSnapshot(values,snapshot.loan().tenorMonths());var frequency=RepaymentFrequency.valueOf(f.repaymentFrequency());
        var schedule=new ArrayList<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow>();var balance=calculation.totalRepayment();
        for(var r:calculation.rows()){
            var ending=balance.subtract(r.amount());
            var date=tenure.unit().equals("DAYS")?(r.number()==1?snapshot.loan().firstPaymentDate():null)
                :tenure.dueDate(null,snapshot.loan().firstPaymentDate(),frequency,r.number(),calculation.rows().size());
            schedule.add(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow(r.number(),date,r.principal(),r.interest(),r.amount(),balance,ending));
            balance=ending;
        }
        return List.copyOf(schedule);
    }
    /** A failed code returns a validation key so attempt counting commits. */
    @Transactional public String sign(AppUserPrincipal a,UUID cycle,String hash,boolean accepted,String code){
        var person=authorize(a,true);if(!accepted)throw new AccessDeniedException("Organization acceptance required");agreements.lock(a,cycle);var row=agreements.own(a,cycle,clock.today());
        check(Objects.equals(row.hash(),hash),"changed");if(row.signed())return null;if(!open(row))return "group.error.organizationSigningClosed";
        requireDocumentPermission(a,person);String documentError=documentError(a,row);if(documentError!=null)return documentError;if(!signatureReady(person))return "group.error.signature";
        UUID challenge=null;var at=clock.now();if(otpSettings.requiresApprovalOtp(a.getMemberId(),a.getSaccoId(),a.getStationId())){
            var recent=agreements.challenges(a,cycle);if(recent.isEmpty())return "group.error.otpRequired";var c=recent.getFirst();
            if(c.delivered()==null || c.consumed()!=null || !c.expires().isAfter(at) || c.attempts()>=5)return "group.error.otpExpired";
            agreements.attempt(c.id());if(code==null || !code.matches("[0-9]{6}") || !passwords.matches(code,c.hash()))return "group.error.otpInvalid";
            challenge=c.id();agreements.consumed(challenge,at);
        }
        agreements.sign(a,cycle,row.hash(),person.getSignatureText(),challenge,at);audit.logEvent("GROUP_LENDING",cycle,"ORGANIZATION_AGREEMENT_SIGNED",a.getMemberId(),AuditEventStatus.SUCCESS,
            "Organization agreement signed by its named representative","GROUP_CYCLE",cycle.toString(),a.getSaccoId(),a.getStationId(),Map.of("loan",row.snapshot().loan().id(),"authority",row.snapshot().authority().id(),"packet",row.hash()));return null;
    }
    @Transactional public Delivery prepareCode(AppUserPrincipal a,UUID cycle,String hash){
        var person=authorize(a,true);agreements.lock(a,cycle);var row=agreements.own(a,cycle,clock.today());check(Objects.equals(row.hash(),hash),"changed");
        check(open(row),"organizationSigningClosed");requireDocumentPermission(a,person);String documentError=documentError(a,row);if(documentError!=null)throw new IllegalArgumentException(documentError);
        check(!row.signed() && otpSettings.requiresApprovalOtp(a.getMemberId(),a.getSaccoId(),a.getStationId()),"otpDisabled");check(signatureReady(person),"signature");
        var now=clock.now();var recent=agreements.challenges(a,cycle);if(!recent.isEmpty() && (recent.getFirst().issued().isAfter(now.minusSeconds(60))
            || recent.size()==4 && recent.getLast().issued().isAfter(now.minusMinutes(10))))throw new IllegalArgumentException("group.error.otpWait");
        int ttl=Math.max(1,configuration.getProperty("app.auth.otp.ttl-minutes",Integer.class,10));String code=Integer.toString(RANDOM.nextInt(900000)+100000);var id=UUID.randomUUID();
        agreements.challenge(a,cycle,row.hash(),id,passwords.encode(code),now,now.plusMinutes(ttl));return new Delivery(id,cycle,a.getMemberId(),row.snapshot().loan().id(),person.getEmail(),person.getPhone(),code,a.getSaccoId(),a.getStationId(),ttl);
    }
    @Transactional public void codeDelivered(AppUserPrincipal a,UUID cycle,UUID id){
        authorize(a,true);agreements.lock(a,cycle);check(open(agreements.own(a,cycle,clock.today())),"organizationSigningClosed");
        var c=agreements.challenges(a,cycle).stream().findFirst().filter(x->x.id().equals(id) && x.expires().isAfter(clock.now()) && x.consumed()==null).orElseThrow(()->new IllegalArgumentException("group.error.otpExpired"));agreements.delivered(c.id(),clock.now());
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ) public ExportData exportData(AppUserPrincipal a,UUID cycle){
        var person=authorize(a,false);requireDocumentPermission(a,person);var row=agreements.own(a,cycle,clock.today());check(row.snapshot().document()!=null,"documentRequired");
        return new ExportData(row,documents.body(a,cycle,row.snapshot().document().id()),documents.signatures(a,cycle));
    }
    /** Records that an authorized PDF response was prepared; the user's acceptance remains separate. */
    @Transactional public void documentPrepared(AppUserPrincipal a,UUID cycle,UUID document,String hash){
        var person=authorize(a,false);requireDocumentPermission(a,person);agreements.lock(a,cycle);var row=agreements.own(a,cycle,clock.today());
        check(row.hash().equals(hash)&&row.snapshot().document()!=null&&row.snapshot().document().id().equals(document),"changed");
        if(!documents.opened(a,cycle,document,hash)){documents.opened(a,cycle,document,hash,clock.now());audit.logEvent("GROUP_LENDING",cycle,"ORGANIZATION_DOCUMENT_REQUESTED",a.getMemberId(),AuditEventStatus.SUCCESS,"Full frozen agreement PDF prepared for its named signatory","GROUP_CYCLE",cycle.toString(),a.getSaccoId(),a.getStationId(),Map.of("document",document,"packet",hash));}
    }
    private void requireDocumentPermission(AppUserPrincipal a,Member person){
        var current=claims.effectiveClaims(person.getId(),person.getActiveStaffRolesResolved(),person.isMemberAccess());
        for(var claim:List.of(UserClaim.LOAN_DOCUMENTS_VIEW,UserClaim.LOAN_DOCUMENTS_EXPORT))if(!access.has(a,claim)||!current.contains(claim))throw new AccessDeniedException("Agreement document permission required");
    }
    private String documentError(AppUserPrincipal a,Row row){if(row.snapshot().document()==null)return "group.error.documentRequired";return documents.opened(a,row.cycle(),row.snapshot().document().id(),row.hash())?null:"group.error.documentOpen";}
    private static boolean open(Row row){return row.currentAuthority() && row.state().equals("SUBMITTED") && !CLOSED.contains(row.loanStatus());}
    private static boolean signatureReady(Member person){return person.getSignatureText()!=null && !person.getSignatureText().isBlank();}
    private static void check(boolean value,String key){if(!value)throw new IllegalArgumentException("group.error."+key);}
}
