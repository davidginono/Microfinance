package com.sacco.mvp.accounting.payroll.service;

import com.sacco.mvp.accounting.payroll.dto.PayrollDtos.*;
import com.sacco.mvp.accounting.reports.AccountingPdfDocument;
import com.sacco.mvp.service.ReportExportLimiter;
import com.sacco.mvp.service.ApplicationTimestamps;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PayrollPdfService {
    private final MessageSource messages;
    private final ReportExportLimiter limiter;

    public byte[] render(Batch batch,List<Line> lines,Locale locale) {
        if(lines.size()>1000)throw new IllegalArgumentException("payroll.error.large");
        return limiter.run(()->build(batch,lines,locale));
    }
    private byte[] build(Batch b,List<Line> lines,Locale locale) {
        try(var document=new PDDocument();var output=new ByteArrayOutputStream();var input=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream()) {
            var font=PDType0Font.load(document,input);
            var context=List.of(b.institution()+" | "+b.branch(),b.getReference(),label("employer",locale)+": "+b.employer(),
                label("month",locale)+": "+b.month().toString().substring(0,7)+" | "+label("deductionDate",locale)+": "+ApplicationTimestamps.display(b.deductionDate()),
                label("cutoff",locale)+": "+ApplicationTimestamps.display(b.cutoff()),"TZS");
            try(var page=new AccountingPdfDocument(document,font,false,label("title",locale),context,null,AccountingPdfDocument.Style.ORIGINAL)) {
                page.text(label("requestOnly",locale));
                page.columns(List.of(label("employeeNumber",locale),label("employeeName",locale),label("deductionReference",locale),label("requested",locale)),new float[]{.18f,.32f,.30f,.20f},new boolean[]{false,false,false,true});
                for(var row:group(b.id(),lines))page.row(List.of(row.employeeNumber(),row.clientName(),row.reference(),amount(row.amount(),locale)),false);
                page.row(List.of(label("total",locale),label("employees",locale)+": "+b.employees(),"",amount(b.total(),locale)),true);
                page.endTable();page.text(label("snapshotNotice",locale));
                if(b.replacesId()!=null)page.text(label("replaces",locale)+": PAY-"+b.replacesId().toString().toUpperCase(Locale.ROOT));
            }
            document.save(output);return output.toByteArray();
        }catch(IOException failure){throw new IllegalStateException("Payroll PDF generation failed",failure);}
    }
    public static List<ExportRow> group(UUID batch,List<Line> lines) {
        var grouped=new LinkedHashMap<UUID,List<Line>>();
        for(var line:lines)if(line.issue().isEmpty() && line.amount().signum()>0)grouped.computeIfAbsent(line.clientId(),ignored->new ArrayList<>()).add(line);
        var result=new ArrayList<ExportRow>();
        for(var entry:grouped.entrySet()) {
            var first=entry.getValue().getFirst();String reference=UUID.nameUUIDFromBytes((batch+"/"+entry.getKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().toUpperCase(Locale.ROOT);
            result.add(new ExportRow(first.employeeNumber(),first.clientName(),reference,entry.getValue().stream().map(Line::amount).reduce(new BigDecimal("0.00"),BigDecimal::add)));
        }
        return result;
    }
    private String label(String key,Locale locale) {return messages.getMessage("payroll."+key,null,locale);}
    private String amount(BigDecimal value,Locale locale) {var f=NumberFormat.getNumberInstance(locale);f.setMinimumFractionDigits(2);f.setMaximumFractionDigits(2);return f.format(value);}
}
