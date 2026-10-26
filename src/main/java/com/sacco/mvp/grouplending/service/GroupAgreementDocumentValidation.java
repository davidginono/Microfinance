package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import java.io.IOException;
import java.util.*;

/** Shared bounded validation of institution-supplied agreement PDFs; no persistence or authorization. */
final class GroupAgreementDocumentValidation {
    private GroupAgreementDocumentValidation() {}
    static void validate(DocumentForm f){
        check(f!=null&&f.getDocumentRequestKey()!=null&&f.isDocumentChecked(),"documentChecked");
        check(Set.of("EN","SW","BOTH").contains(Objects.toString(f.getDocumentLanguage(),"")),"documentFields");
        f.setDocumentReference(text(f.getDocumentReference(),500,true));f.setInstitutionAddress(text(f.getInstitutionAddress(),500,true));
        f.setContactPhone(text(f.getContactPhone(),80,false));f.setComplaintPhone(text(f.getComplaintPhone(),80,false));
        f.setContactEmail(email(f.getContactEmail()));f.setComplaintEmail(email(f.getComplaintEmail()));f.setComplaintAddress(text(f.getComplaintAddress(),500,false));
        check(!f.getContactPhone().isEmpty()||!f.getContactEmail().isEmpty(),"documentContacts");check(!f.getComplaintPhone().isEmpty()||!f.getComplaintEmail().isEmpty(),"documentContacts");
    }
    private static String email(String s){s=text(s,160,false);check(s.isEmpty()||s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),"documentContacts");return s;}
    private static String text(String s,int max,boolean required){check(s!=null&&s.length()<=max&&s.chars().noneMatch(Character::isISOControl)&&(!required||!s.isBlank()),"documentFields");return s.strip();}
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
