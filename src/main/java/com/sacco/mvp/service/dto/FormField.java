package com.sacco.mvp.service.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class FormField {
    String name;
    String type;
    boolean required;
    BigDecimal min;
    BigDecimal max;
    List<String> enumOptions;
    String labelKey;
}
