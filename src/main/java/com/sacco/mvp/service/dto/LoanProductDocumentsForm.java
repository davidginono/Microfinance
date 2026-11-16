package com.sacco.mvp.service.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class LoanProductDocumentsForm {
    private List<String> requiredAttachmentId=new ArrayList<>();
    private List<String> requiredAttachmentName=new ArrayList<>();
    private List<BigDecimal> requiredAttachmentMaxSizeMb=new ArrayList<>();
}
