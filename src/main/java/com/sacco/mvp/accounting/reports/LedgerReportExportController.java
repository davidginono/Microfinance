package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/reports/financial")
public class LedgerReportExportController {
    private final LedgerReportExporter exports;
    private final ApplicationClock clock;
    private final MessageSource messages;
    @GetMapping({"/export","/accounts/{accountId}/export"})
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable(required=false) UUID accountId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedThrough,
            @RequestParam(defaultValue="false") boolean institutionWide,
            @RequestParam LedgerReportExporter.Format format,Locale locale) {
        var parameters=new LedgerReportService.Parameters(from,through,recordedThrough.atZone(clock.zoneId()).toOffsetDateTime(),0);
        var result=accountId==null?exports.trial(actor,parameters,institutionWide,format,locale)
            :exports.activity(actor,accountId,parameters,institutionWide,format,locale);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(result.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(result.filename(),StandardCharsets.UTF_8).build().toString())
            .body(result.bytes());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalidSource(IllegalArgumentException failure,Locale locale) {
        String key=failure.getMessage();if(key==null || !key.startsWith("financial.report.error."))key="financial.report.error.policy";
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_PLAIN)
            .body(messages.getMessage(key,null,locale));
    }
}
