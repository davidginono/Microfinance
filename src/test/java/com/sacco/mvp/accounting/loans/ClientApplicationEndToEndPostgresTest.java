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
            fixture.fixture();fixture.service.saveNotice(fixture.f.actor,fixture.notice());
            fixture.f.jdbc.update("update loan_product_settings set application_fee=0,processing_fee_rate=0,insurance_rate=0,affordability_check_required=true where id=?",fixture.f.product);
            fixture.f.jdbc.update("update loan_product_settings set manager_review_required=true,disbursement_officer_required=true where id=?",fixture.f.product);
            fixture.f.jdbc.update("insert into member_staff_roles(member_id,role_name) values(?,'MANAGER') on conflict do nothing",fixture.f.actor.getMemberId());
            for(var claim:List.of("CLIENT_ONBOARDING_VIEW","CLIENT_ONBOARDING_UPDATE"))fixture.f.jdbc.update("insert into member_access_claims(member_id,claim_name) values(?,?) on conflict do nothing",fixture.f.actor.getMemberId(),claim);
            app=new SpringApplicationBuilder(MicrofinanceApplication.class,DeliveryConfig.class).run(
                "--server.port=0","--spring.config.import=","--spring.datasource.url="+System.getenv("MICROFINANCE_RECORDING_TEST_URL"),"--spring.datasource.username=microfinance_test","--spring.datasource.password=","--spring.datasource.hikari.minimum-idle=1","--spring.datasource.hikari.maximum-pool-size=6","--app.outbox.scheduler-enabled=false","--app.sms.enabled=false","--app.retention.cleanup-enabled=false","--app.uploads.root=target/client-portal-test-uploads","--spring.devtools.restart.enabled=false","--spring.main.allow-bean-definition-overriding=true");
            var portal=app.getBean(ClientPortalService.class);var applications=app.getBean(ClientApplicationService.class);
            String base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
            var http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build();
            String register=get(http,base+"/client-portal/register");assertThat(register).contains("Client portal","registration.profile.firstName");
            var fields=ClientOnboardingRenderedPostgresTest.hidden(register);fields.put("intent","REGISTER");fields.put("institution",fixture.f.institution);fields.put("branch","B1");fields.put("registration.profile.firstName","Synthetic");fields.put("registration.profile.lastName","Applicant");fields.put("registration.profile.phone",fixture.phone());fields.put("registration.profile.email",UUID.randomUUID()+"@example.invalid");
            post(http,base+"/client-portal/request-code",fields);String verifyPage=get(http,base+"/client-portal/verify");var verification=ClientOnboardingRenderedPostgresTest.hidden(verifyPage);verification.put("code",OTP.get());post(http,base+"/client-portal/verify",verification);
            String profile=get(http,base+"/client-portal/workspace/profile");assertThat(profile).contains("Bio data","Business details","Spouse details","Bank details").doesNotContain("/admin/");
            UUID self=fixture.f.jdbc.queryForObject("select id from members where email=?",UUID.class,fields.get("registration.profile.email"));String number=fixture.f.jdbc.queryForObject("select member_no from members where id=?",String.class,self);
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
    static String get(HttpClient http,String url)throws Exception{var r=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(90)).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).as("GET %s: %s",url,r.body()).isEqualTo(200);return r.body();}
    static void post(HttpClient http,String url,Map<String,String> fields)throws Exception{ClientOnboardingRenderedPostgresTest.post(http,url,fields,false);}
    @TestConfiguration static class DeliveryConfig {
        @Bean @Primary StationOtpDeliveryService testOtpDelivery(){var d=mock(StationOtpDeliveryService.class);doAnswer(i->{OTP.set(i.getArgument(7,String.class));System.out.println("CLIENT_PORTAL_TEST_OTP="+OTP.get());return null;}).when(d).deliver(anyString(),anyString(),nullable(String.class),anyString(),any(),anyString(),anyString(),anyString(),anyInt());return d;}
    }
}
