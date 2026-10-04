package com.sacco.mvp.reporting.execution;

import com.sacco.mvp.accounting.statements.StatementDefinition;
import com.sacco.mvp.accounting.statements.StatementDesignerService.RowValue;
import com.sacco.mvp.reporting.execution.dto.StatementOutputDtos.Output;
import com.sacco.mvp.reporting.execution.repository.StatementOutputRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatementOutputPayloadBoundaryTest {
 @Test void multibyteProvenanceCannotBypassArchiveByteLimit(){
  var base=StatementTypedExporterTest.result();String note="é".repeat(4*1024*1024);
  var row=new RowValue("RETAINED_PROOF","Required source","Chanzo cha lazima",StatementDefinition.RowKind.NOTE,StatementDefinition.Unit.NONE,null,null,"TEXT","TEXT",true,false,true,note,"");
  var result=StatementTypedExporterTest.withRows(base,List.of(row));var mapper=JsonMapper.builder().findAndAddModules().build();String json=mapper.writeValueAsString(result);
  assertThat(json.length()).isLessThan(8*1024*1024);assertThat(json.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(8*1024*1024);
  var output=mock(Output.class);when(output.result()).thenReturn(result);var jdbc=mock(JdbcTemplate.class);
  assertThatThrownBy(()->new StatementOutputRepository(jdbc,mapper).create(output)).hasMessage("accounting.release.error.size");
  verifyNoInteractions(jdbc);
 }
}
