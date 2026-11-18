package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportProductControllerTest {
    @Test void productSelectionSurvivesParametersPreviewAndEveryExport()throws Exception {
        var f=new ManagementControllerTest();f.setup();UUID id=UUID.randomUUID();
        var choice=new Choice(id,"","Education Loan");var kind=Kind.DISBURSED_LOANS_AUDIT;
        String url="/reports/financial/operations/disbursed-loans-audit";
        when(f.reports.choices(f.actor,kind,"products","Education",0)).thenReturn(List.of(choice));
        when(f.reports.selectedProduct(f.actor,kind,id)).thenReturn(choice);
        f.mvc.perform(get(url+"/choices/products").param("search","Education")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id.toString()));
        f.mvc.perform(get(url).param("productId",id.toString())).andExpect(status().isOk()).andExpect(model().attribute("productChoice",choice));
        var p=new Parameters(LocalDate.of(2026,10,1),ManagementReportTest.DAY,ManagementReportTest.NOW,null,null,null,"Education Loan",0,id);
        var result=new Result(kind,p,"Institution","Branch",List.of(),List.of(),List.of(),Map.of(),0,0,false,"","");
        when(f.reports.report(eq(f.actor),eq(kind),any(),anyBoolean())).thenReturn(result);
        when(f.excel.build(eq(result),any())).thenReturn(new byte[]{2});
        when(f.pdf.build(eq(result),any())).thenReturn(new byte[]{1});
        for(String suffix:List.of("","/pdf","/print","/excel"))f.mvc.perform(get(url+suffix).locale(Locale.ENGLISH).param("productId",id.toString()).param("run","true")).andExpect(status().isOk());
        var captured=ArgumentCaptor.forClass(Parameters.class);verify(f.reports,times(4)).report(eq(f.actor),eq(kind),captured.capture(),anyBoolean());
        assertThat(captured.getAllValues()).allSatisfy(value->assertThat(value.productId()).isEqualTo(id));
    }
}
