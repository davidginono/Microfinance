package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.MicrofinanceApplication;
import com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.*;
import com.sacco.mvp.clients.applications.service.ClientApplicationService;
import com.sacco.mvp.clients.portal.dto.ClientPortalDtos.EntryForm;
import com.sacco.mvp.clients.portal.service.ClientPortalService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.service.StationOtpDeliveryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockMultipartFile;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Full application, real security chain, real JSP rendering, real product workflow and PostgreSQL. */
@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
class ClientApplicationEndToEndPostgresTest {
    static final AtomicReference<String> OTP=new AtomicReference<>();
    @Test void assistedAndOtpClientsRecordSubmitAndRepeatApplicationsAgainstTheSameIdentity()throws Exception{
        var fixture=new ClientOnboardingPostgresTest();fixture.start();ConfigurableApplicationContext app=null;
        try{
            fixture.fixture();
            fixture.f.jdbc.update("update loan_product_settings set application_fee=0,processing_fee_rate=0,insurance_rate=0,affordability_check_required=true where id=?",fixture.f.product);
            fixture.f.jdbc.update("update loan_product_settings set manager_review_required=true,disbursement_officer_required=true where id=?",fixture.f.product);
            fixture.f.jdbc.update("insert into member_staff_roles(member_id,role_name) values(?,'MANAGER') on conflict do nothing",fixture.f.actor.getMemberId());
            for(var claim:List.of("CLIENT_ONBOARDING_VIEW","CLIENT_ONBOARDING_UPDATE"))fixture.f.jdbc.update("insert into member_access_claims(member_id,claim_name) values(?,?) on conflict do nothing",fixture.f.actor.getMemberId(),claim);
            app=new SpringApplicationBuilder(MicrofinanceApplication.class,DeliveryConfig.class).run(
                "--server.port=0","--spring.config.import=","--spring.datasource.url="+System.getenv("MICROFINANCE_RECORDING_TEST_URL"),"--spring.datasource.username=microfinance_test","--spring.datasource.password=","--spring.datasource.hikari.minimum-idle=1","--spring.datasource.hikari.maximum-pool-size=6","--app.outbox.scheduler-enabled=false","--app.sms.enabled=false","--app.retention.cleanup-enabled=false","--app.uploads.root=target/client-portal-test-uploads","--spring.devtools.restart.enabled=false","--spring.main.allow-bean-definition-overriding=true");
            var portal=app.getBean(ClientPortalService.class);var applications=app.getBean(ClientApplicationService.class);
            String base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
            var http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build();
            assertThat(get(http,base+"/")).contains("href=\"/client-portal/register\"","Staff login")
                .doesNotContain("href=\"/system-admin/login\"");
            String register=get(http,base+"/client-portal/register");assertThat(register).contains("Client portal","registration.profile.firstName");
            for(String field:com.sacco.mvp.clients.onboarding.service.OnboardingService.PERSONAL_FIELDS)if(!field.equals("groupId")&&!field.equals("activity"))assertThat(register).contains("name=\"registration.profile."+field+"\"");
            assertThat(register).contains("Spouse details","Bank details","data-profile-section=\"documents\"","Verify your contact to add photos and documents.")
                .doesNotContain("data-loan-choice","data-income-required");
            var fields=ClientOnboardingRenderedPostgresTest.hidden(register);fields.put("intent","REGISTER");fields.put("institution",fixture.f.institution);fields.put("branch","B1");fields.put("registration.profile.firstName","Synthetic");fields.put("registration.profile.lastName","Applicant");fields.put("registration.profile.phone",fixture.phone());fields.put("registration.profile.email",UUID.randomUUID()+"@example.invalid");
            fields.put("registration.profile.businessName","Public synthetic shop");fields.put("registration.profile.dailySales","12345.67");fields.put("registration.profile.spouseName","Public synthetic spouse");fields.put("registration.profile.bankAccountNumber","9876543210");
            post(http,base+"/client-portal/request-code",fields);String verifyPage=get(http,base+"/client-portal/verify");var verification=ClientOnboardingRenderedPostgresTest.hidden(verifyPage);verification.put("code",OTP.get());post(http,base+"/client-portal/verify",verification);
            String profile=get(http,base+"/client-portal/workspace/profile");assertThat(profile).contains("Bio data","Business details","Spouse details","Bank details",
                "data-profile-start=\"documents\"","data-profile-add-documents","/client-portal/workspace/profile/documents/new").doesNotContain("/admin/");
            UUID self=fixture.f.jdbc.queryForObject("select id from members where email=?",UUID.class,fields.get("registration.profile.email"));String number=fixture.f.jdbc.queryForObject("select member_no from members where id=?",String.class,self);
            var initialProfile=fixture.service.latest(fixture.f.actor,self).orElseThrow().profile();assertThat(initialProfile.getBusinessName()).isEqualTo("Public synthetic shop");assertThat(initialProfile.getDailySales()).isEqualByComparingTo("12345.67");assertThat(initialProfile.getSpouseName()).isEqualTo("Public synthetic spouse");assertThat(initialProfile.getBankAccountNumber()).isEqualTo("9876543210");
            var savedProfile=fixture.profile(self,"TZ-E2E-"+UUID.randomUUID());fixture.service.save(fixture.f.actor,self,savedProfile);UUID submittedProfile=fixture.submitted(self,savedProfile.getProfile().getIdentityNumber());fixture.service.review(fixture.f.operator("B1"),self,fixture.decision(submittedProfile));
            var entry=new EntryForm();entry.setIntent("LOAN");entry.setClientNumber(number);UUID nonce=portal.request(entry,"127.0.0.51");var session=portal.verify(nonce,OTP.get(),"127.0.0.51").orElseThrow();var owner=portal.actor(session,true);
            // Record the first application through the assisted path, then continue as the client.
            var f=new Form();f.setClientId(self);f.setProductId(fixture.f.product);f.setAmount(new BigDecimal("100000.00"));f.setTenureValue(2);f.setApplicationDate(fixture.day);f.setIncome(new BigDecimal("1500000.00"));f.setExpenses(new BigDecimal("200000.00"));f.setOtherDebt(BigDecimal.ZERO);f.setPurpose("Business stock");f.setUseOfFunds("Synthetic inventory purchase");
            var asset=new Collateral();asset.setItemName("Synthetic machine");asset.setItemNumber("TEST-1");asset.setDescription("Declared business asset");asset.setEstimatedValue(new BigDecimal("150000.00"));f.getCollateral().add(asset);
            f.setDocumentNames(new ArrayList<>(List.of("Signed synthetic application")));f.setDocumentTypes(new ArrayList<>(List.of("SIGNED_APPLICATION")));f.setRequiredAttachmentIds(new ArrayList<>(List.of("")));
            var file=new MockMultipartFile("files","application.png","image/png",fixture.image);UUID id=applications.save(fixture.f.actor,f,List.of(file));assertThat(applications.save(fixture.f.actor,f,List.of(file))).isEqualTo(id);assertThat(applications.details(owner,id).status()).isEqualTo("DRAFT");assertThat(applications.list(owner,"",0).rows()).extracting(Row::id).contains(id);assertThat(applications.documents(owner,id)).hasSize(1);assertThat(applications.download(owner,id,applications.documents(owner,id).getFirst().get("id").toString()).bytes()).isEqualTo(fixture.image);
            var submit=applications.form(owner,null,id);submit.setCommand("SUBMIT");submit.setDeclarationAccepted(true);assertThat(applications.save(owner,submit,List.of())).isEqualTo(id);assertThat(applications.details(owner,id).status()).isEqualTo("READY_FOR_MANAGER");assertThat(fixture.f.jdbc.queryForObject("select form_data->'recordingDetails'->>'useOfFunds' from loan_applications where id=?",String.class,id)).isEqualTo("Synthetic inventory purchase");assertThat(fixture.f.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,fixture.f.institution)).isZero();
            assertThatThrownBy(()->applications.details(fixture.f.operator("B2"),id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            // A completed/rejected request permits another application without another profile or identity.
            fixture.f.jdbc.update("update loan_applications set status='MANAGER_REJECTED' where id=?",id);var repeat=applications.form(owner,self,null);repeat.setProductId(fixture.f.product);repeat.setAmount(new BigDecimal("100000.00"));repeat.setTenureValue(2);var second=applications.save(owner,repeat,List.of());assertThat(second).isNotEqualTo(id);assertThat(fixture.f.jdbc.queryForObject("select member_no from members where id=?",String.class,self)).isEqualTo(number);
            // Use the HTTP session to request loan access and render every new form/details page.
            String contactKey=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(("request/contact/"+self).getBytes(java.nio.charset.StandardCharsets.UTF_8)));fixture.f.jdbc.update("update client_portal_rate_limit set last_requested=now()-interval '61 seconds' where key=?",contactKey);
            String entryPage=get(http,base+"/client-portal");var access=ClientOnboardingRenderedPostgresTest.hidden(entryPage);access.put("intent","LOAN");access.put("clientNumber",number);post(http,base+"/client-portal/request-code",access);var accessCode=ClientOnboardingRenderedPostgresTest.hidden(get(http,base+"/client-portal/verify"));accessCode.put("code",OTP.get());post(http,base+"/client-portal/verify",accessCode);
            assertThat(get(http,base+"/client-portal/workspace/applications")).contains("Client applications");assertThat(get(http,base+"/client-portal/workspace/applications/new")).contains("Application information","Collateral","Documents");assertThat(get(http,base+"/client-portal/workspace/applications/"+second+"/edit")).contains("100000.00");assertThat(get(http,base+"/client-portal/workspace/applications/"+id)).contains("Synthetic machine","Signed synthetic application");assertThat(get(http,base+"/client-portal/workspace/applications/new?lang=sw")).contains("Rekodi ombi","Taarifa za ombi");
            String editPage=get(http,base+"/client-portal/workspace/applications/"+second+"/edit");var updated=ClientOnboardingRenderedPostgresTest.hidden(editPage);updated.put("productId",fixture.f.product.toString());updated.put("amount","110000.00");updated.put("tenureUnit","MONTHS");updated.put("tenureValue","2");updated.put("applicationDate",fixture.day.toString());updated.put("purpose","NEW BUSINESS STOCK");updated.put("command","DRAFT");post(http,base+"/client-portal/workspace/applications/save",updated);assertThat(applications.details(owner,second).version()).isEqualTo(2);
            int seconds=Integer.getInteger("microfinance.portal.browser-seconds",0);if(seconds>0){System.out.println("CLIENT_PORTAL_BROWSER_QA="+base);System.out.println("CLIENT_PORTAL_BROWSER_NUMBER="+number);System.out.println("CLIENT_PORTAL_BROWSER_INSTITUTION="+fixture.f.institution);System.out.println("CLIENT_PORTAL_BROWSER_PRODUCT="+fixture.f.product);var stop=java.nio.file.Path.of("target/client-portal-browser-stop");java.nio.file.Files.deleteIfExists(stop);for(int i=0;i<Math.min(seconds,900)&&!java.nio.file.Files.exists(stop);i++)Thread.sleep(1000);}
        }finally{if(app!=null)app.close();fixture.stop();}
    }
    @Test
    void staffAndBorrowerUseTheSharedApplicationForm() throws Exception {
        var fixture = new ClientOnboardingPostgresTest();
        fixture.start();
        ConfigurableApplicationContext app = null;
        try {
            fixture.fixture();
            fixture.f.jdbc.update("update loan_product_settings set application_fee=0, processing_fee_rate=0, insurance_rate=0, affordability_check_required=true where id=?", fixture.f.product);
            fixture.f.jdbc.update("insert into member_staff_roles(member_id,role_name) values(?,'MANAGER') on conflict do nothing", fixture.f.actor.getMemberId());
            for (String claim : List.of("CLIENT_ONBOARDING_VIEW", "CLIENT_ONBOARDING_UPDATE")) {
                fixture.f.jdbc.update("insert into member_access_claims(member_id,claim_name) values(?,?) on conflict do nothing",
                    fixture.f.actor.getMemberId(), claim);
            }
            app = new SpringApplicationBuilder(MicrofinanceApplication.class, DeliveryConfig.class).run(
                "--server.port=0", "--spring.config.import=", "--spring.datasource.url=" + System.getenv("MICROFINANCE_RECORDING_TEST_URL"),
                "--spring.datasource.username=microfinance_test", "--spring.datasource.password=",
                "--spring.datasource.hikari.minimum-idle=1", "--spring.datasource.hikari.maximum-pool-size=6",
                "--app.outbox.scheduler-enabled=false", "--app.sms.enabled=false", "--app.retention.cleanup-enabled=false",
                "--app.uploads.root=target/client-portal-test-uploads", "--spring.devtools.restart.enabled=false");
            var encoder = app.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);
            String password = "Synthetic-Application-Only-25!";
            String staffLogin = "staff-" + UUID.randomUUID() + "@example.invalid";
            String borrowerLogin = "borrower-" + UUID.randomUUID() + "@example.invalid";
            fixture.f.jdbc.update("update members set email=?, password_hash=? where id=?",
                staffLogin, encoder.encode(password), fixture.f.actor.getMemberId());
            UUID borrower = fixture.f.client;
            fixture.f.jdbc.update("update members set email=? where id=?", borrowerLogin, borrower);
            UUID submittedBorrowerProfile = fixture.submitted(borrower, "TZ-BORROWER-" + UUID.randomUUID());
            fixture.service.review(fixture.f.operator("B1"), borrower, fixture.decision(submittedBorrowerProfile));
            fixture.f.jdbc.update("update members set client_portal_enabled=true, password_hash=? where id=?", encoder.encode(password), borrower);
            for (String claim : List.of("MEMBER_LOANS_CREATE", "MEMBER_LOANS_UPDATE", "MEMBER_LOANS_VIEW")) {
                fixture.f.jdbc.update("insert into member_access_claims(member_id,claim_name) values(?,?) on conflict do nothing",
                    borrower, claim);
            }
            var userSettings = app.getBean(com.sacco.mvp.service.UserSettingsService.class);
            userSettings.updateOtpPreferences(fixture.f.actor.getMemberId(), false, true);
            userSettings.updateOtpPreferences(borrower, false, true);
            String base = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
            var applications = app.getBean(ClientApplicationService.class);
            UUID assistedClient = UUID.randomUUID();
            fixture.f.jdbc.update("""
                insert into members (id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,
                    phone,client_portal_enabled,created_at)
                values (?,?,'B1',?,'Synthetic assisted client','ACTIVE','MEMBER',true,'!NO_PORTAL_ACCESS',?,false,now())
                """, assistedClient, fixture.f.institution, assistedClient.toString(), fixture.phone());
            var draft = applications.form(fixture.f.actor, assistedClient, null);
            draft.setProductId(fixture.f.product);
            draft.setAmount(new BigDecimal("100000.00"));
            draft.setTenureValue(2);
            draft.setDocumentNames(new ArrayList<>(List.of("Signed synthetic application", "Temporary synthetic evidence")));
            draft.setDocumentTypes(new ArrayList<>(List.of("SIGNED_APPLICATION", "OTHER")));
            draft.setRequiredAttachmentIds(new ArrayList<>(List.of("", "")));
            var file = new MockMultipartFile("files", "application.png", "image/png", fixture.image);
            UUID id = applications.save(fixture.f.actor, draft, List.of(file, file));
            var staffHttp = login(base, staffLogin, password, true);
            String staffForm = get(staffHttp, base + "/finance/client-applications/" + id + "/edit");
            assertThat(staffForm).contains("Record application", "Preferred loan officer",
                "data-application-panel=\"guarantors\"", "data-repayment-estimate", "Signed synthetic application");
            var staffQuote = ClientOnboardingRenderedPostgresTest.hidden(staffForm);
            staffQuote.put("productId", fixture.f.product.toString());
            staffQuote.put("amount", "100000.00");
            staffQuote.put("tenureUnit", "MONTHS");
            staffQuote.put("tenureValue", "2");
            staffQuote.put("applicationDate", fixture.day.toString());
            var quoted = ClientOnboardingRenderedPostgresTest.submit(staffHttp, base + "/finance/client-applications/quote", staffQuote, false);
            assertThat(quoted.statusCode()).as(quoted.body()).isEqualTo(200);
            assertThat(quoted.body()).contains("repaymentSchedule", "principalAmount", "Month 2");
            String extraDocument = applications.documents(fixture.f.actor, id).stream()
                .filter(document -> "OTHER".equals(document.get("documentPurpose"))).findFirst().orElseThrow().get("id").toString();
            var remove = ClientOnboardingRenderedPostgresTest.hidden(staffForm);
            String removeUrl = base + "/finance/client-applications/" + id + "/documents/" + extraDocument + "/remove";
            var withoutCsrf = new LinkedHashMap<>(remove);
            withoutCsrf.remove("_csrf");
            assertThat(ClientOnboardingRenderedPostgresTest.submit(staffHttp, removeUrl, withoutCsrf, false).statusCode()).isEqualTo(403);
            post(staffHttp, removeUrl, remove);
            post(staffHttp, removeUrl, remove);
            assertThat(applications.details(fixture.f.actor, id).version()).isEqualTo(2);
            assertThat(applications.documents(fixture.f.actor, id)).extracting(document -> document.get("documentTitle"))
                .containsExactly("Signed synthetic application");
            var borrowerHttp = login(base, borrowerLogin, password, false);
            String borrowerPath = "/app/loan-applications";
            String borrowerForm = get(borrowerHttp, base + borrowerPath + "/new?loanProductId=" + fixture.f.product);
            assertThat(borrowerForm).contains("data-borrower-workspace=\"true\"", "Application information",
                "Guarantors", "Collateral", "Documents", "data-repayment-estimate")
                .doesNotContain("data-loan-flow-step", "Preferred loan officer", "Signed application");
            var borrowerFields = ClientOnboardingRenderedPostgresTest.hidden(borrowerForm);
            borrowerFields.put("amount", "120000.00");
            borrowerFields.put("tenureUnit", "MONTHS");
            borrowerFields.put("tenureValue", "2");
            borrowerFields.put("income", "1500000.00");
            borrowerFields.put("expenses", "200000.00");
            borrowerFields.put("otherDebt", "0");
            borrowerFields.put("purpose", "STOCK");
            borrowerFields.put("useOfFunds", "Borrower shared form inventory");
            borrowerFields.put("action", "SAVE_DRAFT");
            var borrowerQuote = ClientOnboardingRenderedPostgresTest.submit(borrowerHttp, base + borrowerPath + "/financial-preview", borrowerFields, false);
            assertThat(borrowerQuote.statusCode()).as(borrowerQuote.body()).isEqualTo(200);
            assertThat(borrowerQuote.body()).contains("repaymentSchedule", "Month 2", "DECLARED_NOT_VERIFIED");
            var borrowerDraft = ClientOnboardingRenderedPostgresTest.submit(borrowerHttp, base + borrowerPath, borrowerFields, false);
            assertThat(borrowerDraft.statusCode()).as(borrowerDraft.body()).isEqualTo(302);
            String borrowerEdit = borrowerDraft.headers().firstValue("Location").orElseThrow();
            assertThat(URI.create(borrowerEdit).getPath()).startsWith(borrowerPath + "/").endsWith("/edit");
            assertThat(get(borrowerHttp, URI.create(base).resolve(borrowerEdit).toString()))
                .contains("Borrower shared form inventory", "120000.00", "name=\"action\" value=\"SEND_TO_GUARANTORS\"");
            assertThat(fixture.f.jdbc.queryForObject("select form_data->>'monthlyIncome' from loan_applications where applicant_member_id=?", String.class, borrower))
                .isEqualTo("1500000.00");
            int seconds = Integer.getInteger("microfinance.portal.browser-seconds", 0);
            if (seconds > 0) {
                System.out.println("SHARED_APPLICATION_BROWSER_QA=" + base);
                System.out.println("APPLICATION_BROWSER_STAFF=" + staffLogin);
                System.out.println("APPLICATION_BROWSER_BORROWER=" + borrowerLogin);
                System.out.println("APPLICATION_BROWSER_EDIT=" + borrowerEdit);
                System.out.println("CLIENT_PORTAL_BROWSER_PRODUCT=" + fixture.f.product);
                var stop = java.nio.file.Path.of("target/shared-application-browser-stop");
                java.nio.file.Files.deleteIfExists(stop);
                for (int elapsed = 0; elapsed < Math.min(seconds, 600) && !java.nio.file.Files.exists(stop); elapsed++) {
                    Thread.sleep(1000);
                }
            }
        } finally {
            if (app != null) app.close();
            fixture.stop();
        }
    }

    private static HttpClient login(String base, String identifier, String password, boolean staff) throws Exception {
        var http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(10)).build();
        var fields = ClientOnboardingRenderedPostgresTest.hidden(get(http, base + "/login"));
        fields.keySet().retainAll(Set.of("_csrf"));
        fields.put("username", identifier);
        fields.put("password", password);
        if (staff) fields.put("loginType", "staff-password");
        var login = ClientOnboardingRenderedPostgresTest.submit(http, base + "/login", fields, false);
        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("Location").orElseThrow()).doesNotContain("error");
        return http;
    }

    static String get(HttpClient http,String url)throws Exception{var r=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(90)).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).as("GET %s: %s",url,r.body()).isEqualTo(200);return r.body();}
    static void post(HttpClient http,String url,Map<String,String> fields)throws Exception{ClientOnboardingRenderedPostgresTest.post(http,url,fields,false);}
    @TestConfiguration static class DeliveryConfig {
        @Bean @Primary StationOtpDeliveryService testOtpDelivery(){var d=mock(StationOtpDeliveryService.class);doAnswer(i->{OTP.set(i.getArgument(7,String.class));System.out.println("CLIENT_PORTAL_TEST_OTP="+OTP.get());return null;}).when(d).deliver(anyString(),anyString(),nullable(String.class),anyString(),any(),anyString(),anyString(),anyString(),anyInt());return d;}
    }
}
