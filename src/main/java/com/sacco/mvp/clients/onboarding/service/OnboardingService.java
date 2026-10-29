package com.sacco.mvp.clients.onboarding.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.*;
import com.sacco.mvp.clients.onboarding.repository.OnboardingRepository;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import com.sacco.mvp.web.form.MemberRegistrationForm;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OnboardingService {
    public static final Set<String> IDENTITY_TYPES=Set.of("NATIONAL_ID","PASSPORT","VOTER_ID","DRIVING_LICENCE","OTHER");
    private final OnboardingRepository records;
    private final GeneralLedgerService access;
    private final MemberRegistrationService registration;
    private final MemberDirectoryService directory;
    private final StoredUploadStorageService uploads;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;

    public void authorize(AppUserPrincipal a,String action) {
        access.requireActor(a,"CLIENT_ONBOARDING_VIEW");
        if(!action.equals("VIEW"))access.requireActor(a,"CLIENT_ONBOARDING_"+action);
    }
    public LocalDate today() {return clock.today();}
    @Transactional(readOnly=true)
    public Page<Client> clients(AppUserPrincipal a,String search,String state,int page,String sort) {
        authorize(a,"VIEW");check(search!=null&&search.length()<=100,"query");page(page);
        check(Set.of("","NOT_STARTED","DRAFT","SUBMITTED","VERIFY","RETURN","REJECT","REVOKE","EXPIRED","STALE").contains(state),"query");
        var columns=Map.of("number","member_no","name","full_name","state","state","due","review_due");
        String value=sort==null||sort.isBlank()?"number_asc":sort;int split=value.lastIndexOf('_');check(split>0,"query");
        String column=columns.get(value.substring(0,split)),direction=value.substring(split+1);check(column!=null&&Set.of("asc","desc").contains(direction),"query");
        var rows=records.clients(a,search,state,page,column+" "+direction+" nulls last",today());
        return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);
    }
    @Transactional(readOnly=true) public Client client(AppUserPrincipal a,UUID client) {authorize(a,"VIEW");return records.client(a,client,today());}
    @Transactional(readOnly=true) public Optional<Revision> latest(AppUserPrincipal a,UUID client) {client(a,client);return records.latest(a,client);}
    @Transactional(readOnly=true) public Revision version(AppUserPrincipal a,UUID client,int sequence) {client(a,client);return records.version(a,client,sequence);}
    @Transactional(readOnly=true) public List<Document> versionDocuments(AppUserPrincipal a,UUID client,int sequence) {return records.documents(version(a,client,sequence).id());}
    @Transactional(readOnly=true) public List<Decision> versionDecisions(AppUserPrincipal a,UUID client,int sequence) {return records.decisions(version(a,client,sequence).id());}
    @Transactional(readOnly=true) public List<Document> documents(AppUserPrincipal a,UUID client) {var c=client(a,client);return c.revisionId()==null?List.of():records.documents(c.revisionId());}
    @Transactional(readOnly=true) public List<Decision> decisions(AppUserPrincipal a,UUID client) {var c=client(a,client);return c.revisionId()==null?List.of():records.decisions(c.revisionId());}
    @Transactional(readOnly=true) public Page<History> history(AppUserPrincipal a,UUID client,int page) {client(a,client);page(page);var rows=records.history(a,client,page);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional(readOnly=true) public boolean duplicate(AppUserPrincipal a,UUID client) {var r=latest(a,client);return r.isPresent()&&!identity(r.get().profile()).isBlank()&&records.duplicate(a.getSaccoId(),identity(r.get().profile()),client);}

    @Transactional(timeout=30)
    public UUID create(AppUserPrincipal a,CreateForm f) {
        authorize(a,"CREATE");text(f.getNumber(),64,"number",true);text(f.getName(),255,"name",true);text(f.getEmail(),255,"email",true);
        check(f.getName().strip().split("\\s+").length>=2,"name");check(f.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),"email");
        check(f.getNumber().matches("[A-Za-z0-9/._-]+"),"number");
        try {f.setPhone(TanzaniaPhoneNumber.normalizeRequired(f.getPhone()));}catch(IllegalStateException e){throw invalid("phone");}
        String hash=hash(f);var old=retry(a,"client_onboarding_creation",f.getRequestKey(),hash);if(old.isPresent())return records.created(old.get());
        var form=new MemberRegistrationForm();form.setMemberNo(f.getNumber());form.setFullName(f.getName());form.setEmail(f.getEmail());form.setPhone(f.getPhone());form.setSaccoId(a.getSaccoId());form.setStationId(a.getStationId());
        // An unshared random password does not create an OTP-verification or consent event.
        form.setPassword(UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""));
        UUID client;
        try {client=registration.registerAssisted(form,a.getSaccoId(),a.getStationId()).getId();}catch(IllegalStateException e){throw invalid("registration");}
        records.creation(a,client,UUID.randomUUID(),f.getRequestKey(),hash,clock.now());event(a,client,"CLIENT_ASSISTED_REGISTRATION");return client;
    }
    @Transactional(timeout=20)
    public UUID save(AppUserPrincipal a,UUID client,ProfileForm f) {
        authorize(a,"UPDATE");return saveRevision(a,client,f,hash(List.of(client,f)));
    }
    private UUID saveRevision(AppUserPrincipal a,UUID client,ProfileForm f,String hash) {
        records.client(a,client,today());var old=retry(a,"client_onboarding_revision",f.getRequestKey(),hash);if(old.isPresent())return old.get();
        records.lock(client);var c=records.client(a,client,today());check(!c.state().equals("SUBMITTED"),"state");check(c.sequence()==f.getExpectedSequence(),"changed");
        check(Set.of("DRAFT","SUBMITTED").contains(f.getAction()),"action");check(f.getProfile()!=null,"profile");var p=json.readValue(json.writeValueAsString(f.getProfile()),Profile.class);
        if(p.isSelfService())records.latest(a,client).ifPresent(previous->p.setLegalHoldReference(previous.profile().getLegalHoldReference()));
        p.setFullName(c.name());p.setPhone(c.phone());p.setEmail(c.email());p.setClientNumber(c.number());validate(p,f.getAction().equals("SUBMITTED"));
        if(f.getAction().equals("SUBMITTED")) {
            check(c.revisionId()!=null,"documents");checkDocuments(p,records.documents(c.revisionId()));
        }
        UUID id=UUID.randomUUID();records.revision(a,client,id,c.sequence()+1,f.getAction(),p,identity(p),f.getRequestKey(),hash,clock.now());records.copyDocuments(a,client,c.revisionId(),id);
        event(a,client,"CLIENT_KYC_"+f.getAction());return id;
    }
    public UUID attach(AppUserPrincipal a,UUID client,DocumentForm f,MultipartFile file) {
        authorize(a,"UPDATE");return attach(a,client,f,file,false);
    }
    private UUID attach(AppUserPrincipal a,UUID client,DocumentForm f,MultipartFile file,boolean own) {
        records.client(a,client,today());check(f.getRevisionId()!=null&&f.getRequestKey()!=null,"request");check(Set.of("IDENTITY","ADDRESS","ACTIVITY","CONSENT").contains(f.getCategory()),"category");
        check(f.getExpiresOn()==null||!f.getExpiresOn().isBefore(today()),"documentExpiry");
        var content=prepare(file);String hash=hash(List.of(client,f,hash(content.bytes())));
        var tx=new TransactionTemplate(transactions);tx.setTimeout(20);
        return tx.execute(status->{
            if(own)requireOwner(a);else authorize(a,"UPDATE");records.client(a,client,today());var old=retry(a,"client_onboarding_document",f.getRequestKey(),hash);if(old.isPresent())return old.get();
            UUID id=UUID.randomUUID();var upload=uploads.store(null,"CLIENT_KYC",id.toString(),f.getCategory(),content.name(),content.contentType(),content.bytes());
            records.lock(client);var c=records.client(a,client,today());check(c.state().equals("DRAFT")&&f.getRevisionId().equals(c.revisionId()),"changed");
            records.document(a,client,c.revisionId(),id,upload.getId(),f,hash,clock.now());event(a,client,"CLIENT_KYC_DOCUMENT");return id;
        });
    }
    @Transactional(timeout=20)
    public UUID review(AppUserPrincipal a,UUID client,ReviewForm f) {
        authorize(a,"REVIEW");records.client(a,client,today());String hash=hash(List.of(client,f));var old=retry(a,"client_onboarding_decision",f.getRequestKey(),hash);if(old.isPresent())return old.get();
        records.lock(client);var c=records.client(a,client,today());check(f.getRevisionId()!=null&&f.getRevisionId().equals(c.revisionId()),"changed");
        check(Set.of("VERIFY","RETURN","REJECT","REVOKE").contains(f.getKind()),"action");
        check(f.getKind().equals("REVOKE")?Set.of("VERIFY","EXPIRED","STALE").contains(c.state()):c.state().equals("SUBMITTED"),"state");
        text(f.getReason(),1000,"reason",true);text(f.getScreeningReference(),1000,"screeningReference",false);text(f.getPolicyReference(),500,"policyReference",false);
        text(f.getClientMessage(),1000,"clientMessage",Set.of("RETURN","REJECT","REVOKE").contains(f.getKind()));
        check(Set.of("CLEARED","ESCALATED","UNAVAILABLE").contains(f.getScreening()),"screening");var r=records.latest(a,client).orElseThrow();
        if(f.getKind().equals("VERIFY")) {
            check(!client.equals(a.getMemberId())&&!r.maker().equals(a.getMemberId())&&!records.contributed(r.id(),a.getMemberId()),"independent");
            validate(r.profile(),true);checkDocuments(r.profile(),records.documents(r.id()));
            check(Objects.equals(c.name(),r.profile().getFullName())&&Objects.equals(c.phone(),r.profile().getPhone())&&Objects.equals(c.email(),r.profile().getEmail()),"contactChanged");
            check(f.isIdentityChecked()&&f.isAddressChecked()&&f.isActivityChecked()&&f.isContactChecked()&&f.isDuplicatesChecked(),"checks");
            check(f.getScreening().equals("CLEARED")&&!f.getScreeningReference().isBlank(),"screening");text(f.getPolicyReference(),500,"policyReference",true);
            check(f.getCheckedOn()!=null&&!f.getCheckedOn().isAfter(today())&&!f.getCheckedOn().isBefore(r.recordedAt().toLocalDate()),"checkedOn");
            check(f.getReviewDue()!=null&&!f.getReviewDue().isBefore(today())&&!f.getReviewDue().isBefore(f.getCheckedOn()),"reviewDue");
            String identity=identity(r.profile());records.lock("CLIENT_IDENTITY/"+a.getSaccoId()+"/"+identity);check(!records.duplicate(a.getSaccoId(),identity,client),"duplicate");
        }
        UUID id=UUID.randomUUID();records.decision(a,client,id,f,hash,clock.now());
        event(a,client,"CLIENT_KYC_"+f.getKind());return id;
    }
    @Transactional(timeout=15)
    public Download download(AppUserPrincipal a,UUID client,UUID document) {
        authorize(a,"DOCUMENTS");records.client(a,client,today());UUID uploadId=records.upload(a,client,document);
        var upload=uploads.load(uploadId,"CLIENT_KYC",document.toString());
        check(upload.getContent().length<=5*1024*1024&&uploads.verify(upload,upload.getContent()),"file");
        event(a,client,"CLIENT_KYC_DOCUMENT_READ");return new Download(upload.getContent(),upload.getContentType(),upload.getOriginalName());
    }
    @Transactional(readOnly=true)
    public OwnStatus ownStatus(AppUserPrincipal a) {
        requireOwner(a);var c=records.client(a,a.getMemberId(),today());return new OwnStatus(c.state(),c.reviewDue());
    }
    private void requireOwner(AppUserPrincipal a) {
        if(a==null||!a.isMemberAccess()||a.isStaffSession())throw new org.springframework.security.access.AccessDeniedException("Client workspace required");
        var member=directory.find(a.getMemberId()).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));
        if(!member.isMemberAccess()||member.getStatus()!=com.sacco.mvp.domain.MemberStatus.ACTIVE||!Objects.equals(member.getSaccoId(),a.getSaccoId())||!Objects.equals(member.getStationId(),a.getStationId()))throw new org.springframework.security.access.AccessDeniedException("Client unavailable");
    }
    @Transactional(readOnly=true)
    public OwnWorkspace ownWorkspace(AppUserPrincipal a,int page,Integer sequence) {
        requireOwner(a);page(page);var c=records.client(a,a.getMemberId(),today());
        var revision=sequence==null?records.latest(a,c.id()).orElse(null):records.version(a,c.id(),sequence);
        if(sequence!=null) {
            var decisions=records.decisions(revision.id());var decision=decisions.isEmpty()?null:decisions.getFirst();var p=revision.profile();
            c=new Client(c.id(),p.getClientNumber(),p.getFullName(),p.getPhone(),p.getEmail(),decision==null?revision.mode():decision.kind(),revision.id(),sequence,decision==null?null:decision.reviewDue());
        }
        var documents=revision==null?List.<OwnDocument>of():records.documents(revision.id()).stream().map(d->new OwnDocument(d.id(),d.category(),d.name(),d.expiresOn())).toList();
        var history=records.history(a,c.id(),page);
        var ownRevision=revision==null?null:new OwnRevision(revision.sequence(),personal(revision.profile()),revision.profile().getNoticeVersion(),revision.profile().getNoticeEnglish(),revision.profile().getNoticeKiswahili(),revision.profile().isNoticeAcknowledged(),revision.profile().getAcknowledgedAt());
        return new OwnWorkspace(c,ownRevision,documents,records.feedback(a,c.id(),sequence==null?null:revision.id()),new Page<>(history.stream().limit(25).map(h->new OwnHistory(h.sequence(),h.result(),h.recordedAt())).toList(),page,history.size()>25),sequence!=null);
    }
    @Transactional(readOnly=true)
    public SelfForm ownForm(AppUserPrincipal a) {
        requireOwner(a);var c=records.client(a,a.getMemberId(),today());check(!c.state().equals("SUBMITTED"),"state");
        var f=new SelfForm();f.setExpectedSequence(c.sequence());records.latest(a,c.id()).ifPresent(r->f.setProfile(personal(r.profile())));records.notice(a).ifPresent(n->f.setNoticeId(n.id()));return f;
    }
    @Transactional(readOnly=true)
    public Optional<Notice> ownNotice(AppUserPrincipal a) {requireOwner(a);return records.notice(a).filter(n->n.enabled()&&!n.retentionReviewOn().isBefore(today()));}
    @Transactional(timeout=20)
    public UUID saveOwn(AppUserPrincipal a,SelfForm f) {
        requireOwner(a);check(f.getProfile()!=null,"profile");String hash=hash(List.of(a.getMemberId(),f));
        var old=retry(a,"client_onboarding_revision",f.getRequestKey(),hash);if(old.isPresent())return old.get();
        records.lock(noticeLock(a));var n=records.notice(a).orElse(null);var p=json.readValue(json.writeValueAsString(f.getProfile()),Profile.class);p.setSelfService(true);
        if(f.getAction().equals("SUBMITTED")) {
            check(n!=null&&n.enabled()&&!n.retentionReviewOn().isBefore(today()),"noticeUnavailable");check(n.id().equals(f.getNoticeId()),"noticeChanged");
            check(f.isNoticeAcknowledged()&&f.isDeclarationAccepted(),"acknowledgement");
        }
        if(n!=null) {
            p.setNoticeId(n.id());p.setNoticeVersion(n.version());p.setNoticeEnglish(n.english());p.setNoticeKiswahili(n.kiswahili());p.setLawfulBasis(n.lawfulBasis());p.setRetentionReference(n.retentionReference());p.setRetentionReviewOn(n.retentionReviewOn());
            if(n.id().equals(f.getNoticeId())&&f.isNoticeAcknowledged()&&f.isDeclarationAccepted()) {
                p.setNoticeAcknowledged(true);p.setDeclarationAccepted(true);p.setAcknowledgedAt(clock.now());p.setConsentOn(today());p.setConsentReference("Client acknowledgement / "+n.id());
            }
        }
        var command=new ProfileForm();command.setRequestKey(f.getRequestKey());command.setExpectedSequence(f.getExpectedSequence());command.setAction(f.getAction());command.setProfile(p);
        return saveRevision(a,a.getMemberId(),command,hash);
    }
    public UUID attachOwn(AppUserPrincipal a,DocumentForm f,MultipartFile file) {requireOwner(a);return attach(a,a.getMemberId(),f,file,true);}
    @Transactional(readOnly=true)
    public DocumentForm ownDocumentForm(AppUserPrincipal a) {
        requireOwner(a);var c=records.client(a,a.getMemberId(),today());check(c.state().equals("DRAFT"),"state");var f=new DocumentForm();f.setRevisionId(c.revisionId());return f;
    }
    @Transactional(timeout=20)
    public Download downloadOwn(AppUserPrincipal a,UUID document) {
        requireOwner(a);var uploadId=records.upload(a,a.getMemberId(),document);var upload=uploads.load(uploadId,"CLIENT_KYC",document.toString());
        check(upload.getContent().length<=5*1024*1024&&uploads.verify(upload,upload.getContent()),"file");event(a,a.getMemberId(),"CLIENT_KYC_DOCUMENT_READ");return new Download(upload.getContent(),upload.getContentType(),upload.getOriginalName());
    }
    @Transactional(readOnly=true)
    public NoticeForm noticeForm(AppUserPrincipal a) {
        authorize(a,"UPDATE");var f=new NoticeForm();records.notice(a).ifPresent(n->{f.setExpectedSequence(n.sequence());f.setEnabled(n.enabled());f.setVersion(n.version());f.setEnglish(n.english());f.setKiswahili(n.kiswahili());f.setLawfulBasis(n.lawfulBasis());f.setRetentionReference(n.retentionReference());f.setRetentionReviewOn(n.retentionReviewOn());});return f;
    }
    @Transactional(timeout=20)
    public UUID saveNotice(AppUserPrincipal a,NoticeForm f) {
        authorize(a,"UPDATE");String hash=hash(f);var old=retry(a,"client_onboarding_notice",f.getRequestKey(),hash);if(old.isPresent())return old.get();
        text(f.getVersion(),100,"noticeVersion",true);text(f.getEnglish(),12000,"noticeEnglish",true);text(f.getKiswahili(),12000,"noticeKiswahili",true);text(f.getRetentionReference(),500,"retentionReference",true);
        check(Set.of("CONSENT","CONTRACT","LEGAL_OBLIGATION").contains(f.getLawfulBasis()),"lawfulBasis");check(f.getRetentionReviewOn()!=null&&!f.getRetentionReviewOn().isBefore(today()),"retentionReviewOn");
        records.lock(noticeLock(a));check(records.notice(a).map(Notice::sequence).orElse(0)==f.getExpectedSequence(),"changed");UUID id=UUID.randomUUID();records.notice(a,id,f,hash,clock.now());
        audit.logEvent("CLIENT_KYC_NOTICE",id,"CLIENT_KYC_NOTICE_PUBLISHED",a.getMemberId(),AuditEventStatus.SUCCESS,"CLIENT_KYC_NOTICE_PUBLISHED","BRANCH",a.getStationId(),a.getSaccoId(),a.getStationId(),Map.of());return id;
    }
    private String noticeLock(AppUserPrincipal a) {return "CLIENT_KYC_NOTICE/"+a.getSaccoId()+"/"+a.getStationId();}
    private ClientProfile personal(Profile p) {
        var result=new ClientProfile();result.setIdentityType(p.getIdentityType());result.setIssuingCountry(p.getIssuingCountry());result.setIdentityNumber(p.getIdentityNumber());result.setBirthDate(p.getBirthDate());result.setIdentityExpiresOn(p.getIdentityExpiresOn());result.setIdentityNoExpiry(p.isIdentityNoExpiry());result.setAddress(p.getAddress());result.setActivity(p.getActivity());result.setActivityAddress(p.getActivityAddress());result.setSourceOfFunds(p.getSourceOfFunds());return result;
    }
    private void checkDocuments(Profile p,List<Document> documents) {
        check(documents.stream().map(Document::category).collect(java.util.stream.Collectors.toSet()).containsAll(p.isSelfService()?Set.of("IDENTITY","ADDRESS","ACTIVITY"):Set.of("IDENTITY","ADDRESS","ACTIVITY","CONSENT")),"documents");
        check(documents.stream().noneMatch(d->d.expiresOn()!=null&&d.expiresOn().isBefore(today())),"documentExpiry");
    }
    private void validate(Profile p,boolean complete) {
        if(complete) {
            text(p.getFullName(),255,"name",true);
            check(p.getPhone()!=null&&p.getPhone().matches("255[0-9]{9}"),"phone");
        }
        text(p.getIdentityType(),30,"identityType",complete);check(p.getIdentityType().isEmpty()||IDENTITY_TYPES.contains(p.getIdentityType()),"identityType");
        text(p.getIssuingCountry(),2,"issuingCountry",complete);check(p.getIssuingCountry().isEmpty()||Arrays.asList(Locale.getISOCountries()).contains(p.getIssuingCountry()),"issuingCountry");
        text(p.getIdentityNumber(),64,"identityNumber",complete);check(p.getIdentityNumber().isEmpty()||p.getIdentityNumber().matches("[A-Za-z0-9 /-]{4,64}"),"identityNumber");
        check(p.getBirthDate()==null?!complete:!p.getBirthDate().isAfter(today())&&p.getBirthDate().getYear()>=1901,"birthDate");
        check(!(p.isIdentityNoExpiry()&&p.getIdentityExpiresOn()!=null),"identityExpiry");
        if(complete)check(p.isIdentityNoExpiry()||(p.getIdentityExpiresOn()!=null&&!p.getIdentityExpiresOn().isBefore(today())),"identityExpiry");
        text(p.getAddress(),1000,"address",complete);text(p.getActivity(),1000,"activity",complete);text(p.getActivityAddress(),1000,"activityAddress",complete);text(p.getSourceOfFunds(),1000,"sourceOfFunds",complete);
        text(p.getNoticeVersion(),100,"noticeVersion",complete);text(p.getConsentReference(),1000,"consentReference",complete);text(p.getLawfulBasis(),30,"lawfulBasis",complete);
        check(p.getLawfulBasis().isEmpty()||Set.of("CONSENT","CONTRACT","LEGAL_OBLIGATION").contains(p.getLawfulBasis()),"lawfulBasis");
        check(p.getConsentOn()==null?!complete:!p.getConsentOn().isAfter(today())&&p.getConsentOn().getYear()>=1901,"consentOn");
        text(p.getRetentionReference(),500,"retentionReference",complete);text(p.getLegalHoldReference(),500,"legalHoldReference",false);
        if(complete)check(p.getRetentionReviewOn()!=null&&!p.getRetentionReviewOn().isBefore(today()),"retentionReviewOn");
        if(complete&&p.isSelfService())check(p.isNoticeAcknowledged()&&p.isDeclarationAccepted()&&p.getAcknowledgedAt()!=null&&p.getNoticeId()!=null&&!p.getNoticeEnglish().isBlank()&&!p.getNoticeKiswahili().isBlank(),"acknowledgement");
    }
    private Download prepare(MultipartFile f) {
        check(f!=null&&!f.isEmpty()&&f.getSize()<=5*1024*1024,"file");
        try {
            byte[] bytes;try(var input=f.getInputStream()){bytes=input.readNBytes(5*1024*1024+1);}check(bytes.length>0&&bytes.length<=5*1024*1024,"file");
            String name=Objects.toString(f.getOriginalFilename(),"").toLowerCase(Locale.ROOT);
            if(name.endsWith(".pdf")&&new String(bytes,0,Math.min(5,bytes.length),StandardCharsets.US_ASCII).equals("%PDF-")) {
                try(var pdf=Loader.loadPDF(bytes)){check(!pdf.isEncrypted()&&pdf.getNumberOfPages()>0&&pdf.getNumberOfPages()<=40,"file");}
                return new Download(bytes,"application/pdf","evidence.pdf");
            }
            boolean png=name.endsWith(".png"),jpg=name.endsWith(".jpg")||name.endsWith(".jpeg");check(png||jpg,"file");
            try(var in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(in);check(readers.hasNext(),"file");var reader=readers.next();
                try {reader.setInput(in);String format=reader.getFormatName();check((png&&format.equalsIgnoreCase("png"))||(jpg&&format.equalsIgnoreCase("JPEG")),"file");check((long)reader.getWidth(0)*reader.getHeight(0)<=9_000_000,"file");check(reader.read(0)!=null,"file");}finally{reader.dispose();}
            }
            return new Download(bytes,png?"image/png":"image/jpeg",png?"evidence.png":"evidence.jpg");
        } catch(IOException|RuntimeException e) {if(e instanceof IllegalArgumentException i&&Objects.toString(i.getMessage(),"").startsWith("kyc.error."))throw i;throw invalid("file");}
    }
    private Optional<UUID> retry(AppUserPrincipal a,String table,UUID key,String hash) {check(key!=null,"request");records.lock(table+"/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);return records.retry(a,table,key,hash);}
    private void event(AppUserPrincipal a,UUID client,String action) {audit.logEvent("CLIENT_KYC",client,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"CLIENT",client.toString(),a.getSaccoId(),a.getStationId(),Map.of());}
    private String identity(Profile p) {if(p.getIdentityType().isBlank()||p.getIssuingCountry().isBlank()||p.getIdentityNumber().isBlank())return "";return digest((p.getIdentityType()+"/"+p.getIssuingCountry()+"/"+p.getIdentityNumber().replaceAll("[ /-]","").toUpperCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));}
    private String hash(Object value) {return digest(value instanceof byte[] b?b:json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));}
    private static String digest(byte[] bytes) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void page(int n) {check(n>=0&&n<=10000,"query");}
    private static void text(String v,int max,String field,boolean required) {check(v!=null&&v.length()<=max&&(!required||!v.isBlank())&&v.codePoints().noneMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'),field);}
    private static void check(boolean condition,String key) {if(!condition)throw invalid(key);}
    private static IllegalArgumentException invalid(String key) {return new IllegalArgumentException("kyc.error."+key);}
}
