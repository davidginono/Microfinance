package com.sacco.mvp.grouplending.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import java.io.IOException;
import java.util.*;

/** Shared bounded validation of institution-supplied agreement PDFs; no persistence or authorization. */
final class GroupAgreementDocumentValidation {
    private GroupAgreementDocumentValidation() {}
    static int pages(byte[] bytes){
        try(var pdf=Loader.loadPDF(bytes)){
            check(!pdf.isEncrypted()&&pdf.getNumberOfPages()>=1&&pdf.getNumberOfPages()<=50,"documentPdf");
            // Retain exact approved bytes. Reject active forms/actions instead of silently rewriting them.
            var pending=new ArrayDeque<COSBase>();pending.add(pdf.getDocumentCatalog().getCOSObject());var seen=Collections.newSetFromMap(new IdentityHashMap<COSBase,Boolean>());
            Set<String> forbidden=Set.of("JS","AA","OpenAction","EmbeddedFiles","AcroForm","XFA","RichMediaContent");int count=0;
            while(!pending.isEmpty()){
                var node=pending.removeLast();if(!seen.add(node))continue;check(++count<=100000,"documentPdf");
                if(node instanceof COSObject indirect){if(indirect.getObject()!=null)pending.add(indirect.getObject());}
                else if(node instanceof COSArray array){for(var child:array)if(child!=null)pending.add(child);}
                else if(node instanceof COSDictionary dictionary){
                    for(var entry:dictionary.entrySet()){check(!forbidden.contains(entry.getKey().getName()),"documentPdf");if(entry.getValue()!=null)pending.add(entry.getValue());}
                    check(!Set.of("JavaScript","Launch","GoToR","SubmitForm","ImportData","Rendition","Sound","Movie").contains(dictionary.getNameAsString(COSName.S,"")),"documentPdf");
                }
            }
            return pdf.getNumberOfPages();
        }catch(IOException|IllegalStateException e){throw new IllegalArgumentException("group.error.documentPdf",e);}
    }
    private static void check(boolean b,String key){if(!b)throw new IllegalArgumentException("group.error."+key);}
}
