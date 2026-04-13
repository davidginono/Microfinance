package com.sacco.mvp.service.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class FormModel {
    List<FormField> fields;
}
