package com.sacco.mvp.reporting.execution.dto;

import java.util.*;

/** Public release display; detailed foreign source proofs remain in the immutable internal receipt. */
public final class AccountingReleaseDisplay {
 private AccountingReleaseDisplay(){}
 public static Map<String,Object> project(Map<?,?> receipt,String ownBranch,boolean closingView){
  var out=new LinkedHashMap<String,Object>();
  for(String key:List.of("schemaVersion","institution","branch","dimension","policy","mapping","closeId","closeVersion","closeChecksum","statementSourceChecksum","statementOutputReviewEvidence","firstSample","secondSample","mandatoryDisclosures"))if(receipt.containsKey(key))out.put(key,receipt.get(key));
  if(receipt.get("opening") instanceof Map<?,?> opening)out.put("opening",closingView?opening:pick(opening,List.of("through","payloadChecksum")));
  if(closingView&&receipt.get("reviewedDifferences")!=null&&!"INSTITUTION".equals(receipt.get("dimension")))out.put("reviewedDifferences",receipt.get("reviewedDifferences"));
  for(String key:List.of("currentSource","comparisonSource")){if(!(receipt.get(key) instanceof Map<?,?> source))continue;var view=pick(source,List.of("role","dimension","period","from","through","checksum","retainedDifferenceCount"));
   view.put("cashVersionCount",source.get("cashVersions") instanceof List<?> versions?versions.size():0);
   var branches=new ArrayList<Map<String,Object>>();if(source.get("branches") instanceof List<?> pins)for(Object pin:pins)if(pin instanceof Map<?,?> fields)branches.add(pick(fields,List.of("branch","reviewId","version","recordedCutoff","checksum","openingChecksum")));view.put("branches",List.copyOf(branches));
   var openings=new ArrayList<Object>();if(closingView&&source.get("reviewedOpenings") instanceof List<?> values)for(Object value:values)if(value instanceof Map<?,?> entry&&Objects.equals(ownBranch,entry.get("branch")))openings.add(entry);view.put("reviewedOpenings",List.copyOf(openings));
   view.put("reviewedDifferences",closingView&&"BRANCH".equals(source.get("dimension"))&&source.get("reviewedDifferences") instanceof List<?> values?values:List.of());out.put(key,Collections.unmodifiableMap(view));
  }return Collections.unmodifiableMap(out);
 }
 private static LinkedHashMap<String,Object> pick(Map<?,?> source,List<String> keys){var out=new LinkedHashMap<String,Object>();for(String key:keys)if(source.containsKey(key))out.put(key,source.get(key));return out;}
}
