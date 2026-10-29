package com.sacco.mvp.platform.onboarding.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AdminSetupRepository {
    private final JdbcTemplate jdbc;
    public record Account(String hash, boolean required) {}

    public Optional<Account> lock(UUID id) {
        return jdbc.query("""
            select password_hash,platform_setup_required from members m where id=?
              and sacco_id is null and station_id is null and status='ACTIVE'
              and exists(select 1 from member_staff_roles where member_id=m.id and role_name='ADMIN')
            for update
            """,(r,n)->new Account(r.getString(1),r.getBoolean(2)),id).stream().findFirst();
    }
    public void provision(UUID id,String hash) {
        int changed=jdbc.update("update members set password_hash=? where id=? and password_hash='!BOOTSTRAP_REQUIRED' and platform_setup_required",hash,id);
        if(changed==1)jdbc.update("""
            insert into user_settings(member_id,language,notification_prefs,login_otp_enabled,approval_otp_enabled,created_at,updated_at)
            values(?,'en','{}',false,false,now(),now()) on conflict(member_id) do update set login_otp_enabled=false,approval_otp_enabled=false,updated_at=now()
            """,id);
    }
    public boolean contactUsed(UUID id,String email,String phone) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from members where id<>? and (lower(email)=? or phone=?))",Boolean.class,id,email,phone));
    }
    public void complete(UUID id,String name,String email,String phone,String hash) {
        jdbc.update("update members set full_name=?,email=?,phone=?,password_hash=?,platform_setup_required=false where id=?",name,email,phone,hash,id);
    }
}
