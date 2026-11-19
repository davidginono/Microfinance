package com.sacco.mvp.accounting.pettycash.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class PettyCashDtos {
    private PettyCashDtos() {}
    public record Fund(UUID accountId,String code,String name,LocalDate startsOn,String basis,UUID openingJournalId,String evidence) {}
    public record Query(LocalDate from,LocalDate through,String view,String search,String sort,int page,int size) {}
    public record Balances(BigDecimal opening,BigDecimal current,BigDecimal closing) {}
    public record Row(UUID id,LocalDate date,UUID voucherId,String voucherKind,String number,String party,String account,
                      String description,BigDecimal paid,BigDecimal moneyIn,BigDecimal moneyOut,BigDecimal balance,String state) {}
    public record Pending(UUID id,String number,String state) {
        public String iconState(){return switch(state){
            case "NOT_REQUIRED" -> "DRAFT";
            case "REQUIRED","HOLD" -> "PENDING";
            case "INITIATED" -> "IN_REVIEW";
            case "WORKFLOW_APPROVED","MANUALLY_APPROVED" -> "APPROVED";
            case "WITHDRAWN" -> "CANCELLED";
            default -> state;
        };}
    }
    public record Workspace(Fund fund,Query query,Balances balances,List<Row> rows,boolean hasNext,
                            OffsetDateTime cutoff,LocalDate today) {}
    public record Setup(UUID accountId,String evidence,boolean enabled) {}
    public record Choice(UUID id,String code,String name) {}
}
