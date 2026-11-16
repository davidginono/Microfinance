package com.sacco.mvp.security;

import com.sacco.mvp.domain.Position;
import com.sacco.mvp.staffoffice.model.StaffOffice;
import java.util.Collection;

public final class WorkspaceLanding {
    private WorkspaceLanding() {}
    public static String staffDashboard(AppUserPrincipal principal) {
        return principal==null || !principal.isStaffSession() ? "/login" : staffDashboard(principal.getStaffRoles());
    }
    private static String staffDashboard(Collection<Position> roles) {
        if(roles.contains(Position.ADMIN)) return "/admin/dashboard";
        return "/staff/dashboard";
    }
    public static String authenticatedDefault(AppUserPrincipal principal) { return staffDashboard(principal); }
    public static String afterLogin(AppUserPrincipal principal,String savedTarget) {
        String fallback=authenticatedDefault(principal);
        if(principal==null||!principal.isStaffSession()||savedTarget==null||savedTarget.isBlank()||savedTarget.startsWith("//"))return fallback;
        try {
            var uri=java.net.URI.create(savedTarget);String path=uri.getPath();
            if(path==null||!path.startsWith("/")||path.startsWith("//")||path.indexOf('\\')>=0)return fallback;
            for(String retired:java.util.List.of("/app","/manager","/accountant","/chairperson","/board","/credit-committee","/loan-officer","/select-sacco"))
                if(path.equals(retired)||path.startsWith(retired+"/"))return fallback;
            if(path.startsWith("/staff/offices/")) {
                var parts=path.substring("/staff/offices/".length()).split("/");
                var office=parts.length==2?StaffOffice.fromSlug(parts[0]).orElse(null):null;
                if(office==null||!StaffOffice.assigned(principal.getStaffRoles()).contains(office)
                    ||!java.util.Set.of("dashboard","queue","reports").contains(parts[1]))return fallback;
                if(parts[1].equals("dashboard"))return fallback;
            }
            if(path.equals("/admin/dashboard")&&!principal.isPlatformIdentity())return fallback;
            // Saved requests are returned on this application. Destination services still check claims and record scope.
            return uri.getRawPath()+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());
        }catch(IllegalArgumentException invalid){return fallback;}
    }
}
