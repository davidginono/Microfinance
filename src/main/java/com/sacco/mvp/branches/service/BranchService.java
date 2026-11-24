package com.sacco.mvp.branches.service;

import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminService;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.SaccoRegistryService;
import com.sacco.mvp.service.SaccoRegistryService.BranchSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BranchService {
    private final AdminService access;
    private final SaccoRegistryService registry;
    private final AuditService audit;

    public record Register(Slice<BranchSummary> rows, boolean canCreate, boolean canEditOtherBranches) {}

    @Transactional(readOnly=true)
    public Register register(AppUserPrincipal actor,int page,String sort) {
        var claims=access.workspaceClaims(actor);
        require(claims,UserClaim.BRANCHES_VIEW);
        if(page<0 || page>10000) throw new IllegalArgumentException("branches.error.filter");
        boolean delegated=access.hasBranchDelegation(claims);
        boolean canCreate=claims.contains(UserClaim.BRANCHES_CREATE);
        String column=switch(sort) {
            case "id_asc","id_desc" -> "stationId";
            case "location_asc","location_desc" -> "addressLocation";
            case "status_asc","status_desc" -> "accessStatus";
            default -> throw new IllegalArgumentException("branches.error.filter");
        };
        var direction=sort.endsWith("_desc")?org.springframework.data.domain.Sort.Direction.DESC:org.springframework.data.domain.Sort.Direction.ASC;
        return new Register(registry.branches(actor.getSaccoId(),delegated||canCreate?null:actor.getStationId(),page,column,direction),canCreate,delegated);
    }

    @Transactional(readOnly=true)
    public BranchSummary branchForEdit(AppUserPrincipal actor,String id) {
        var claims=access.workspaceClaims(actor);
        require(claims,UserClaim.BRANCHES_VIEW);
        require(claims,UserClaim.BRANCHES_UPDATE);
        if(!actor.getStationId().equals(id) && !access.hasBranchDelegation(claims))
            throw new AccessDeniedException("Branch unavailable");
        return summary(actor.getSaccoId(),id);
    }

    @Transactional(readOnly=true)
    public void requireCreate(AppUserPrincipal actor) {
        var claims=access.workspaceClaims(actor);
        require(claims,UserClaim.BRANCHES_VIEW);
        require(claims,UserClaim.BRANCHES_CREATE);
    }

    @Transactional
    public void create(AppUserPrincipal actor,String id,String location) {
        requireCreate(actor);
        String branch=id==null?"":id.strip().toUpperCase(java.util.Locale.ROOT);
        if(!branch.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")) throw new IllegalArgumentException("branches.error.id");
        validateLocation(location);
        registry.addStation(actor.getSaccoId(),branch,location);
        audit.log("BRANCH",null,"BRANCH_CREATED",actor.getMemberId(),null,
            Map.of("saccoId",actor.getSaccoId(),"stationId",branch,"location",location.strip()));
    }

    @Transactional
    public void edit(AppUserPrincipal actor,String id,String location) {
        branchForEdit(actor,id);
        validateLocation(location);
        registry.editBranchLocation(actor.getSaccoId(),id,location,actor.getMemberId());
    }

    private static void validateLocation(String location) {
        if(location==null || location.isBlank() || location.length()>255) throw new IllegalArgumentException("branches.error.location");
    }

    @Transactional(readOnly=true)
    public ReadScope oversight(AppUserPrincipal actor,String branch) {
        var claims=access.workspaceClaims(actor);
        require(claims,UserClaim.OTHER_BRANCHES_VIEW);
        var selected=summary(actor.getSaccoId(),branch==null || branch.isBlank()?actor.getStationId():branch);
        return new ReadScope(actor.getSaccoId(),selected,claims);
    }

    @Transactional(readOnly=true)
    public List<String> visibleBranches(ReadScope scope) {
        return registry.institutionBranchIds(scope.institution());
    }

    private BranchSummary summary(String institution,String id) {
        if(id==null || id.isBlank() || id.length()>255) throw new AccessDeniedException("Branch unavailable");
        return registry.findStation(institution,id)
            .map(b -> new BranchSummary(b.getStationId(),b.getAddressLocation(),b.isActive(),b.isAccessSuspended()))
            .orElseThrow(() -> new AccessDeniedException("Branch unavailable"));
    }

    private static void require(Set<UserClaim> claims,UserClaim claim) {
        if(!claims.contains(claim)) throw new AccessDeniedException("Permission required");
    }

    public static final class ReadScope {
        private final String institution;
        private final BranchSummary branch;
        private final Set<UserClaim> claims;

        private ReadScope(String institution,BranchSummary branch,Set<UserClaim> claims) {
            this.institution=institution;
            this.branch=branch;
            this.claims=Set.copyOf(claims);
        }
        public String institution() { return institution; }
        public BranchSummary branch() { return branch; }
        public boolean permits(UserClaim claim) { return branch.active() && !branch.suspended() && claims.contains(claim); }
        public void require(UserClaim claim) {
            if(!permits(claim)) throw new AccessDeniedException("Branch data unavailable");
        }
    }
}
