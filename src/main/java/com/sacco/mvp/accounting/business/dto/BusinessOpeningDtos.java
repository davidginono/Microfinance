package com.sacco.mvp.accounting.business.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Source registers are evidence, never a command to create money or an operational loan history. */
public final class BusinessOpeningDtos {
    private BusinessOpeningDtos() { }
    public record Command(UUID requestKey,UUID account,UUID generalLedgerOpening,LocalDate through,
                          String purpose,String evidence,boolean completeCoverage) { }
    public record Row(String reference,BigDecimal signedBalance,String evidence) { }
    public record Preview(Command command,String filename,String fileChecksum,List<Row> rows,BigDecimal signedBalance) {
        public Preview {rows=List.copyOf(rows);}
    }
    public record Opening(UUID id,String institution,String branch,UUID maker,Preview preview,
                          String payloadChecksum,OffsetDateTime importedAt,String decision,UUID reviewer,
                          String reviewEvidence,OffsetDateTime reviewedAt) { }
    public record Summary(UUID id,UUID account,LocalDate through,String purpose,BigDecimal signedBalance,
                          boolean completeCoverage,String decision,OffsetDateTime importedAt) { }
    public record File(String filename,byte[] bytes,String checksum) {
        public File {bytes=bytes.clone();}
        @Override public byte[] bytes(){return bytes.clone();}
    }
}
