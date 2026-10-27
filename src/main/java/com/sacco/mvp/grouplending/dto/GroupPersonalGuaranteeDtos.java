package com.sacco.mvp.grouplending.dto;

import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class GroupPersonalGuaranteeDtos {
    private GroupPersonalGuaranteeDtos() {}
    @Data public static class Form {
        private UUID requestKey=UUID.randomUUID(),clientId;
        private BigDecimal capacity;
        private String capacityEvidence="";
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate validThrough;
        private DocumentForm document=new DocumentForm();
    }
    public record Source(UUID cycle,UUID loan,String number,String borrower,String model,String status,int required,
        BigDecimal principal,BigDecimal interest,BigDecimal charges,BigDecimal cap,BigDecimal rate,String basis,BigDecimal installment,String method,String frequency,
        LocalDate firstDue,LocalDate fundedDate,String institution,String financial,BigDecimal maxGuarantee,boolean active) { public BigDecimal ratePercent(){return rate==null?null:rate.movePointRight(2);} }
    public record Person(UUID id,String number,String name) {}
    public record Offer(UUID id,UUID loan,UUID cycle,UUID client,String number,String name,String code,String loanNumber,String borrower,BigDecimal cap,BigDecimal capacity,
        String capacityEvidence,LocalDate validThrough,String hash,String filename,String language,String reference,String sha256,
        long bytes,int pages,String state,OffsetDateTime recordedAt,OffsetDateTime decidedAt,String reason,String signature) {}
    public record Workspace(Source source,GroupLendingDtos.Page<Offer> offers,Form form,Person selected,boolean ready) {}
    public record Packet(Offer offer,Source source,DocumentForm document,boolean opened,boolean otpRequired,boolean signatureReady,boolean open) {}
    public record Evidence(Packet packet,byte[] terms) {}
    public record Delivery(UUID id,UUID offer,UUID loan,UUID client,String institution,String branch,String email,String phone,String code,int ttlMinutes) {}
}
