package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class WebRequestClassifierTest {

    @Test
    void recognizesJsonAcceptHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "application/json");

        assertThat(WebRequestClassifier.isJsonRequest(request)).isTrue();
    }

    @Test
    void recognizesXmlHttpRequestHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        assertThat(WebRequestClassifier.isJsonRequest(request)).isTrue();
    }

    @Test
    void leavesHtmlRequestsUnchanged() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "text/html");

        assertThat(WebRequestClassifier.isJsonRequest(request)).isFalse();
    }
}
