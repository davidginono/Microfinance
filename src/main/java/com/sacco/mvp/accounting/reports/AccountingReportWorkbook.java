package com.sacco.mvp.accounting.reports;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Typed, bounded report output. Text is never interpreted as an Excel formula. */
public final class AccountingReportWorkbook implements AutoCloseable {
    private static final XSSFColor GRID = color(0x9AA4AE);
    private final XSSFWorkbook book = new XSSFWorkbook();
    private final CellStyle caption, section, fact, factText, factLabel, factMoney, factDate, factEmphasis;
    private final CellStyle text, literal, money, date, integer, total, totalMoney;
    private final XSSFCellStyle heading, accentHeading;
    private boolean exactText;

    public AccountingReportWorkbook(String family) {
        this(family, family);
    }

    public AccountingReportWorkbook(String family, String factsFamily) {
        caption = style(family, false);
        caption.setAlignment(HorizontalAlignment.LEFT);
        caption.setWrapText(false);
        section = copy(caption);
        section.setFont(font(family, 10, true));
        fact = style(factsFamily, false);
        factText = copy(fact);
        factText.setDataFormat((short) BuiltinFormats.getBuiltinFormat("@"));
        factText.setAlignment(HorizontalAlignment.LEFT);
        factLabel = style(factsFamily, true);
        factEmphasis = copy(factLabel);
        var emphasis = font(factsFamily, 11, true);
        emphasis.setItalic(true);
        factEmphasis.setFont(emphasis);
        factMoney = copy(fact);
        factDate = copy(fact);
        var formats = book.createDataFormat();
        factMoney.setDataFormat(formats.getFormat("#,##0.00;[Red](#,##0.00)"));
        factDate.setDataFormat(formats.getFormat("dd-mmm-yyyy"));
        factDate.setAlignment(HorizontalAlignment.LEFT);
        factEmphasis.setDataFormat(factDate.getDataFormat());
        factEmphasis.setAlignment(HorizontalAlignment.LEFT);
        var body = style(family, false);
        text = table(body);
        literal = copy(text);
        literal.setAlignment(HorizontalAlignment.LEFT);
        literal.setDataFormat(factText.getDataFormat());
        money = copy(text);
        money.setDataFormat(factMoney.getDataFormat());
        date = copy(text);
        date.setDataFormat(factDate.getDataFormat());
        integer = copy(text);
        integer.setDataFormat(formats.getFormat("0"));
        total = table(style(family, true));
        total.setBorderTop(BorderStyle.MEDIUM);
        totalMoney = copy(total);
        totalMoney.setDataFormat(money.getDataFormat());
        heading = table(factLabel);
        heading.setBorderBottom(BorderStyle.MEDIUM);
        fill(heading, 0xEEF1F4);
        accentHeading = copy(heading);
        fill(accentHeading, 0xFFDFBF);
    }

    private Font font(String family, int points, boolean bold) {
        var font = book.createFont();
        font.setFontName(family);
        font.setFontHeightInPoints((short) points);
        font.setBold(bold);
        return font;
    }

    private XSSFCellStyle style(String family, boolean bold) {
        var style = book.createCellStyle();
        style.setFont(font(family, 10, bold));
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        return style;
    }

    private XSSFCellStyle copy(CellStyle base) {
        var style = book.createCellStyle();
        style.cloneStyleFrom(base);
        return style;
    }

    private XSSFCellStyle table(CellStyle base) {
        var style = copy(base);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(GRID);
        style.setBottomBorderColor(GRID);
        style.setLeftBorderColor(GRID);
        style.setRightBorderColor(GRID);
        return style;
    }

    private static XSSFColor color(int rgb) {
        return new XSSFColor(new Color(rgb), null);
    }

    private static void fill(XSSFCellStyle style, int rgb) {
        style.setFillForegroundColor(color(rgb));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    public Sheet sheet(String name, int columns, boolean landscape, short paper) {
        var sheet = book.createSheet(WorkbookUtil.createSafeSheetName(name));
        sheet.setDisplayGridlines(false);
        sheet.setFitToPage(true);
        var print = sheet.getPrintSetup();
        print.setLandscape(landscape);
        print.setPaperSize(paper);
        print.setFitWidth((short) 1);
        print.setFitHeight((short) 0);
        sheet.getFooter().setCenter("&P / &N");
        for (int column = 0; column < columns; column++) sheet.setColumnWidth(column, 20 * 256);
        return sheet;
    }

    private static Row append(Sheet sheet) {
        return sheet.createRow(sheet.getLastRowNum() + 1);
    }

    public void space(Sheet sheet) {
        append(sheet).setHeightInPoints(8);
    }

    /** Captions sit in ordinary cells and can flow through the empty cells to the right. */
    public void line(Sheet sheet, String value, int columns, boolean bold) {
        line(sheet, value, 0, columns, bold);
    }

    public void line(Sheet sheet, String value, int firstColumn, int columns, boolean bold) {
        int capacity = 0;
        for (int column = firstColumn; column < firstColumn + columns; column++) capacity += sheet.getColumnWidth(column) / 256;
        capacity = Math.max(20, (int) (capacity * .9));
        for (String paragraph : value.split("\n", -1)) {
            String remaining = paragraph;
            while (remaining.length() > capacity) {
                int split = remaining.lastIndexOf(' ', capacity);
                if (split <= 0) split = capacity;
                caption(append(sheet), firstColumn, remaining.substring(0, split), bold);
                remaining = remaining.substring(split).stripLeading();
            }
            caption(append(sheet), firstColumn, remaining, bold);
        }
    }

    public void caption(Row row, int column, String value, boolean bold) {
        var cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(bold ? section : caption);
    }

    public void headings(String family, boolean shaded) {
        var font = font(family, 10, true);
        heading.setFont(font);
        accentHeading.setFont(font);
        if (shaded) fill(heading, 0xEAEAEA);
    }

    public void title(Sheet sheet, String value, int columns) {
        title(sheet, value, columns, "Arial", 14, true, false);
    }

    public Row title(Sheet sheet, String value, int columns, String family, int points, boolean bold, boolean underline) {
        var row = append(sheet);
        var cell = row.createCell(0);
        var style = copy(fact);
        var font = font(family, points, bold);
        if (underline) font.setUnderline(Font.U_SINGLE);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        cell.setCellStyle(style);
        cell.setCellValue(value);
        if (columns > 1) sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, columns - 1));
        row.setHeightInPoints(Math.max(24, (points + 6) * value.split("\n", -1).length + 4));
        return row;
    }

    public int headers(Sheet sheet, List<String> labels) {
        return headers(sheet, labels, -1);
    }

    public int headers(Sheet sheet, List<String> labels, int accentColumn) {
        var row = append(sheet);
        int lines = 2;
        for (int column = 0; column < labels.size(); column++) {
            var cell = row.createCell(column);
            String label = labels.get(column);
            cell.setCellValue(label);
            cell.setCellStyle(column == accentColumn ? accentHeading : heading);
            lines = Math.max(lines, lineCount(sheet, column, label));
        }
        row.setHeightInPoints(Math.min(409, 13 * lines + 5));
        return row.getRowNum();
    }

    public void row(Sheet sheet, List<?> values, Set<Integer> monetary, boolean bold) {
        writeRow(sheet, values, monetary, Set.of(), bold, true);
    }

    public void facts(Sheet sheet, List<?> values, Set<Integer> monetary, Set<Integer> labels) {
        writeRow(sheet, values, monetary, labels, false, false);
    }

    public void emphasizeFact(Sheet sheet, int column) {
        sheet.getRow(sheet.getLastRowNum()).getCell(column).setCellStyle(factEmphasis);
    }

    private void writeRow(Sheet sheet, List<?> values, Set<Integer> monetary, Set<Integer> labels, boolean bold, boolean grid) {
        var row = append(sheet);
        int lines = 1;
        for (int column = 0; column < values.size(); column++) {
            Object value = values.get(column);
            if (value == null && !grid) continue;
            var cell = row.createCell(column);
            boolean amount = monetary.contains(column);
            cell.setCellStyle(grid ? (bold ? (amount ? totalMoney : total) : (amount ? money : text))
                : amount ? factMoney : labels.contains(column) ? factLabel : fact);
            if (value instanceof BigDecimal decimal) {
                double number = decimal.doubleValue();
                if (Double.isFinite(number) && decimal.stripTrailingZeros().precision() <= 15 && BigDecimal.valueOf(number).compareTo(decimal) == 0) {
                    cell.setCellValue(number);
                } else {
                    String exact = decimal.toPlainString();
                    cell.setCellValue(exact);
                    lines = Math.max(lines, lineCount(sheet, column, exact));
                    exactText = true;
                }
            } else if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
                if (grid && !amount && !bold) cell.setCellStyle(integer);
            } else if (value instanceof LocalDate day) {
                cell.setCellValue(day);
                cell.setCellStyle(grid ? date : factDate);
            } else if (value != null) {
                String textValue = value.toString();
                if (!bold && !amount && !labels.contains(column)) cell.setCellStyle(grid ? literal : factText);
                cell.setCellValue(textValue);
                lines = Math.max(lines, lineCount(sheet, column, textValue));
            }
        }
        row.setHeightInPoints(Math.min(409, 14 * lines + 3));
    }

    private static int lineCount(Sheet sheet, int column, String value) {
        int width = Math.max(5, sheet.getColumnWidth(column) / 256 - 1);
        int lines = 0;
        for (String part : value.split("\n", -1)) {
            int length = part.length();
            int start = 0;
            while (length - start > width) {
                int end = part.lastIndexOf(' ', start + width);
                start = end > start ? end + 1 : start + width;
                lines++;
            }
            lines++;
        }
        return lines;
    }

    public void filter(Sheet sheet, int header, int columns) {
        sheet.createFreezePane(0, header + 1);
        sheet.setRepeatingRows(new CellRangeAddress(header, header, -1, -1));
        if (sheet.getLastRowNum() > header) sheet.setAutoFilter(new CellRangeAddress(header, sheet.getLastRowNum(), 0, columns - 1));
    }

    public boolean hasExactText() {
        return exactText;
    }

    public byte[] bytes() {
        try (var output = new ByteArrayOutputStream()) {
            book.write(output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Spreadsheet generation failed", failure);
        }
    }

    @Override
    public void close() {
        try {
            book.close();
        } catch (IOException failure) {
            throw new IllegalStateException("Spreadsheet close failed", failure);
        }
    }
}
