package com.sacco.mvp.web;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ErrorPageControllerTest {
    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    void forbiddenPageSupportsSecurityForwardsWithoutLosingThe403Status(String method) throws Exception {
        MockMvcBuilders.standaloneSetup(new ErrorPageController()).build()
            .perform(request(HttpMethod.valueOf(method), "/error/403")
                .requestAttr(RequestDispatcher.FORWARD_REQUEST_URI, "/repayments/loans/example/payments"))
            .andExpect(status().isForbidden())
            .andExpect(view().name("error/general"))
            .andExpect(model().attribute("errorStatus", 403))
            .andExpect(model().attribute("errorPath", "/repayments/loans/example/payments"));
    }
}
