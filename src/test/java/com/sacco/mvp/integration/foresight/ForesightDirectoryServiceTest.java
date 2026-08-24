package com.sacco.mvp.integration.foresight;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForesightDirectoryServiceTest {

    @Test
    void phoneLookupReturnsFoundForValidMemberProfile() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = server(200, """
            {"memberNo":"MBR-001","stationId":"ST-1","saccoName":"Demo SACCO"}
            """, path, query);
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            ForesightDirectoryService.MemberProfileLookupResult result =
                service.lookupMemberProfileByPhone("+2556764239920");

            assertThat(result.status()).isEqualTo(ForesightDirectoryService.MemberProfileLookupStatus.FOUND);
            assertThat(result.profile().memberNo()).isEqualTo("MBR-001");
            assertThat(path.get()).isEqualTo("/member-profile");
            assertThat(query.get()).isEqualTo("phoneNumber=+2556764239920");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void emailV2LookupReturnsFoundForValidMemberProfile() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = server(200, """
            {"memberNo":"MBR-002","stationId":"ST-1","saccoName":"Demo SACCO"}
            """, path, query);
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            ForesightDirectoryService.MemberProfileLookupResult result =
                service.lookupMemberProfileByEmailV2("anna.smith@example.com");

            assertThat(result.status()).isEqualTo(ForesightDirectoryService.MemberProfileLookupStatus.FOUND);
            assertThat(result.profile().memberNo()).isEqualTo("MBR-002");
            assertThat(path.get()).isEqualTo("/member-profile-v2");
            assertThat(query.get()).isEqualTo("email=anna.smith@example.com");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void lookupReturnsNotFoundFor404() throws Exception {
        HttpServer server = server(404, "", new AtomicReference<>(), new AtomicReference<>());
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            ForesightDirectoryService.MemberProfileLookupResult result =
                service.lookupMemberProfileByEmailV2("missing@example.com");

            assertThat(result.status()).isEqualTo(ForesightDirectoryService.MemberProfileLookupStatus.NOT_FOUND);
            assertThat(result.profile()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void lookupReturnsUnavailableWhenNoResponseCanBeRead() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        ForesightDirectoryService service = service("http://localhost:" + port, Duration.ofMillis(100));

        ForesightDirectoryService.MemberProfileLookupResult result =
            service.lookupMemberProfileByPhone("+2556764239920");

        assertThat(result.status()).isEqualTo(ForesightDirectoryService.MemberProfileLookupStatus.UNAVAILABLE);
    }

    @Test
    void lookupReturnsUnavailableForEmptyProfileBody() throws Exception {
        HttpServer server = server(200, "{}", new AtomicReference<>(), new AtomicReference<>());
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            ForesightDirectoryService.MemberProfileLookupResult result =
                service.lookupMemberProfileByPhone("+2556764239920");

            assertThat(result.status()).isEqualTo(ForesightDirectoryService.MemberProfileLookupStatus.UNAVAILABLE);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void activeLoansLookupUsesRecordsPathAndParsesArrayResponse() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = server(200, """
            [
              {
                "loanId": "1001",
                "disbursedDate": "2024-03-15",
                "loanDescription": "Personal Loan",
                "requestedAmount": 500000.00,
                "disbursedAmount": 525000.00,
                "interestRate": 15.5,
                "totalInterest": 25000.00
              }
            ]
            """, path, query);
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            var loans = service.fetchActiveLoans("MEM001", "ST01");

            assertThat(loans).hasSize(1);
            ForesightActiveLoan loan = loans.getFirst();
            assertThat(loan.loanIdText()).isEqualTo("1001");
            assertThat(loan.loanDescription()).isEqualTo("Personal Loan");
            assertThat(loan.disbursedDate()).isEqualTo(LocalDate.of(2024, 3, 15));
            assertThat(loan.requestedAmount()).isEqualByComparingTo("500000.00");
            assertThat(loan.disbursedAmount()).isEqualByComparingTo("525000.00");
            assertThat(loan.totalInterest()).isEqualByComparingTo("25000.00");
            assertThat(path.get()).isEqualTo("/active-loans/records");
            assertThat(query.get()).isEqualTo("memberNumber=MEM001&stationId=ST01");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void activeLoansLookupReturnsEmptyListForEmptyArray() throws Exception {
        HttpServer server = server(200, "[]", new AtomicReference<>(), new AtomicReference<>());
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            assertThat(service.fetchActiveLoans("MEM001", "ST01")).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void activeLoansLookupThrowsAvailabilityExceptionForUpstreamFailure() throws Exception {
        HttpServer server = server(500, "{\"error\":\"down\"}", new AtomicReference<>(), new AtomicReference<>());
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            assertThatThrownBy(() -> service.fetchActiveLoans("MEM001", "ST01"))
                .isInstanceOf(UpstreamAvailabilityException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void accountSummaryParsesDocumentedOutstandingLoanFields() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = server(200, """
            {
              "savingsBalance": 100000.00,
              "sharesBalance": 200000.00,
              "depositsBalance": 300000.00,
              "outstandingLoans": [
                {
                  "loanId": 1001,
                  "loanDescription": "Education Loan",
                  "outstandingPrincipal": 700000.00,
                  "outstandingInterest": 70000.00
                }
              ]
            }
            """, path, query);
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            ForesightAccountSummary summary = service.fetchAccountSummary("MEM001", "ST01");

            assertThat(summary.savingsBalance()).isEqualByComparingTo("100000.00");
            assertThat(summary.outstandingLoans()).hasSize(1);
            ForesightAccountSummary.ForesightOutstandingLoan loan = summary.outstandingLoans().getFirst();
            assertThat(loan.loanIdText()).isEqualTo("1001");
            assertThat(loan.loanDescription()).isEqualTo("Education Loan");
            assertThat(loan.outstandingPrincipal()).isEqualByComparingTo("700000.00");
            assertThat(loan.outstandingInterest()).isEqualByComparingTo("70000.00");
            assertThat(loan.balanceIncludingInterest()).isEqualByComparingTo("770000.00");
            assertThat(path.get()).isEqualTo("/account-summary");
            assertThat(query.get()).isEqualTo("memberNumber=MEM001&stationId=ST01");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void loanPaymentSummaryLookupUsesDocumentedPathAndParsesArrayResponse() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = server(200, """
            [
              {
                "loanId": 1001,
                "loanDescription": "Personal Loan",
                "requestedAmount": 500000.00,
                "disbursedAmount": 525000.00,
                "interestRate": 15.5,
                "effectiveDate": "2024-03-15",
                "lastPaymentDate": "2024-04-10",
                "principalAmount": 500000.00,
                "interestAmount": 25000.00,
                "totalPrincipalPaid": 75000.00,
                "totalInterestPaid": 5000.00,
                "outstandingPrincipal": 425000.00,
                "outstandingInterest": 20000.00,
                "totalOutstanding": 445000.00
              }
            ]
            """, path, query);
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            var summaries = service.fetchLoanPaymentSummary("MEM001", "ST01", "1001");

            assertThat(summaries).hasSize(1);
            ForesightLoanPaymentSummary summary = summaries.getFirst();
            assertThat(summary.loanIdText()).isEqualTo("1001");
            assertThat(summary.loanDescription()).isEqualTo("Personal Loan");
            assertThat(summary.totalOutstanding()).isEqualByComparingTo("445000.00");
            assertThat(summary.outstandingPrincipal()).isEqualByComparingTo("425000.00");
            assertThat(summary.outstandingInterest()).isEqualByComparingTo("20000.00");
            assertThat(summary.totalPrincipalPaid()).isEqualByComparingTo("75000.00");
            assertThat(summary.totalInterestPaid()).isEqualByComparingTo("5000.00");
            assertThat(summary.lastPaymentDate()).isEqualTo(LocalDate.of(2024, 4, 10));
            assertThat(path.get()).isEqualTo("/loan-payment-summary");
            assertThat(query.get()).isEqualTo("memberNumber=MEM001&stationId=ST01&loanId=1001");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void loanPaymentSummaryLookupReturnsEmptyListForEmptyArray() throws Exception {
        HttpServer server = server(200, "[]", new AtomicReference<>(), new AtomicReference<>());
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            assertThat(service.fetchLoanPaymentSummary("MEM001", "ST01", "1001")).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void loanPaymentSummaryLookupThrowsAvailabilityExceptionForUpstreamFailure() throws Exception {
        HttpServer server = server(500, "{\"error\":\"down\"}", new AtomicReference<>(), new AtomicReference<>());
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            assertThatThrownBy(() -> service.fetchLoanPaymentSummary("MEM001", "ST01", "1001"))
                .isInstanceOf(UpstreamAvailabilityException.class);
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(int status,
                              String responseBody,
                              AtomicReference<String> path,
                              AtomicReference<String> query) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        return server;
    }

    private ForesightDirectoryService service(String baseUrl) {
        return service(baseUrl, Duration.ofSeconds(2));
    }

    private ForesightDirectoryService service(String baseUrl, Duration timeout) {
        ForesightDirectoryService service = new ForesightDirectoryService(RestClient.builder());
        ReflectionTestUtils.setField(service, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(service, "connectTimeout", timeout);
        ReflectionTestUtils.setField(service, "readTimeout", timeout);
        return service;
    }
}
