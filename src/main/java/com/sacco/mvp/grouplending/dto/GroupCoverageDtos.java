package com.sacco.mvp.grouplending.dto;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class GroupCoverageDtos {
 private GroupCoverageDtos(){}
 @Data public static class PolicyForm {private UUID requestKey=UUID.randomUUID(),expectedPreviousId;private Integer noticeDays=14,caseDays,promiseDays;private String reference="";private boolean enabled,verified;}
 @Data public static class CallForm {private UUID requestKey=UUID.randomUUID(),sourceId,policyId;private String sourceKind="",borrowerNoticeEvidence="",personNoticeEvidence="",noticeLanguage="";private BigDecimal amount;@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)private LocalDate borrowerNoticeDate,personNoticeDate;private boolean noticesVerified;}
 @Data public static class ResponseForm {private UUID requestKey=UUID.randomUUID();private int expectedSequence;private String kind="",reason="";private BigDecimal amount;@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)private LocalDate promiseOn;private boolean confirmed;}
 @Data public static class LinkForm {private UUID requestKey=UUID.randomUUID(),postingId,contributionId;private String payerEvidence="";private boolean payerVerified;}
 @Data public static class CorrectionForm {private UUID requestKey=UUID.randomUUID();private String reason="";private boolean confirmed;}
 public record Context(UUID cycle,String group,String product){}
 public record CallPreparation(Context context,Source source,Policy policy,BigDecimal maximum){}
 public record NoticeFacts(LocalDate borrowerDate,LocalDate personDate,String borrowerEvidence,String personEvidence,String filename){}
 public record CaseContext(Case header,Policy policy,NoticeFacts notice,LocalDate today){}
 public record Policy(UUID id,int revision,int noticeDays,int caseDays,int promiseDays,String reference,boolean enabled){}
 public record Source(UUID id,String kind,UUID cycle,UUID loan,UUID client,String number,String name,String borrower,String loanNumber,UUID product,BigDecimal cap,String terms,BigDecimal arrears,LocalDate oldestDue,String status,boolean known){}
 public record Case(UUID id,UUID cycle,UUID loan,UUID source,String sourceKind,UUID client,String number,String name,String borrower,String loanNumber,UUID policy,LocalDate callDate,LocalDate expiresOn,LocalDate oldestDue,BigDecimal requested,BigDecimal cap,BigDecimal originalArrears,BigDecimal remainingDue,BigDecimal unpaid,BigDecimal received,String state,int sequence,String terms,String noticeLanguage,String sha256,int pages,OffsetDateTime recordedAt){}
 public record Event(UUID id,int sequence,String kind,BigDecimal amount,LocalDate promiseOn,String reason,OffsetDateTime at){}
 public record PaymentSource(UUID posting,UUID contribution,UUID receipt,String reference,LocalDate date,String payer,UUID payerId,BigDecimal amount,BigDecimal principal,BigDecimal interest,BigDecimal charges,OffsetDateTime recordedAt){}
 public record Payment(UUID id,UUID posting,UUID receipt,String reference,LocalDate date,String payer,BigDecimal amount,BigDecimal principal,BigDecimal interest,BigDecimal charges,BigDecimal net,UUID reversedBy,UUID correctedBy,String evidence,OffsetDateTime linkedAt,UUID recordedBy,String correctionReason,OffsetDateTime correctedAt){}
 public record Detail(Case header,GroupLendingDtos.Page<Event> events,GroupLendingDtos.Page<Payment> payments,boolean staff,Policy policy,NoticeFacts notice,LocalDate today){}
 public record CorrectionContext(UUID actor,UUID correction,UUID correctionActor,UUID requestKey,String reason){}
 public record Trace(List<Case> rows,int page,boolean hasNext,boolean own){}
}
