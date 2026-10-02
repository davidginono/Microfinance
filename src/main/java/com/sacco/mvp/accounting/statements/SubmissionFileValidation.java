package com.sacco.mvp.accounting.statements;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import static com.sacco.mvp.accounting.statements.StatementDefinition.require;

/** Uploaded manual files are reviewed evidence, not executable spreadsheets or active PDF documents. */
final class SubmissionFileValidation {
    private SubmissionFileValidation() { }
    static void validate(byte[] bytes,RegulatoryFormatCatalog.FileFormat format){
        require(bytes!=null && bytes.length>0 && bytes.length<=2000000,"officialFile");
        try{switch(format){case CSV->csv(bytes);case XLSX->xlsx(bytes);case PDF->pdf(bytes);}}
        catch(IOException|IllegalStateException|org.apache.poi.openxml4j.exceptions.InvalidFormatException e){throw new IllegalArgumentException("statement.error.officialFile");}
        catch(RuntimeException e){if(e instanceof IllegalArgumentException && e.getMessage()!=null && e.getMessage().startsWith("statement.error."))throw e;throw new IllegalArgumentException("statement.error.officialFile");}
    }
    private static void csv(byte[] bytes)throws CharacterCodingException{
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        if(text.startsWith("\uFEFF"))text=text.substring(1);
        StringBuilder cell=new StringBuilder();boolean quoted=false;int cells=0;
        for(int n=0;n<=text.length();n++){char c=n==text.length()?'\n':text.charAt(n);
            if(c=='"'){if(quoted && n+1<text.length() && text.charAt(n+1)=='"'){cell.append('"');n++;}else quoted=!quoted;}
            else if(!quoted && (c==',' || c=='\n' || c=='\r')){safeCell(cell.toString());cell.setLength(0);require(++cells<=100000,"size");}
            else{require(c!=0 && cell.length()<10000,"officialFile");cell.append(c);}}
        require(!quoted,"officialFile");
    }
    private static void safeCell(String value){String t=value.stripLeading();if(!t.isEmpty() && "=+@-".indexOf(t.charAt(0))>=0)require(t.matches("[-+]?[0-9]+(?:\\.[0-9]+)?"),"officialFile");}
    private static void xlsx(byte[] bytes)throws IOException,org.apache.poi.openxml4j.exceptions.InvalidFormatException{
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))){require(workbook.getNumberOfSheets()>0 && workbook.getNumberOfSheets()<=10 && workbook.getExternalLinksTable().isEmpty() && !workbook.isMacroEnabled(),"officialFile");int cells=0;
            for(var sheet:workbook)for(var row:sheet)for(var cell:row){require(++cells<=100000 && cell.getCellType()!=CellType.FORMULA,"officialFile");if(cell.getCellType()==CellType.STRING)require(cell.getStringCellValue().length()<=10000,"size");}}
    }
    private static void pdf(byte[] bytes)throws IOException{
        try(var document=Loader.loadPDF(bytes)){require(!document.isEncrypted() && document.getNumberOfPages()>0 && document.getNumberOfPages()<=1000,"officialFile");var seen=Collections.newSetFromMap(new IdentityHashMap<COSBase,Boolean>());int[] count={0};checkPdf(document.getDocumentCatalog().getCOSObject(),seen,count,0);}
    }
    private static void checkPdf(COSBase base,Set<COSBase>seen,int[]count,int depth){
        require(depth<=64 && ++count[0]<=100000,"size");if(base==null || !seen.add(base))return;if(base instanceof COSObject object){checkPdf(object.getObject(),seen,count,depth+1);return;}
        if(base instanceof COSDictionary dict){for(var entry:dict.entrySet()){String key=entry.getKey().getName();require(!Set.of("JavaScript","JS","Launch","EmbeddedFile","EmbeddedFiles","OpenAction","AA","RichMedia","AcroForm").contains(key),"officialFile");checkPdf(entry.getValue(),seen,count,depth+1);}}
        else if(base instanceof COSArray array)for(var item:array)checkPdf(item,seen,count,depth+1);
    }
}
