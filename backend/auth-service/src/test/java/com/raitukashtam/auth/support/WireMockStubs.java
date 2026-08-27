package com.raitukashtam.auth.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/** Canned 2Factor.in / Google reCAPTCHA responses, stubbed at the HTTP level. */
public final class WireMockStubs {

    private WireMockStubs() {
    }

    public static final String OTP_SESSION_ID = "test-session-id";
    public static final String CORRECT_OTP = "123456";

    /** AUTOGEN always succeeds, always hands back the same known session id. */
    public static void stubOtpGenerateSuccess() {
        stubFor(get(urlPathMatching("/2factor/.*/SMS/.*/AUTOGEN"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Status\":\"Success\",\"Details\":\"" + OTP_SESSION_ID + "\"}")));
    }

    public static void stubOtpGenerateFailure() {
        stubFor(get(urlPathMatching("/2factor/.*/SMS/.*/AUTOGEN"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Status\":\"Error\",\"Details\":\"Invalid Phone Number\"}")));
    }

    /** CORRECT_OTP verifies successfully; any other code (matched by the catch-all) doesn't. */
    public static void stubOtpVerifyBehavior() {
        stubFor(get(urlPathMatching("/2factor/.*/SMS/VERIFY/.*/" + CORRECT_OTP))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Status\":\"Success\",\"Details\":\"OTP Matched\"}")));
        stubFor(get(urlPathMatching("/2factor/.*/SMS/VERIFY/.*"))
                .atPriority(2)
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Status\":\"Error\",\"Details\":\"OTP Mismatch\"}")));
    }

    public static void stubRecaptchaSuccess() {
        stubFor(post(urlPathMatching("/recaptcha/siteverify"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true}")));
    }

    public static void stubRecaptchaFailure() {
        stubFor(post(urlPathMatching("/recaptcha/siteverify"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":false}")));
    }
}
