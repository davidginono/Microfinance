package com.sacco.mvp.accounting.batch.service;

import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.ImportRow;
import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.ImportRowError;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ReceiptEntryLoan;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipInputStream;

@Component
@RequiredArgsConstructor
public class ReceiptBatchWorkbook {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_ROWS = 1000;
    private static final int HEADER_ROW = 1;
    private static final String LOAN_LIST = "LoanList";
    private static final List<String> COLUMNS = List.of(
        "loan", "client", "instalmentDate", "instalmentAmount", "received", "paymentDate", "reference");
    private final MessageSource messages;

    public byte[] write(UUID batch, List<ReceiptEntryLoan> loans, Locale locale) {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet(label("sheet", locale));
            var batchRow = sheet.createRow(0);
            batchRow.createCell(0).setCellValue(label("batchId", locale));
            batchRow.createCell(1).setCellValue(batch.toString());
            var headerStyle = book.createCellStyle();
            var font = book.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setWrapText(true);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var entryStyle = book.createCellStyle();
            entryStyle.setDataFormat(book.createDataFormat().getFormat("@"));
            entryStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            entryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var loanList = book.createSheet(LOAN_LIST);
            for (int index = 0; index < loans.size(); index++) {
                var loan = loans.get(index);
                var row = loanList.createRow(index);
                row.createCell(0).setCellValue(loan.number());
                row.createCell(1).setCellValue(loan.client());
                row.createCell(2).setCellValue(loan.instalmentDate() == null ? "" : loan.instalmentDate().toString());
                row.createCell(3).setCellValue(loan.instalmentAmount() == null ? "" : loan.instalmentAmount().toPlainString());
            }
            book.setSheetHidden(1, true);
            var loanIds = book.createName();
            loanIds.setNameName("RepaymentLoanIds");
            loanIds.setRefersToFormula(LOAN_LIST + "!$A$1:$A$" + loans.size());
            var header = sheet.createRow(HEADER_ROW);
            header.setHeightInPoints(32);
            int[] widths = {20, 42, 22, 31, 24, 28, 34};
            for (int column = 0; column < COLUMNS.size(); column++) {
                var cell = header.createCell(column);
                cell.setCellValue(label(COLUMNS.get(column), locale));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(column, widths[column] * 256);
            }
            int rowCount = Math.min(MAX_ROWS, loans.size() + 25);
            for (int index = 0; index < rowCount; index++) {
                var row = sheet.createRow(HEADER_ROW + 1 + index);
                var loanCell = row.createCell(0);
                loanCell.setCellStyle(entryStyle);
                if (index < loans.size()) loanCell.setCellValue(loans.get(index).number());
                String loanAddress = "A" + (row.getRowNum() + 1);
                for (int column = 1; column <= 3; column++) {
                    row.createCell(column).setCellFormula("IF(" + loanAddress + "=\"\",\"\",IFERROR(VLOOKUP("
                        + loanAddress + "," + LOAN_LIST + "!$A$1:$D$" + loans.size() + "," + (column + 1) + ",FALSE),\"\"))");
                }
                for (int column = 4; column < COLUMNS.size(); column++) row.createCell(column).setCellStyle(entryStyle);
            }
            var helper = sheet.getDataValidationHelper();
            var selector = helper.createValidation(helper.createFormulaListConstraint("RepaymentLoanIds"),
                new org.apache.poi.ss.util.CellRangeAddressList(HEADER_ROW + 1, HEADER_ROW + rowCount, 0, 0));
            selector.setShowErrorBox(true);
            selector.setErrorStyle(DataValidation.ErrorStyle.STOP);
            sheet.addValidationData(selector);
            book.setForceFormulaRecalculation(true);
            sheet.createFreezePane(0, HEADER_ROW + 1);
            sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, HEADER_ROW + rowCount, 0, COLUMNS.size() - 1));
            book.write(output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Receipt template generation failed", failure);
        }
    }

    public List<ImportRow> read(UUID batch, byte[] content) {
        require(content.length > 0 && content.length <= MAX_BYTES, "importFile");
        try {
            checkArchive(content);
            try (var book = new XSSFWorkbook(new ByteArrayInputStream(content))) {
                require(book.getWorkbookType() == XSSFWorkbookType.XLSX && book.getNumberOfSheets() == 2
                    && LOAN_LIST.equals(book.getSheetName(1)) && book.getExternalLinksTable().isEmpty(), "importFile");
                var sheet = book.getSheetAt(0);
                var scope = sheet.getRow(0);
                require(scope != null && batch.toString().equals(text(scope.getCell(1), 36)), "importBatch");
                var header = sheet.getRow(HEADER_ROW);
                require(header != null && header.getLastCellNum() == COLUMNS.size()
                    && (matches(header, Locale.ENGLISH) || matches(header, Locale.forLanguageTag("sw"))), "importHeaders");
                require(sheet.getLastRowNum() <= MAX_ROWS + HEADER_ROW, "limit");
                var rows = new ArrayList<ImportRow>();
                for (int index = HEADER_ROW + 1; index <= sheet.getLastRowNum(); index++) {
                    var row = sheet.getRow(index);
                    if (row == null) continue;
                    try {
                        require(row.getLastCellNum() <= COLUMNS.size(), "importRow");
                        // The workbook's lookup formulas are display-only. Import only the loan and actual payment cells.
                        if (blank(row.getCell(4)) && blank(row.getCell(5)) && blank(row.getCell(6))) continue;
                        String loan = text(row.getCell(0), 80);
                        String reference = text(row.getCell(6), 80);
                        require(!loan.isEmpty() && !reference.isEmpty(), "importRow");
                        rows.add(new ImportRow(index + 1, loan, amount(row.getCell(4)), date(row.getCell(5)), reference));
                    } catch (IllegalArgumentException | java.time.DateTimeException failure) {
                        throw new ImportRowError("batch.error.importRow", index + 1);
                    }
                }
                require(!rows.isEmpty(), "importEmpty");
                return List.copyOf(rows);
            }
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("batch.error.importFile", failure);
        }
    }

    private boolean matches(Row header, Locale locale) {
        for (int column = 0; column < COLUMNS.size(); column++) {
            var cell = header.getCell(column);
            if (cell == null || cell.getCellType() != CellType.STRING
                || !label(COLUMNS.get(column), locale).equals(cell.getStringCellValue())) return false;
        }
        return true;
    }

    private static BigDecimal amount(Cell cell) {
        require(cell != null, "importRow");
        boolean numeric = cell.getCellType() == CellType.NUMERIC;
        require(!numeric || !DateUtil.isCellDateFormatted(cell), "importRow");
        String value = numeric ? ((XSSFCell) cell).getRawValue() : text(cell, 19);
        require(value != null && value.length() <= 40 && (numeric
            ? value.matches("[0-9]{1,17}(\\.[0-9]{1,17})?([Ee][+-]?[0-9]{1,2})?")
            : value.matches("[0-9]{1,16}(\\.[0-9]{1,2})?")), "importRow");
        var money = new BigDecimal(value);
        require(money.signum() > 0 && money.precision() - money.scale() <= 16
            && money.stripTrailingZeros().scale() <= 2
            && (!numeric || money.stripTrailingZeros().precision() <= 15), "importRow");
        return money.setScale(2);
    }

    private static LocalDate date(Cell cell) {
        require(cell != null, "importRow");
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
            && cell.getNumericCellValue() >= 1 && cell.getNumericCellValue() % 1 == 0) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = text(cell, 10);
        require(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), "importRow");
        return LocalDate.parse(value);
    }

    private static boolean blank(Cell cell) {
        return cell == null || cell.getCellType() == CellType.BLANK
            || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank());
    }

    private static String text(Cell cell, int maximum) {
        if (blank(cell)) return "";
        require(cell.getCellType() == CellType.STRING, "importRow");
        String value = cell.getStringCellValue().strip();
        require(value.length() <= maximum && value.chars().noneMatch(Character::isISOControl), "importRow");
        return value;
    }

    private static void checkArchive(byte[] content) throws IOException {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            int entries = 0;
            int total = 0;
            var buffer = new byte[8192];
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                require(++entries <= 128 && !name.contains("vbaproject") && !name.contains("externallinks/"), "importFile");
                int size = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    size += read;
                    total += read;
                    require(size <= 4 * 1024 * 1024 && total <= 16 * 1024 * 1024, "importFile");
                }
            }
            require(entries > 0, "importFile");
        }
    }

    private String label(String key, Locale locale) {
        return messages.getMessage("batch.sheet." + key, null, locale);
    }

    private static void require(boolean condition, String error) {
        if (!condition) throw new IllegalArgumentException("batch.error." + error);
    }
}
