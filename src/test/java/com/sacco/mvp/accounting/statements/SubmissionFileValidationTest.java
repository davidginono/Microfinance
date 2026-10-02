package com.sacco.mvp.accounting.statements;

import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.cos.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class SubmissionFileValidationTest {
    @Test void safeCsvRetainsNegativeMoneyAndRejectsFormulaCells(){SubmissionFileValidation.validate("Balance,-1.01\n".getBytes(StandardCharsets.UTF_8),RegulatoryFormatCatalog.FileFormat.CSV);assertThatThrownBy(()->SubmissionFileValidation.validate("Name,=HYPERLINK(\"https://attacker.example\")\n".getBytes(StandardCharsets.UTF_8),RegulatoryFormatCatalog.FileFormat.CSV)).hasMessage("statement.error.officialFile");assertThatThrownBy(()->SubmissionFileValidation.validate(new byte[]{(byte)0xc0,(byte)0xaf},RegulatoryFormatCatalog.FileFormat.CSV)).hasMessage("statement.error.officialFile");}
    @Test void workbookFormulaContentIsRejected()throws Exception{try(var workbook=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()){workbook.createSheet("Synthetic").createRow(0).createCell(0).setCellFormula("1+1");workbook.write(bytes);assertThatThrownBy(()->SubmissionFileValidation.validate(bytes.toByteArray(),RegulatoryFormatCatalog.FileFormat.XLSX)).hasMessage("statement.error.officialFile");}}
    @Test void ordinaryPdfPassesAndActiveActionsAreRejected()throws Exception{try(var document=new PDDocument();var bytes=new ByteArrayOutputStream()){document.addPage(new PDPage());document.save(bytes);SubmissionFileValidation.validate(bytes.toByteArray(),RegulatoryFormatCatalog.FileFormat.PDF);var action=new COSDictionary();action.setName(COSName.S,"JavaScript");action.setString(COSName.getPDFName("JS"),"app.alert('hostile')");document.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("OpenAction"),action);bytes.reset();document.save(bytes);assertThatThrownBy(()->SubmissionFileValidation.validate(bytes.toByteArray(),RegulatoryFormatCatalog.FileFormat.PDF)).hasMessage("statement.error.officialFile");}}
}
