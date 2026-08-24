package com.sacco.mvp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMaintenanceAndOnboardingSqlContractTest {

    @Test
    void maintenanceResetScriptTracksCurrentSchemaTablesAndDefaults() throws IOException {
        String script = read("src/main/resources/db/maintenance/reset_operational_data_preserve_super_admin.sql");

        assertThat(script)
            .contains("CREATE TABLE IF NOT EXISTS public.member_access_claims")
            .contains("CREATE TABLE IF NOT EXISTS public.external_guarantor_registry")
            .contains("CREATE TABLE IF NOT EXISTS public.platform_support_contact_settings")
            .contains("CREATE TABLE IF NOT EXISTS public.platform_session_settings")
            .contains("CREATE TABLE IF NOT EXISTS public.platform_email_settings")
            .contains("CREATE TABLE IF NOT EXISTS public.platform_sms_gateway_settings")
            .contains("ADD COLUMN IF NOT EXISTS storage_backend varchar(40) NOT NULL DEFAULT 'DATABASE'")
            .contains("ADD COLUMN IF NOT EXISTS external_guarantor_registry_id uuid")
            .contains("ADD COLUMN IF NOT EXISTS portfolio_at_risk_days integer NOT NULL DEFAULT 30")
            .contains("'PROCESSING'")
            .contains("'PAR'::character varying")
            .contains("DELETE FROM public.external_guarantor_registry")
            .contains("DELETE FROM public.member_access_claims")
            .contains("INSERT INTO public.member_access_claims (member_id, claim_name)")
            .contains("ON CONFLICT (id) DO NOTHING");
        assertThat(script)
            .doesNotContain("ON CONFLICT (id) DO UPDATE\nSET low_percent");
    }

    @Test
    void currentSchemaOnboardingMigrationRefreshesSuperAdminAndPlatformDefaults() throws IOException {
        String migration = read("src/main/resources/db/migration/V31__current_schema_onboarding_defaults.sql");

        assertThat(migration)
            .contains("Historical onboarding migrations stay immutable")
            .contains("staff_no")
            .contains("staff_access_status")
            .contains("INSERT INTO public.member_staff_roles (member_id, role_name)")
            .contains("INSERT INTO public.member_access_claims (member_id, claim_name)")
            .contains("('SACCO_REGISTRY_VIEW')")
            .contains("('PLATFORM_SETTINGS_UPDATE')")
            .contains("('SMS_USAGE_ADD')")
            .contains("CREATE SEQUENCE IF NOT EXISTS public.sacco_numeric_id_seq")
            .contains("CREATE SEQUENCE IF NOT EXISTS public.staff_number_seq")
            .contains("INSERT INTO public.platform_sms_settings")
            .contains("INSERT INTO public.platform_branding_settings")
            .contains("INSERT INTO public.platform_support_contact_settings")
            .contains("INSERT INTO public.platform_session_settings")
            .contains("INSERT INTO public.platform_email_settings")
            .contains("INSERT INTO public.platform_sms_gateway_settings")
            .contains("DELETE FROM public.registered_saccos")
            .contains("WHERE sacco_id = 'PLATFORM'");
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
