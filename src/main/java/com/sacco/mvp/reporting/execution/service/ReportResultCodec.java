package com.sacco.mvp.reporting.execution.service;

import com.sacco.mvp.reporting.OperationalReportDefinition;
import com.sacco.mvp.reporting.OperationalReportDefinition.Field;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Values remain explicitly typed by the frozen field catalog; decimals are never parsed through doubles. */
@Component
@RequiredArgsConstructor
public class ReportResultCodec {
 private final ObjectMapper mapper;
 public String rows(List<Map<String,Object>> rows){
  return mapper.writeValueAsString(rows.stream().map(row->{Map<String,String> cells=new LinkedHashMap<>();row.forEach((field,value)->cells.put(field,value==null?null:value instanceof BigDecimal decimal?decimal.toPlainString():value.toString()));return cells;}).toList());
 }
 public List<Map<String,Object>> rows(String json,OperationalReportDefinition definition){
  List<Map<String,String>> encoded=mapper.readValue(json,new TypeReference<>(){});List<Map<String,Object>> rows=new ArrayList<>();
  for(var encodedRow:encoded){Map<String,Object> cells=new LinkedHashMap<>();for(var cell:encodedRow.entrySet()){Field field=Field.valueOf(cell.getKey());if(!OperationalReportDefinition.fields(definition.dataset()).contains(field))throw new IllegalStateException("Frozen field unavailable");String value=cell.getValue();cells.put(cell.getKey(),value==null?null:switch(field.getType()){case MONEY->new BigDecimal(value);case DATE->LocalDate.parse(value);case TEXT->value;});}rows.add(Collections.unmodifiableMap(cells));}return List.copyOf(rows);
 }
 public String totals(Map<String,BigDecimal> totals){Map<String,String> encoded=new TreeMap<>();totals.forEach((field,value)->encoded.put(field,value==null?null:value.toPlainString()));return mapper.writeValueAsString(encoded);}
 public Map<String,BigDecimal> totals(String json){Map<String,String> encoded=mapper.readValue(json,new TypeReference<>(){});Map<String,BigDecimal> totals=new LinkedHashMap<>();encoded.forEach((key,value)->totals.put(key,value==null?null:new BigDecimal(value)));return Collections.unmodifiableMap(totals);}
}
