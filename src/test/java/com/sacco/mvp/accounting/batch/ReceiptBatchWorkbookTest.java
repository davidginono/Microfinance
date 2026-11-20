package com.sacco.mvp.accounting.batch;

import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.ImportRowError;
import com.sacco.mvp.accounting.batch.service.ReceiptBatchWorkbook;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ReceiptEntryLoan;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ReceiptBatchWorkbookTest {
    private final UUID batch = UUID.randomUUID();
    private final UUID firstClient = UUID.randomUUID();
    private final UUID secondClient = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 11, 9);
    private final ReceiptBatchWorkbook workbook;

    ReceiptBatchWorkbookTest() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        workbook = new ReceiptBatchWorkbook(messages);
    }

    @Test
    void dropdownUpdatesLookupAndOnlyActualPaymentColumnsBecomeDraftInput() throws Exception {
        try (var book = template(); var output = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            assertThat(book.isSheetHidden(1)).isTrue();
            assertThat(sheet.isColumnHidden(7)).isTrue();
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            assertThat(sheet.getDataValidations()).singleElement().satisfies(validation -> {
                assertThat(validation.getValidationConstraint().getFormula1()).isEqualTo(
                    "INDIRECT(\"ClientLoans_\"&SUBSTITUTE($H3,\"-\",\"\"))");
                assertThat(validation.getRegions().getCellRangeAddress(0).formatAsString()).isEqualTo("B3:B4");
            });
            assertThat(book.getName("ClientLoans_" + firstClient.toString().replace("-", "")).getRefersToFormula())
                .isEqualTo("LoanList!$A$1:$A$2");
            assertThat(book.getName("ClientLoans_" + secondClient.toString().replace("-", "")).getRefersToFormula())
                .isEqualTo("LoanList!$A$3:$A$3");
            var first = sheet.getRow(2);
            assertThat(first.getCell(0).getStringCellValue()).isEqualTo("001 - First client");
            assertThat(first.getCell(1).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("00456");
            assertThat(first.getCell(4).getCellType()).isEqualTo(CellType.BLANK);
            for (int column = 0; column < 7; column++) {
                assertThat(first.getCell(column).getCellStyle().getBorderBottom()).isEqualTo(BorderStyle.THIN);
                assertThat(first.getCell(column).getCellStyle().getBorderRight()).isEqualTo(BorderStyle.THIN);
            }
            first.getCell(1).setCellValue("00789");
            var evaluator = book.getCreationHelper().createFormulaEvaluator();
            assertThat(evaluator.evaluate(first.getCell(3)).getNumberValue()).isEqualTo(75.25);
            evaluator.evaluateFormulaCell(first.getCell(2));
            assertThat(first.getCell(2).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(date.plusDays(1));
            first.getCell(4).setCellValue("9007199254740993.01");
            first.getCell(5).setCellValue(date.toString());
            first.getCell(6).setCellValue("00007");
            book.write(output);
            assertThat(workbook.read(batch, output.toByteArray())).singleElement().satisfies(row -> {
                assertThat(row.loanNumber()).isEqualTo("00789");
                assertThat(row.clientId()).isEqualTo(firstClient);
                assertThat(row.amount()).isEqualByComparingTo("9007199254740993.01");
                assertThat(row.date()).isEqualTo(date);
                assertThat(row.reference()).isEqualTo("00007");
            });
        }
    }

    @Test
    void wrongBatchAndIncompleteOrFormulaPaymentRowsAreRejected() throws Exception {
        try (var book = template(); var output = new ByteArrayOutputStream()) {
            book.write(output);
            assertThatThrownBy(() -> workbook.read(UUID.randomUUID(), output.toByteArray())).hasMessage("batch.error.importBatch");
            assertThatThrownBy(() -> workbook.read(batch, output.toByteArray())).hasMessage("batch.error.importEmpty");
            var row = book.getSheetAt(0).getRow(2);
            row.getCell(4).setCellFormula("100+20");
            row.getCell(5).setCellValue(date.toString());
            row.getCell(6).setCellValue("REF-01");
            output.reset();
            book.write(output);
            assertThatThrownBy(() -> workbook.read(batch, output.toByteArray())).isInstanceOfSatisfying(ImportRowError.class,
                error -> assertThat(error.line()).isEqualTo(3));
        }
    }

    @Test
    void previouslyDownloadedLoanFirstTemplateStillImports() throws Exception {
        try (var book = template(); var output = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            var header = sheet.getRow(1);
            String client = header.getCell(0).getStringCellValue();
            header.getCell(0).setCellValue(header.getCell(1).getStringCellValue());
            header.getCell(1).setCellValue(client);
            header.removeCell(header.getCell(7));
            for (int index = 2; index <= sheet.getLastRowNum(); index++) {
                var row = sheet.getRow(index);
                row.removeCell(row.getCell(7));
            }
            var row = sheet.getRow(2);
            row.getCell(0).setCellValue("00123");
            row.getCell(4).setCellValue(25.50);
            row.getCell(5).setCellValue(date.toString());
            row.getCell(6).setCellValue("BANK-01");
            book.write(output);
            assertThat(workbook.read(batch, output.toByteArray())).singleElement().satisfies(input -> {
                assertThat(input.loanNumber()).isEqualTo("00123");
                assertThat(input.clientId()).isNull();
            });
        }
    }

    private XSSFWorkbook template() throws Exception {
        var first = new ReceiptEntryLoan(UUID.randomUUID(), firstClient, "00123", "First client", "001",
            "Business", date.minusMonths(1), date, new BigDecimal("50.00"));
        var second = new ReceiptEntryLoan(UUID.randomUUID(), secondClient, "00456", "Second client", "002",
            "Business", date.minusMonths(1), date, new BigDecimal("90.99"));
        var another = new ReceiptEntryLoan(UUID.randomUUID(), firstClient, "00789", "First client", "001",
            "Business", date.minusMonths(1), date.plusDays(1), new BigDecimal("75.25"));
        return new XSSFWorkbook(new ByteArrayInputStream(workbook.write(batch, List.of(first, second, another), Locale.forLanguageTag("sw"))));
    }
}
