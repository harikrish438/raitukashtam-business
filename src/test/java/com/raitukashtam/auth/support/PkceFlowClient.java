package com.raitukashtam.auth.support;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Drives the real Authorization Code + PKCE dance over plain HTTP, the
 * same sequence a browser (or raitukashtam-web) actually performs --
 * GET /oauth2/authorize -> 302 to /login -> POST /login -> 302 back to the
 * saved /oauth2/authorize request -> 302 to the client's redirect_uri with
 * a code -> POST /oauth2/token to exchange it. Deliberately not a shortcut
 * (e.g. minting a JWT directly): this is the one thing that has to be
 * proven to actually work end-to-end, not just assumed from reading the
 * config. TestRestTemplate's default HttpClientOptions (none) mean it does
 * NOT auto-follow redirects or manage a cookie jar -- both handled
 * manually here so each hop can be inspected/asserted on.
 */
public class PkceFlowClient {

    private final TestRestTemplate restTemplate;
    private String sessionCookie;

    public PkceFlowClient(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public record PkcePair(String verifier, String challenge) {
    }

    public static PkcePair generatePkce() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return new PkcePair(verifier, challenge);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Full happy-path login: authorize -> login page -> submit credentials
     * -> follow back to authorize -> extract the issued code -> exchange
     * it. Throws AssertionError with the actual response at whichever hop
     * didn't behave as expected, so a failing test points straight at the
     * broken step instead of a generic NPE.
     */
    public String loginAndGetAccessToken(String baseUrl, String clientId, String redirectUri,
                                          String username, String password) {
        PkcePair pkce = generatePkce();
        ResponseEntity<Void> toLogin = startAuthorize(baseUrl, clientId, redirectUri, pkce.challenge(), "state123");
        requireStatus(toLogin, 302, "GET /oauth2/authorize (unauthenticated)");

        ResponseEntity<Void> loginResult = submitLogin(baseUrl, username, password, null);
        requireStatus(loginResult, 302, "POST /login");
        String backToAuthorize = location(loginResult);
        if (backToAuthorize.contains("/login?error")) {
            throw new AssertionError("Login failed unexpectedly, redirected to: " + backToAuthorize);
        }

        ResponseEntity<Void> toRedirectUri = get(backToAuthorize);
        requireStatus(toRedirectUri, 302, "GET /oauth2/authorize (authenticated)");
        String code = extractQueryParam(location(toRedirectUri), "code");

        TokenResult token = exchangeCode(baseUrl, code, redirectUri, clientId, pkce.verifier());
        if (token.accessToken() == null) {
            throw new AssertionError("Token exchange did not return an access_token: " + token.body());
        }
        return token.accessToken();
    }

    public ResponseEntity<Void> startAuthorize(String baseUrl, String clientId, String redirectUri,
                                                String codeChallenge, String state) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "api")
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", state)
                .build().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE);
        ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Void.class);
        captureCookie(response);
        return response;
    }

    public ResponseEntity<Void> submitLogin(String baseUrl, String username, String password, String recaptchaToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", password);
        if (recaptchaToken != null) {
            form.add("recaptchaToken", recaptchaToken);
        }
        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl + "/login", HttpMethod.POST, new HttpEntity<>(form, headers), Void.class);
        captureCookie(response);
        return response;
    }

    public ResponseEntity<Void> get(String url) {
        // Every caller passes a Location header Spring Security itself
        // issued, which it always reconstructs as an absolute URL (scheme
        // + this test's dynamic port) -- no relative-path guessing needed
        // or safe to do, since the port varies per test run.
        if (!url.startsWith("http")) {
            throw new IllegalArgumentException("Expected an absolute URL, got: " + url);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Void.class);
        captureCookie(response);
        return response;
    }

    public record TokenResult(String accessToken, java.util.Map<String, Object> body, int status) {
    }

    public TokenResult exchangeCode(String baseUrl, String code, String redirectUri, String clientId,
                                     String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", clientId);
        form.add("code_verifier", codeVerifier);
        return postToken(baseUrl, form, null, null);
    }

    public TokenResult clientCredentials(String baseUrl, String clientId, String clientSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        return postToken(baseUrl, form, clientId, clientSecret);
    }

    @SuppressWarnings("unchecked")
    private TokenResult postToken(String baseUrl, MultiValueMap<String, String> form, String basicUser, String basicPass) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (basicUser != null) {
            headers.setBasicAuth(basicUser, basicPass);
        }
        ResponseEntity<java.util.Map> response = restTemplate.exchange(
                baseUrl + "/oauth2/token", HttpMethod.POST, new HttpEntity<>(form, headers), java.util.Map.class);
        java.util.Map<String, Object> body = response.getBody();
        String accessToken = body == null ? null : (String) body.get("access_token");
        return new TokenResult(accessToken, body, response.getStatusCode().value());
    }

    private void captureCookie(ResponseEntity<?> response) {
        // Spring Session (spring-session-data-redis) names its cookie
        // "SESSION", not the servlet container's default "JSESSIONID" --
        // confirmed live, cost a debug round-trip the first time.
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies != null) {
            for (String setCookie : setCookies) {
                if (setCookie.startsWith("SESSION=")) {
                    this.sessionCookie = setCookie.split(";", 2)[0];
                }
            }
        }
    }

    private static void requireStatus(ResponseEntity<?> response, int expected, String step) {
        if (response.getStatusCode().value() != expected) {
            throw new AssertionError(step + " expected HTTP " + expected + " but got "
                    + response.getStatusCode() + " (Location: " + response.getHeaders().getLocation() + ")");
        }
    }

    private static String location(ResponseEntity<?> response) {
        URI location = response.getHeaders().getLocation();
        return location == null ? "" : location.toString();
    }

    private static String extractQueryParam(String url, String param) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(param);
    }
}
