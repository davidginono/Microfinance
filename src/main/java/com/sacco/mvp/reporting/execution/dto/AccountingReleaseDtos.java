package com.sacco.mvp.reporting.execution.dto;

import java.time.OffsetDateTime;
import java.util.*;

/** Proposal commands name retained records; they never carry financial amounts or source-verification flags. */
public final class AccountingReleaseDtos {
 private AccountingReleaseDtos() { }
 public enum Stage { ACCOUNTANT, COMPLIANCE, STAFF }
 public record Proposal(UUID requestKey,UUID statementOutput,UUID firstSample,UUID secondSample,String evidence) { }
 public record Decision(Stage stage,boolean approved,UUID reviewer,String evidence,OffsetDateTime at) { }
 public record Release(UUID id,String institution,String branch,UUID requester,OffsetDateTime requestedAt,
  UUID policy,int policyVersion,UUID period,UUID mapping,UUID template,UUID statementOutput,UUID firstSample,UUID secondSample,
  String dependencyJson,String checksum,String evidence,List<Decision> decisions,boolean invalidated) {
  public Release { decisions=List.copyOf(decisions); }
  public String getState(){return state();}
  public String state(){if(invalidated)return "INVALIDATED";if(decisions.stream().anyMatch(d->!d.approved()))return "REJECTED";return decisions.size()==3?"APPROVED":"PENDING";}
 }
}
