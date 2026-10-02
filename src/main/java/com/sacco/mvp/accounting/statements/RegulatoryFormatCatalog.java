package com.sacco.mvp.accounting.statements;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.service.ApplicationClock;
import java.nio.file.*;
import java.util.*;
import java.time.LocalDate;
import static com.sacco.mvp.accounting.statements.StatementDefinition.*;

/** Deployment-owned registry. No institution HTTP route can create, amend or replace prescribed fields. */
@Service @RequiredArgsConstructor
public class RegulatoryFormatCatalog {
    private final StatementRepository repository;
    private final ObjectMapper mapper;
    private final ApplicationClock clock;
    @Value("${app.accounting.regulatory-format-manifest:}") private String manifestPath;
    @Value("${app.accounting.regulatory-format-sha256:}") private String manifestChecksum;
    public enum FileFormat { CSV, XLSX, PDF }
    public enum Period { MONTH, QUARTER, YEAR }
    public record Field(String key,String labelEn,String labelSw,Unit unit,String sourceLine) { }
    public record Calculation(String key,Unit unit,Expression expression) { }
    public record Format(int schemaVersion,String authority,String key,String version,String officialReference,
            String applicabilityEvidence,Kind kind,FileFormat fileFormat,Period period,int deadlineDays,List<Field> fields,List<Calculation> calculations) {
        public void validate(){
            require(schemaVersion==1 && kind!=null && fileFormat!=null && period!=null && deadlineDays>=0 && deadlineDays<=366,"officialVersion");
            text(authority,120);text(key,80);text(version,40);text(applicabilityEvidence,1000);
            require(key.matches("[A-Z][A-Z0-9_]{0,79}") && version.matches("[A-Za-z0-9._-]{1,40}"),"officialVersion");
            require(officialReference!=null && officialReference.length()<=1000 && officialReference.matches("https://(?:www\\.)?(?:bot\\.go\\.tz|nbaa\\.go\\.tz)/[^\\s<>]+"),"officialReference");
            require(fields!=null && !fields.isEmpty() && fields.size()<=100 && calculations!=null && calculations.size()<=50,"size");var keys=new HashSet<String>();
            for(var field:fields){require(field!=null && field.key()!=null && field.key().matches("[A-Z][A-Z0-9_]{0,39}") && keys.add(field.key())
                && field.unit()!=null && field.unit()!=Unit.NONE && field.sourceLine()!=null && field.sourceLine().matches("[A-Z][A-Z0-9_]{0,39}"),"line");text(field.labelEn(),120);text(field.labelSw(),120);}
            var protectedRows=new ArrayList<Row>();var sources=List.of("ASSETS","LIABILITIES","EQUITY","INCOME","EXPENSES","PROFIT","OPENING_EQUITY","EQUITY_MOVEMENT","CASH_OPENING","CASH_CLOSING","CASH_MOVEMENT","RECONCILIATION_DIFFERENCE","LOAN_PRINCIPAL","INTEREST_RECEIVABLE","FEE_RECEIVABLE","ALLOWANCE","NET_LOANS","CASH","BANK","MOBILE_MONEY","PAYABLE","FUNDING","CAPITAL");
            for(String source:sources)protectedRows.add(new Row("REQUIRED_"+source,source,source,RowKind.ACCOUNT_GROUP,Section.NOTES,Unit.TZS,List.of(UUID.nameUUIDFromBytes(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))),Sign.DEBIT_POSITIVE,null,null,null,true,false,false,null));
            for(var c:calculations){require(c!=null && c.key()!=null && c.key().startsWith("REG_") && keys.add(c.key()) && c.unit()!=null && c.expression()!=null,"expression");protectedRows.add(new Row(c.key(),c.key(),c.key(),c.unit()==Unit.RATIO?RowKind.RATIO:RowKind.SUBTOTAL,Section.NOTES,c.unit(),List.of(),null,c.expression(),null,null,true,false,false,null));}
            new StatementDefinition(1,1,Kind.NOTES,"Official calculations","Mahesabu rasmi",protectedRows,List.of(),false).validate();
            for(var f:fields){var row=protectedRows.stream().filter(r->r.id().equals(f.sourceLine())).findFirst().orElseThrow(()->new IllegalArgumentException("statement.error.officialField"));require(row.unit()==f.unit(),"units");}
        }
        public LocalDate deadline(LocalDate from,LocalDate through){
            require(from!=null && through!=null && from.getDayOfMonth()==1,"officialPeriod");
            int months=switch(period){case MONTH->1;case QUARTER->3;case YEAR->12;};
            require(through.equals(from.plusMonths(months).minusDays(1)) && (period!=Period.YEAR || from.getMonthValue()==1)
                && (period!=Period.QUARTER || (from.getMonthValue()-1)%3==0),"officialPeriod");return through.plusDays(deadlineDays);
        }
    }
    /** A verified local manifest is installed explicitly by release configuration, never auto-discovered or downloaded. */
    @EventListener(ApplicationReadyEvent.class) @Transactional
    public void installConfiguredManifest() throws java.io.IOException {
        if(manifestPath==null || manifestPath.isBlank())return;
        require(manifestChecksum!=null && manifestChecksum.matches("[a-f0-9]{64}"),"officialChecksum");
        Path path=Path.of(manifestPath);require(path.isAbsolute() && Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) && Files.size(path)<=200000,"size");
        byte[] bytes=Files.readAllBytes(path);require(StatementDesignerService.sha(bytes).equals(manifestChecksum),"officialChecksum");
        String json=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);Format format=mapper.readValue(json,Format.class);format.validate();
        repository.installOfficial(UUID.randomUUID(),format.authority(),format.key(),format.version(),json,manifestChecksum,format.officialReference(),format.applicabilityEvidence(),clock.now());
    }
    public Format get(UUID id){var f=repository.format(id);require(StatementDesignerService.sha(f.json().getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(f.checksum()),"officialChecksum");Format format=mapper.readValue(f.json(),Format.class);format.validate();return format;}
}
