package com.sacco.mvp.web.view;

import com.sacco.mvp.domain.Position;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentUserView {
    private String memberNo;
    private String staffNo;
    private String fullName;
    private String email;
    private Position position;
    private boolean memberAccess;
    private boolean staffSession;

    public String getDisplayRole() {
        return position == null ? "" : position.getDisplayName();
    }
}
