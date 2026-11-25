package com.sacco.mvp.web;

import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.service.AdminService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.jasper.servlet.JspServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AccessMatrixJspRenderingTest {
    @TempDir Path temporary;

    @Test
    void staffRoleCardsRenderTheirIllustrationsAndSavedSelections() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/user-edit.jsp"));
        int start = source.indexOf("<section id=\"user-access-roles\"");
        String page = """
            <%@ page session="false" %>
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
            """ + source.substring(start, source.indexOf("</section>", start) + "</section>".length());
        var positions = Position.staffAssignableRoles();
        var user = AdminService.UserAccessView.builder().staffRoles(EnumSet.of(Position.ICT_MANAGER)).build();
        var response = render(page, Map.of("staffPositions", positions, "user", user), "en");
        var cards = Pattern.compile("<label\\b[^>]*>(.*?)</label>", Pattern.DOTALL).matcher(response.body());
        var staticRoot = Path.of("src/main/resources/static");

        for (Position position : positions) {
            assertThat(cards.find()).as("Card for %s", position).isTrue();
            String card = cards.group(1);
            var checkbox = Pattern.compile("<input\\b[^>]*>").matcher(card);
            assertThat(checkbox.find()).as("Checkbox for %s", position).isTrue();
            assertThat(checkbox.group()).contains("type=\"checkbox\"", "name=\"positions\"", "value=\"" + position + "\"");
            assertThat(Pattern.compile("\\schecked(?:\\s|=|>)").matcher(checkbox.group()).find())
                .as("Saved selection for %s", position).isEqualTo(user.getStaffRoles().contains(position));
            assertThat(card).contains(">" + position.getDisplayName() + "</span>");

            if (position == Position.ADMIN) {
                assertThat(card).containsPattern("<svg\\b[^>]*aria-hidden=\"true\"");
                assertThat(card).contains("href=\"/images/staff-role-icons.svg#ADMIN\"");
                assertThat(Files.readString(staticRoot.resolve("images/staff-role-icons.svg"))).contains("id=\"ADMIN\"");
            } else {
                String icon = position == Position.ACCOUNTS_CLERK_2 ? "ACCOUNTS_CLERK" : position.name();
                String asset = "images/staff-roles/" + icon + ".png";
                assertThat(card).containsPattern("<img\\b[^>]*alt=\"\"").contains("src=\"/" + asset + "\"");
                assertThat(staticRoot.resolve(asset)).isRegularFile();
            }
        }
        assertThat(cards.find()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"en,Loan approval workflow setup","sw,Mipangilio ya hatua za kuidhinisha mikopo"})
    void savedClaimsRenderCheckedWhileUnassignedClaimsStayUnchecked(String language,String approvalLabel) throws Exception {
        String source=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/user-edit.jsp"));
        int start=source.indexOf("<c:set var=\"matrixColumns\"");
        String matrix=source.substring(start,source.indexOf("</section>",start)+"</section>".length());
        String page="""
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
            """+matrix;
        Set<UserClaim> assigned=EnumSet.of(UserClaim.MANAGER_QUEUE_VIEW,UserClaim.VOUCHER_RULE_CONFIGURE);
        var user=AdminService.UserAccessView.builder().claims(assigned).build();
        var response=render(page,Map.of("user",user,"accessMatrixRows",UserClaim.matrixRows(),"canGrantBranchView",true),language);
        assertThat(response.body()).contains("<span class=\"access-matrix-name\">"+approvalLabel+"</span>");
        for(var claim:UserClaim.matrixRows().stream().flatMap(row->row.claimsByAction().values().stream()).toList()){
            var input=Pattern.compile("<input[^>]*data-access-claim=\""+claim.name()+"\"[^>]*>").matcher(response.body());
            assertThat(input.find()).as(claim.name()).isTrue();
            assertThat(Pattern.compile("\\schecked(?:\\s|=|>)").matcher(input.group()).find())
                .as("Saved selection for %s",claim).isEqualTo(assigned.contains(claim));
        }
    }

    @Test void staffRegistryRendersRealUserRowsWithoutRetiredAcknowledgementProperties() throws Exception {
        String source=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/users.jsp"))
            .replaceAll("<%@ include[^%]*%>","");
        var user=AdminService.UserAccessView.builder().accountId(java.util.UUID.randomUUID()).userIdLabel("STAFF-10001")
            .fullName("Synthetic staff").membershipLabel("Staff").roleSummary("Credit Officer")
            .status(com.sacco.mvp.domain.MemberStatus.ACTIVE).displayStatus("ACTIVE").build();
        var response=render(source,Map.of("users",List.of(user),"usersPage",new org.springframework.data.domain.PageImpl<>(List.of(user)),
            "staffPositions",List.of(com.sacco.mvp.domain.Position.CREDIT_OFFICER),"canEditUsers",true,"selectedPageSize",25),"en");
        assertThat(response.body()).contains("STAFF-10001","Synthetic staff","/admin/users/"+user.getAccountId()+"/edit");
        assertThat(response.body()).contains("data-staff-registration", "Full name (three names)", "staff-email-status", "staff-phone-status", "data-staff-csrf")
            .doesNotContain("/profile/complete", "kyc.workspaceRequired");
    }

    private HttpResponse<String> render(String source,Map<String,Object> attributes,String language) throws Exception {
        Files.writeString(temporary.resolve("matrix.jsp"),source);
        var tomcat=new Tomcat();tomcat.setBaseDir(temporary.resolve("tomcat").toString());tomcat.setPort(0);
        var context=tomcat.addContext("",temporary.toString());
        context.setParentClassLoader(getClass().getClassLoader());
        context.addServletContainerInitializer(new JasperInitializer(),null);
        var spring=new StaticWebApplicationContext();
        context.addServletContainerInitializer((classes,servletContext)->{
            var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
            spring.getStaticMessageSource().setParentMessageSource(messages);
            spring.setServletContext(servletContext);spring.getStaticMessageSource().setUseCodeAsDefaultMessage(true);spring.refresh();
            servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,spring);
        },null);
        Tomcat.addServlet(context,"jsp",new JspServlet());context.addServletMappingDecoded("*.jsp","jsp");
        Tomcat.addServlet(context,"matrix",new HttpServlet(){
            @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws java.io.IOException,jakarta.servlet.ServletException {
                attributes.forEach(request::setAttribute);
                request.getRequestDispatcher("/matrix.jsp").forward(request,response);
            }
        });context.addServletMappingDecoded("/matrix","matrix");
        try {
            tomcat.getConnector();tomcat.start();
            var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/matrix"))
                .header("Accept-Language",language).timeout(Duration.ofSeconds(60)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            return response;
        } finally {tomcat.stop();tomcat.destroy();spring.close();}
    }
}
