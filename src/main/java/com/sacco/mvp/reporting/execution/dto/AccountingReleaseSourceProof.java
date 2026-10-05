package com.sacco.mvp.reporting.execution.dto;

import com.sacco.mvp.accounting.statements.StatementDesignerService.BranchSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Minimal retained provenance from trusted publication snapshots, never request fields. */
public final class AccountingReleaseSourceProof {
 private AccountingReleaseSourceProof() { }
 public record BranchPin(String role,String branch,UUID reviewId,int version,OffsetDateTime recordedCutoff,String checksum,UUID reviewer,UUID openingId,String openingChecksum,UUID openingMaker,UUID openingReviewer) { }
 public record CashPin(String role,UUID journalId,UUID allocationId,int version,String sourceChecksum,String definitionChecksum,UUID reviewer,OffsetDateTime reviewedAt) { }
 public record Proof(String role,String dimension,UUID period,LocalDate from,LocalDate through,String checksum,List<BranchPin> branches,List<CashPin> cashVersions,List<?> reviewedOpenings,List<?> businessControlSources,Object reviewedDifferences,long retainedDifferenceCount) {
  public Proof {branches=List.copyOf(branches);cashVersions=List.copyOf(cashVersions);reviewedOpenings=List.copyOf(reviewedOpenings);businessControlSources=List.copyOf(businessControlSources);}
 }
 public static Proof from(String role,String dimension,UUID period,LocalDate from,LocalDate through,String checksum,List<BranchSource> branches,Map<?,?> snapshot){
  require(Set.of("CURRENT","COMPARISON").contains(role)&&Set.of("BRANCH","INSTITUTION").contains(dimension)&&period!=null&&branches!=null&&!branches.isEmpty()&&branches.size()<=1000);
  require(period.toString().equals(text(snapshot.get("period")))&&from.toString().equals(text(snapshot.get("from")))&&through.toString().equals(text(snapshot.get("through"))));sha(checksum);
  var openings=new TreeMap<String,Map<?,?>>();var retainedOpenings=new ArrayList<Object>();
  if("INSTITUTION".equals(dimension)){require("INSTITUTION".equals(snapshot.get("dimension")));for(var value:list(snapshot.get("reviewedOpenings"),1000)){var entry=object(value);String branch=text(entry.get("branch"));var opening=object(entry.get("opening"));require(openings.put(branch,opening)==null);retainedOpenings.add(entry);}}
  else {require(branches.size()==1);var opening=object(snapshot.get("reviewedOpening"));openings.put(branches.get(0).branch(),opening);retainedOpenings.add(Map.of("branch",branches.get(0).branch(),"opening",opening));}
  require(openings.size()==branches.size());var seen=new HashSet<String>();var pins=new ArrayList<BranchPin>();
  for(var branch:branches.stream().sorted(Comparator.comparing(BranchSource::branch)).toList()){
   require(seen.add(branch.branch())&&branch.reviewId()!=null&&branch.version()>0&&branch.reviewer()!=null&&branch.recordedCutoff()!=null);sha(branch.checksum());var opening=openings.get(branch.branch());require(opening!=null);
   UUID maker=uuid(opening.get("maker")),reviewer=uuid(opening.get("reviewer"));require(!maker.equals(reviewer));String openingHash=sha(text(opening.get("payloadChecksum")));text(opening.get("sourceEvidence"));text(opening.get("reviewEvidence"));
   require(!time(opening.get("reviewedAt")).isAfter(branch.recordedCutoff())&&!time(opening.get("postedAt")).isAfter(branch.recordedCutoff()));
   pins.add(new BranchPin(role,branch.branch(),branch.reviewId(),branch.version(),branch.recordedCutoff(),branch.checksum(),branch.reviewer(),uuid(opening.get("id")),openingHash,maker,reviewer));
  }
  var cash=object(snapshot.get("cashFlowAllocations"));require(list(cash.get("missingJournalIds"),1000).isEmpty());var cashPins=new ArrayList<CashPin>();var journals=new HashSet<UUID>();
  for(var value:list(cash.get("versions"),1000)){var version=object(value);UUID journal=uuid(version.get("journalId")),maker=uuid(version.get("maker")),reviewer=uuid(version.get("checker"));require(journals.add(journal)&&!maker.equals(reviewer));var source=object(version.get("source"));require(journal.equals(uuid(source.get("journalId"))));cashPins.add(new CashPin(role,journal,uuid(version.get("id")),positive(version.get("version")),sha(text(version.get("sourceChecksum"))),sha(text(version.get("definitionChecksum"))),reviewer,time(version.get("reviewedAt"))));}
  cashPins.sort(Comparator.comparing(p->p.journalId().toString()));var controls=list(snapshot.get("businessControlSources"),1000);Object differences;long count;
  if("INSTITUTION".equals(dimension)){count=nonnegative(snapshot.get("retainedDifferenceCount"));differences=List.of();}
  else {var values=new ArrayList<Object>(list(snapshot.get("retainedDifferences"),1000));for(var value:list(snapshot.get("certificates"),1000)){var certificate=object(value);var amount=new BigDecimal(text(certificate.get("difference")));if(amount.signum()!=0){uuid(certificate.get("checker_id"));text(certificate.get("evidence"));values.add(certificate);}}differences=List.copyOf(values);count=values.size();}
  return new Proof(role,dimension,period,from,through,checksum,pins,cashPins,retainedOpenings,controls,differences,count);
 }
 private static int positive(Object value){long n=nonnegative(value);require(n>0&&n<=Integer.MAX_VALUE);return(int)n;}
 private static long nonnegative(Object value){require(value instanceof Number);long n=new BigDecimal(value.toString()).longValueExact();require(n>=0);return n;}
 private static Map<?,?> object(Object value){require(value instanceof Map<?,?>);return(Map<?,?>)value;}
 private static List<?> list(Object value,int maximum){require(value instanceof List<?>);var values=(List<?>)value;require(values.size()<=maximum);return values;}
 private static UUID uuid(Object value){return UUID.fromString(text(value));}
 private static OffsetDateTime time(Object value){return OffsetDateTime.parse(text(value));}
 private static String text(Object value){require(value!=null&&!value.toString().isBlank());return value.toString();}
 private static String sha(String value){require(value!=null&&value.matches("[0-9a-f]{64}"));return value;}
 private static void require(boolean valid){if(!valid)throw new IllegalArgumentException("accounting.release.error.source");}
}
