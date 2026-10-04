package com.sacco.mvp.accounting.business.service;

import com.sacco.mvp.accounting.business.dto.BusinessOpeningDtos.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** A bounded, fixed format. The retained bytes, including their line endings, determine file identity. */
public final class BusinessOpeningImport {
    private BusinessOpeningImport() { }
    public static final Set<String> PURPOSES=Set.of("LOAN_PRINCIPAL","INTEREST_RECEIVABLE","ALLOWANCE",
        "SUPPLIER_PAYABLE","FUNDING_PRINCIPAL","UNAPPLIED_FUNDS","INTERNAL_DUE_FROM","INTERNAL_DUE_TO");
    public record Parsed(Preview preview,byte[] bytes) {
        public Parsed {bytes=bytes.clone();}
        @Override public byte[] bytes(){return bytes.clone();}
    }
    public static Parsed parse(Command c,MultipartFile file) {
        require(c!=null&&c.requestKey()!=null&&c.account()!=null&&c.through()!=null
            &&c.through().getYear()>=1&&c.through().getYear()<=9999&&c.purpose()!=null&&PURPOSES.contains(c.purpose()),"validation");
        text(c.evidence(),500);
        require(file!=null&&!file.isEmpty()&&file.getSize()<=1_000_000,"file");
        String filename=file.getOriginalFilename();text(filename,160);
        require(!filename.contains("/")&&!filename.contains("\\"),"file");
        byte[] bytes;String content;
        try {
            bytes=file.getBytes();require(bytes.length>0&&bytes.length<=1_000_000,"file");
            content=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }catch(java.io.IOException failure){throw invalid("file");}
        String[] lines=content.split("\r?\n",-1);int length=lines.length;
        if(length>1&&lines[length-1].isEmpty())length--;
        require(length>=1&&length<=1001&&lines[0].equals("reference,signed_balance,evidence"),"format");
        var rows=new ArrayList<Row>();var seen=new HashSet<String>();BigDecimal total=new BigDecimal("0.00");
        for(int i=1;i<length;i++) {
            String[] cells=lines[i].split(",",-1);require(cells.length==3,"format");
            text(cells[0],160);text(cells[2],500);require(seen.add(cells[0]),"duplicate");
            require(cells[1].matches("-?[0-9]{1,16}(\\.[0-9]{1,2})?"),"amount");
            var amount=new BigDecimal(cells[1]).setScale(2);require(amount.signum()!=0&&amount.abs().compareTo(new BigDecimal("10000000000000000"))<0,"amount");
            rows.add(new Row(cells[0],amount,cells[2]));total=total.add(amount);
        }
        require(total.abs().compareTo(new BigDecimal("10000000000000000"))<0,"amount");
        return new Parsed(new Preview(c,filename,sha(bytes),rows,total),bytes);
    }
    static void text(String value,int max){require(value!=null&&!value.isBlank()&&value.equals(value.strip())
        &&value.length()<=max&&value.chars().noneMatch(Character::isISOControl),"validation");}
    public static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException failure){throw new IllegalStateException(failure);}}
    static void require(boolean valid,String key){if(!valid)throw invalid(key);}
    static IllegalArgumentException invalid(String key){return new IllegalArgumentException("finance.business.opening.error."+key);}
}
