package com.sacco.mvp.accounting.policy.dto;

import java.util.List;

public record PolicyPage(List<PolicyView> content, int number, boolean hasNext) {
    public PolicyPage { content = List.copyOf(content); }
    public boolean hasPrevious() { return number > 0; }
}
