package com.sacco.mvp.grouplending.dto;

import com.sacco.mvp.domain.PaymentDestinationType;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class GroupAuthorityDtos {
    private GroupAuthorityDtos() {}
    @Data public static class AuthorityForm {
        private UUID requestKey=UUID.randomUUID();
        private UUID expectedPreviousId;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate validFrom;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate validThrough;
        private BigDecimal maxPrincipal;
        private Integer requiredSignatures;
        private String capacityEvidence="",resolutionEvidence="",signatureEvidence="";
        private PaymentDestinationType payoutType;
        private String payoutProvider="",payoutHolder="",payoutIdentifier="",payoutEvidence="";
        private List<SignerForm> signers=new ArrayList<>();
        private boolean verified;
    }
    @Data public static class SignerForm {
        private UUID clientId;
        private boolean mayApply,maySign,mayReceive,mayViewAccount;
    }
    @Data public static class RevocationForm { private String reason=""; }
    public record Signer(UUID clientId,String number,String name,boolean mayApply,boolean maySign,boolean mayReceive,boolean mayViewAccount) {}
    public record Summary(UUID id,int revision,LocalDate validFrom,LocalDate validThrough,BigDecimal maxPrincipal,String state) {}
    public record Editor(GroupLendingDtos.Group group,AuthorityForm form,Map<UUID,Signer> selected) {}
    public record History(GroupLendingDtos.Group group,GroupLendingDtos.Page<Summary> page) {}
    public record Authority(UUID id,UUID groupId,int revision,UUID previousId,String groupName,String groupCode,String legalForm,
        LocalDate validFrom,LocalDate validThrough,BigDecimal maxPrincipal,int requiredSignatures,
        String capacityEvidence,String resolutionEvidence,String signatureEvidence,PaymentDestinationType payoutType,
        String payoutProvider,String payoutHolder,String payoutIdentifier,String payoutEvidence,
        UUID recordedBy,String recordedByName,OffsetDateTime recordedAt,String state,
        String revocationReason,UUID revokedBy,String revokedByName,OffsetDateTime revokedAt,List<Signer> signers) {}
}
