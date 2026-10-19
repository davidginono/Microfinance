package com.sacco.mvp.accounting.payroll;

import com.sacco.mvp.accounting.payroll.dto.PayrollDtos.*;
import com.sacco.mvp.accounting.payroll.service.PayrollPdfService;
import com.sacco.mvp.service.ReportExportLimiter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PayrollPdfTest {
    @Test void groupedEmployeeScheduleIsBilingualMultipageAndOmitsInternalFinancialDetails()throws Exception {
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
        var pdf=new PayrollPdfService(messages,new ReportExportLimiter(1));var lines=new ArrayList<Line>();
        UUID batchId=UUID.randomUUID();
        for(int n=0;n<100;n++){UUID client=UUID.randomUUID();lines.add(line(client,"EMP-"+n,"Synthetic Employee With A Long Name "+n));if(n==0)lines.add(line(client,"EMP-0","Synthetic Employee With A Long Name 0"));}
        var batch=new Batch(batchId,"Synthetic Employer With A Long Name",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),false,OffsetDateTime.parse("2026-10-19T12:00:00+03:00"),"INTERNAL_ACTOR","Synthetic Institution","Synthetic Branch","hash",null,"FROZEN",new BigDecimal("10100.00"),100,0);
        assertThat(PayrollPdfService.group(batchId,lines)).hasSize(100);assertThat(PayrollPdfService.group(batchId,lines).getFirst().amount()).isEqualByComparingTo("200.00");
        for(var language:List.of("en","sw")) {
            var bytes=pdf.render(batch,lines,Locale.forLanguageTag(language));
            try(var document=Loader.loadPDF(bytes)) {
                assertThat(document.getNumberOfPages()).isGreaterThan(1);var text=new PDFTextStripper().getText(document);
                assertThat(text).contains("EMP-0","EMP-99","10,100.00","31/10/2026");
                assertThat(text).doesNotContain("SECRET_LOAN_NUMBER","INTERNAL_ACTOR","SECRET_CLIENT_NUMBER");
                var expression=jakarta.el.ExpressionFactory.newInstance();var el=new jakarta.el.StandardELContext(expression);
                el.getVariableMapper().setVariable("batch",expression.createValueExpression(batch,Batch.class));
                String reference=expression.createValueExpression(el,"${batch.reference}",String.class).getValue(el);
                assertThat(reference).isEqualTo(batch.reference());
            }
            String output=System.getProperty("microfinance.payroll.pdf-output");if(output!=null){Files.createDirectories(Path.of(output));Files.write(Path.of(output,"payroll-"+language+".pdf"),bytes);}
        }
    }
    private Line line(UUID client,String employee,String name){return new Line(UUID.randomUUID(),UUID.randomUUID(),client,employee,name,"SECRET_CLIENT_NUMBER","SECRET_LOAN_NUMBER",new BigDecimal("90.00"),new BigDecimal("10.00"),new BigDecimal("100.00"),"");}
}
