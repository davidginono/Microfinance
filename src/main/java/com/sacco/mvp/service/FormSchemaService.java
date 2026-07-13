package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.service.dto.FormField;
import com.sacco.mvp.service.dto.FormModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class FormSchemaService {
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final ObjectMapper objectMapper;

    public LoanProductSetting getSchema(String saccoId, LoanType loanType) {
        if (loanType == null) {
            throw new IllegalArgumentException("Loan product schema not found");
        }
        return loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId).stream()
            .filter(product -> product.getLoanType() == loanType)
            .sorted(Comparator.comparingInt(LoanProductSetting::getResolvedDisplayOrder))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Loan product schema not found"));
    }

    public LoanProductSetting getSchema(String saccoId, UUID loanProductId, LoanType fallbackLoanType) {
        if (loanProductId != null) {
            return loanProductSettingRepository.findByIdAndSaccoIdAndActiveTrue(loanProductId, saccoId)
                .orElseThrow(() -> new IllegalArgumentException("Loan product schema not found"));
        }
        return getSchema(saccoId, fallbackLoanType);
    }

    public FormModel toFormModel(LoanType loanType, String schemaJson) {
        try {
            JsonNode schema = objectMapper.readTree(schemaJson);
            Set<String> requiredNames = new HashSet<>();
            if (schema.has("required")) {
                schema.get("required").forEach(n -> requiredNames.add(n.asText()));
            }
            List<FormField> fields = new ArrayList<>();
            JsonNode properties = schema.path("properties");
            Iterator<Map.Entry<String, JsonNode>> iterator = properties.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                String name = entry.getKey();
                if (shouldExcludeField(name)) {
                    continue;
                }
                JsonNode field = entry.getValue();
                String type = field.path("type").asText("text");
                if ("textarea".equalsIgnoreCase(field.path("format").asText())) {
                    type = "textarea";
                }
                List<String> options = null;
                if (field.has("enum")) {
                    options = new ArrayList<>();
                    for (JsonNode enumValue : field.get("enum")) {
                        options.add(enumValue.asText());
                    }
                    type = "select";
                }

                fields.add(FormField.builder()
                    .name(name)
                    .type(type)
                    .required(requiredNames.contains(name))
                    .min(field.has("minimum") ? field.get("minimum").decimalValue() : null)
                    .max(field.has("maximum") ? field.get("maximum").decimalValue() : null)
                    .enumOptions(options)
                    .labelKey("form." + loanType.name().toLowerCase() + "." + name)
                    .build());
            }
            return FormModel.builder().fields(fields).build();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid schema JSON", e);
        }
    }

    public void validateAgainstSchema(String schemaJson, Map<String, Object> formData) {
        try {
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            JsonNode dataNode = objectMapper.valueToTree(formData);
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            JsonSchema schema = factory.getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(dataNode);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("Form validation failed: " + errors.iterator().next().getMessage());
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse JSON", e);
        }
    }

    public Map<String, Object> extractFormData(Map<String, String> requestParams, String schemaJson) {
        try {
            JsonNode schema = objectMapper.readTree(schemaJson);
            JsonNode properties = schema.path("properties");
            Map<String, Object> cleaned = new LinkedHashMap<>();
            requestParams.forEach((k, v) -> {
                if (v == null || v.isBlank()) {
                    return;
                }
                if (shouldExcludeField(k)) {
                    return;
                }
                String raw = v.trim();
                JsonNode property = properties.path(k);
                String type = property.path("type").asText("string");
                cleaned.put(k, coerceValue(k, raw, type));
            });
            return cleaned;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid schema JSON", e);
        }
    }

    private Object coerceValue(String field, String raw, String type) {
        try {
            return switch (type) {
                case "number" -> new java.math.BigDecimal(raw);
                case "integer" -> Integer.valueOf(raw);
                case "boolean" -> Boolean.valueOf(raw);
                default -> raw;
            };
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid value for " + field + ": " + raw);
        }
    }

    public String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize form data", e);
        }
    }

    private boolean shouldExcludeField(String fieldName) {
        return fieldName != null && "additionalnotes".equals(fieldName.trim().toLowerCase(Locale.ROOT));
    }
}

