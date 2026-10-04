package com.sacco.mvp.accounting.business.service;

import com.sacco.mvp.accounting.business.dto.BusinessOpeningDtos.*;
import com.sacco.mvp.accounting.business.repository.BusinessOpeningRepository;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.Page;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.sacco.mvp.accounting.business.service.BusinessOpeningImport.*;

@Service @RequiredArgsConstructor
public class BusinessOpeningService {
    private final BusinessOpeningRepository repo;
    private final BusinessSourceProofAuthorization authorization;
    private final ApplicationClock clock;
    private final AuditService audit;
    public Preview preview(AppUserPrincipal actor,Command command,MultipartFile file){authorize(actor,UserClaim.ACCOUNTING_BUSINESS_CREATE);require(command!=null&&command.through()!=null&&!command.through().isAfter(clock.today()),"dates");return parse(command,file).preview();}
    @Transactional
    public Opening importFile(AppUserPrincipal actor,Command command,MultipartFile file){
        authorize(actor,UserClaim.ACCOUNTING_BUSINESS_CREATE);require(command!=null&&command.through()!=null&&!command.through().isAfter(clock.today()),"dates");var parsed=parse(command,file);
        repo.lockRequest(actor.getSaccoId(),actor.getStationId(),command.requestKey());
        var prior=repo.byRequest(actor.getSaccoId(),actor.getStationId(),command.requestKey());
        if(prior.isPresent()){var o=opening(actor,prior.get(),false);require(o.maker().equals(actor.getMemberId())&&repo.payload(o.preview()).equals(repo.payload(parsed.preview())),"retry");return o;}
        UUID id=UUID.randomUUID();repo.insert(id,actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),parsed,clock.now());event(actor,id,"SOURCE_OPENING_IMPORTED");return opening(actor,id,false);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Opening review(AppUserPrincipal actor,UUID id,String decision,String evidence,boolean confirmed){
        authorize(actor,UserClaim.ACCOUNTING_BUSINESS_APPROVE);text(evidence,500);require(decision!=null&&Set.of("APPROVED","REJECTED").contains(decision)&&confirmed,"confirmation");
        var o=opening(actor,id,true);require(!o.maker().equals(actor.getMemberId()),"checker");
        if(o.decision()!=null){require(o.reviewer().equals(actor.getMemberId())&&o.decision().equals(decision)&&o.reviewEvidence().equals(evidence),"retry");return o;}
        require(!clock.now().isBefore(o.importedAt()),"dates");verify(o,repo.file(o.institution(),o.branch(),o.id()).orElseThrow(()->invalid("file")));
        if("APPROVED".equals(decision))repo.requireReviewable(id);
        repo.review(o,actor.getMemberId(),decision,evidence,clock.now());event(actor,id,"SOURCE_OPENING_"+decision);return opening(actor,id,false);
    }
    @Transactional(readOnly=true) public Opening view(AppUserPrincipal actor,UUID id){authorize(actor,UserClaim.ACCOUNTING_BUSINESS_VIEW);return opening(actor,id,false);}
    @Transactional(readOnly=true) public File file(AppUserPrincipal actor,UUID id){authorize(actor,UserClaim.ACCOUNTING_BUSINESS_VIEW);var o=opening(actor,id,false);var file=repo.file(o.institution(),o.branch(),id).orElseThrow(()->invalid("file"));verify(o,file);return file;}
    @Transactional(readOnly=true) public Page<Summary> list(AppUserPrincipal actor,int page){authorize(actor,UserClaim.ACCOUNTING_BUSINESS_VIEW);require(page>=0&&page<=10000,"validation");var rows=repo.list(actor.getSaccoId(),actor.getStationId(),page*25);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    private Opening opening(AppUserPrincipal actor,UUID id,boolean lock){return repo.opening(actor.getSaccoId(),actor.getStationId(),id,lock).orElseThrow(()->new AccessDeniedException("Source opening outside current workspace"));}
    private void authorize(AppUserPrincipal actor,UserClaim claim){authorization.authorizeBusiness(actor,claim);}
    private void verify(Opening o,File file){
        require(sha(file.bytes()).equals(file.checksum())&&o.preview().fileChecksum().equals(file.checksum())
            &&sha(repo.payload(o.preview()).getBytes(StandardCharsets.UTF_8)).equals(o.payloadChecksum()),"integrity");
    }
    private void event(AppUserPrincipal actor,UUID id,String action){audit.logEvent("ACCOUNTING",id,action,actor.getMemberId(),AuditEventStatus.SUCCESS,action,"SOURCE_OPENING",id.toString(),actor.getSaccoId(),actor.getStationId(),Map.of());}
}
