package com.sacco.mvp.accounting.business.service;

import com.sacco.mvp.accounting.business.dto.BusinessOpeningDtos.Command;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class BusinessOpeningImportTest {
    private Command command(){return new Command(UUID.randomUUID(),UUID.randomUUID(),null,LocalDate.of(2026,10,1),"SUPPLIER_PAYABLE","Verified source register",true);}
    private MockMultipartFile file(String text){return new MockMultipartFile("file","opening.csv","text/csv",text.getBytes(StandardCharsets.UTF_8));}
    @Test void exactCentsAndIndependentRowEvidenceAreRetained(){var p=BusinessOpeningImport.parse(command(),file("reference,signed_balance,evidence\r\nSupplier-A,-100.01,Invoice register\r\nSupplier-B,20.00,Credit register\r\n"));assertThat(p.preview().signedBalance()).isEqualByComparingTo("-80.01");assertThat(p.preview().rows()).hasSize(2);assertThat(p.preview().fileChecksum()).isEqualTo(BusinessOpeningImport.sha(p.bytes()));}
    @Test void explicitHeaderOnlyZeroDiffersFromUnreviewedCoverage(){var p=BusinessOpeningImport.parse(command(),file("reference,signed_balance,evidence\n"));assertThat(p.preview().rows()).isEmpty();assertThat(p.preview().signedBalance()).isZero();assertThat(p.preview().command().completeCoverage()).isTrue();}
    @Test void malformedMoneyDuplicateReferencesAndUnsupportedFormatsAreRejected(){for(String body:new String[]{"A,1.001,Proof","A,1e3,Proof","A,0.00,Proof","A,1.01,Proof\nA,2.01,Proof","A,1.01,Proof,extra","A,9999999999999999.99,Proof\nB,0.01,Proof"})assertThatThrownBy(()->BusinessOpeningImport.parse(command(),file("reference,signed_balance,evidence\n"+body))).isInstanceOf(IllegalArgumentException.class);}
    @Test void malformedUtf8OversizedUploadAndUnsafeFilenameAreRejected(){assertThatThrownBy(()->BusinessOpeningImport.parse(command(),new MockMultipartFile("file","opening.csv","text/csv",new byte[]{(byte)0xc3,(byte)0x28}))).hasMessage("finance.business.opening.error.file");assertThatThrownBy(()->BusinessOpeningImport.parse(command(),new MockMultipartFile("file","../opening.csv","text/csv","reference,signed_balance,evidence".getBytes(StandardCharsets.UTF_8)))).hasMessage("finance.business.opening.error.file");assertThatThrownBy(()->BusinessOpeningImport.parse(command(),new MockMultipartFile("file","opening.csv","text/csv",new byte[1_000_001]))).hasMessage("finance.business.opening.error.file");}
    @Test void fileIdentityPreservesOriginalLineEndingsAndRowsAreBounded(){String header="reference,signed_balance,evidence";var c=command();assertThat(BusinessOpeningImport.parse(c,file(header+"\n")).preview().fileChecksum()).isNotEqualTo(BusinessOpeningImport.parse(c,file(header+"\r\n")).preview().fileChecksum());var text=new StringBuilder(header);for(int i=0;i<1001;i++)text.append("\nR").append(i).append(",1.00,Proof");assertThatThrownBy(()->BusinessOpeningImport.parse(c,file(text.toString()))).hasMessage("finance.business.opening.error.format");}
}
