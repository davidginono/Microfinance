package com.sacco.mvp.staffoffice.dto;

import com.sacco.mvp.service.AdminService.AdminDashboard;
import java.util.List;

/** Presentation definitions and bounded counts only; widgets never grant access. */
public final class PersonalDashboard {
    private PersonalDashboard() {}
    public record Destination(String office, String labelKey, String href) {}
    public record Activity(String key, String labelKey, List<Destination> destinations) {}
    public record View(List<Activity> activities, AdminDashboard administration, String layoutScope) {}
    public record Count(String office, int shown, boolean more) {}
    public record Summary(String source, List<Count> entries) {}
}
