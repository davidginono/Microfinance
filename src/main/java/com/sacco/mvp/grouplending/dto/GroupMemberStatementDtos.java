package com.sacco.mvp.grouplending.dto;

import com.sacco.mvp.grouplending.dto.GroupSharedLoanDtos.Balance;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class GroupMemberStatementDtos {
    private GroupMemberStatementDtos() {}
    public record Source(UUID cycle,UUID loan,String loanNumber,String institution,String branch,String groupCode,String groupName,String status,String liability,BigDecimal cap,BigDecimal deductions,BigDecimal net,BigDecimal maximumLiability,Balance member,BigDecimal duePrincipal,BigDecimal dueInterest,BigDecimal arrears,BigDecimal loanCharges,boolean funded,boolean known) {}
    public record Due(int number,LocalDate date,BigDecimal principal,BigDecimal interest){public BigDecimal amount(){return principal.add(interest);}}
    public record Credit(UUID posting,UUID receipt,String number,LocalDate date,String kind,String payer,BigDecimal principal,BigDecimal interest,BigDecimal charges,BigDecimal excludedInterest,BigDecimal waivedInterest,BigDecimal writtenOff,String reverses,String reversedBy){public BigDecimal amount(){return principal.add(interest).add(charges);}}
    public record Statement(Source source,LocalDate asOf,OffsetDateTime generatedAt,LocalDate from,LocalDate through,List<Due> schedule,List<Credit> credits,int page,boolean hasNext,int schedulePage,boolean scheduleNext,boolean staff,boolean receiptLinks,GroupCoverageDtos.Trace coverage) {
        public Statement(Source source,LocalDate asOf,OffsetDateTime generatedAt,LocalDate from,LocalDate through,List<Due> schedule,List<Credit> credits,int page,boolean hasNext,int schedulePage,boolean scheduleNext,boolean staff,boolean receiptLinks){this(source,asOf,generatedAt,from,through,schedule,credits,page,hasNext,schedulePage,scheduleNext,staff,receiptLinks,null);}
    }
}
