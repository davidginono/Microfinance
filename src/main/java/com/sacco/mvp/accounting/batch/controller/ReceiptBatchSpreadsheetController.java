package com.sacco.mvp.accounting.batch.controller;

import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.ImportRowError;
import com.sacco.mvp.accounting.batch.service.ReceiptBatchService;
import com.sacco.mvp.accounting.batch.service.ReceiptBatchWorkbook;
import com.sacco.mvp.accounting.batch.service.ReceiptBatchWorkerService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ReportExportLimiter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/finance/batches/receipts/{id}/spreadsheet")
@PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_VIEW') and @access.has(principal,'LOAN_RECORDING_VIEW')"
    + " and @access.has(principal,'ACCOUNTING_BUSINESS_CREATE') and @access.has(principal,'LOAN_RECORDING_POST')")
public class ReceiptBatchSpreadsheetController {
    private static final String VIEW = "accounting/batches/spreadsheet";
    private final ReceiptBatchService batches;
    private final ReceiptBatchWorkbook workbook;
    private final LoanRecordingService loans;
    private final ReportExportLimiter limiter;
    private final MessageSource messages;

    @ModelAttribute
    void cache(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    @GetMapping
    public String page(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable UUID id, Model model) {
        model.addAttribute("batch", batches.spreadsheetBatch(actor, id));
        return VIEW;
    }

    @GetMapping("/template.xlsx")
    public ModelAndView template(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable UUID id,
                                 @RequestParam(defaultValue = "") String search, Locale locale,
                                 HttpServletResponse response, Model model) throws IOException {
        page(actor, id, model);
        byte[] file;
        try {
            file = limiter.run(() -> workbook.write(id, batches.templateLoans(actor, id, search), locale));
        } catch (IllegalArgumentException failure) {
            response.setStatus(400);
            model.addAttribute("spreadsheetError", ReceiptBatchWorkerService.safeError(failure));
            model.addAttribute("search", search.substring(0, Math.min(search.length(), 100)));
            return new ModelAndView(VIEW, model.asMap());
        } catch (ResponseStatusException busy) {
            response.setStatus(busy.getStatusCode().value());
            model.addAttribute("spreadsheetError", "batch.error.importBusy");
            return new ModelAndView(VIEW, model.asMap());
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename("repayment-entry.xlsx").build().toString());
        response.setContentLength(file.length);
        response.getOutputStream().write(file);
        return null;
    }

    @PostMapping(consumes = "multipart/form-data")
    public String upload(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable UUID id,
                         @RequestParam(required = false) UUID moneyAccountId, @RequestParam MultipartFile file,
                         HttpServletResponse response, Model model, RedirectAttributes redirect) {
        page(actor, id, model);
        try {
            if (moneyAccountId == null) throw new IllegalArgumentException("batch.error.importAccount");
            model.addAttribute("selectedAccount", loans.receiptMoneyAccount(actor, moneyAccountId));
            if (file.isEmpty() || file.getSize() > ReceiptBatchWorkbook.MAX_BYTES) {
                throw new IllegalArgumentException("batch.error.importFile");
            }
            var rows = limiter.run(() -> {
                try (var input = file.getInputStream()) {
                    return workbook.read(id, input.readNBytes(ReceiptBatchWorkbook.MAX_BYTES + 1));
                } catch (IOException failure) {
                    throw new IllegalArgumentException("batch.error.importFile", failure);
                }
            });
            int imported = batches.importDrafts(actor, id, moneyAccountId, rows);
            redirect.addFlashAttribute("importedReceiptCount", imported);
            return "redirect:/finance/batches/receipts/" + id;
        } catch (IllegalArgumentException failure) {
            response.setStatus(400);
            model.addAttribute("spreadsheetError", ReceiptBatchWorkerService.safeError(failure));
            if (failure instanceof ImportRowError row) model.addAttribute("spreadsheetErrorLine", row.line());
            return VIEW;
        } catch (ResponseStatusException busy) {
            response.setStatus(busy.getStatusCode().value());
            model.addAttribute("spreadsheetError", "batch.error.importBusy");
            return VIEW;
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> unavailable(IllegalArgumentException failure, Locale locale) {
        return ResponseEntity.status(409).body(messages.getMessage(ReceiptBatchWorkerService.safeError(failure), null, locale));
    }
}
