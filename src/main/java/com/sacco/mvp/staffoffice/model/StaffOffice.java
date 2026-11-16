package com.sacco.mvp.staffoffice.model;

import com.sacco.mvp.domain.Position;
import java.util.*;

/** Presentation of staff assignments only. Offices never grant operational permissions. */
public enum StaffOffice {
    LOAN_OFFICER(Position.LOAN_OFFICER, "loan-officer"),
    GENERAL_MANAGER(Position.MANAGER, "general-manager"),
    FINANCE_MANAGER(Position.FINANCE_MANAGER, "finance-manager"),
    CHIEF_ACCOUNTANT(Position.ACCOUNTANT, "chief-accountant"),
    ICT_MANAGER(Position.ICT_MANAGER, "ict-manager"),
    CREDIT_OFFICER(Position.CREDIT_OFFICER, "credit-officer"),
    ACCOUNTS_CLERK(Position.ACCOUNTS_CLERK, "accounts-clerk"),
    CUSTOMER_CARE(Position.CUSTOMER_CARE, "customer-care"),
    ACCOUNTS_CLERK_2(Position.ACCOUNTS_CLERK_2, "accounts-clerk-2");

    private final Position group;
    private final String slug;
    StaffOffice(Position group, String slug) { this.group=group; this.slug=slug; }
    public Position getGroup() { return group; }
    public String getSlug() { return slug; }
    public String getLabelKey() { return "userGroup."+group.name(); }
    public String getBase() { return "/staff/offices/"+slug; }
    public List<Position> getAliases() { return Arrays.stream(Position.values()).filter(p->p.userGroup()==group).toList(); }
    public static Optional<StaffOffice> fromSlug(String slug) { return Arrays.stream(values()).filter(o->o.slug.equals(slug)).findFirst(); }
    public static List<StaffOffice> assigned(Collection<Position> roles) {
        if(roles==null || roles.contains(Position.ADMIN)) return List.of();
        var groups=new HashSet<Position>();
        roles.stream().filter(Objects::nonNull).map(Position::userGroup).forEach(groups::add);
        return Position.staffAssignableRoles().stream().filter(groups::contains)
            .map(p->Arrays.stream(values()).filter(o->o.group==p).findFirst().orElse(null))
            .filter(Objects::nonNull).toList();
    }
}
