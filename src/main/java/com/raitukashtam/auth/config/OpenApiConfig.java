package com.raitukashtam.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI at /swagger-ui.html, raw spec at /v3/api-docs (both permitAll
 * in SecurityConfig). Three GroupedOpenApi dropdowns, split by @Tag on each
 * controller method rather than by path prefix -- the same path prefix
 * (/users/**, /products/**) often mixes audiences (e.g. GET /users/{id} is
 * business-service-facing, GET /users is platform-admin-only), so grouping
 * by path alone would misfile endpoints.
 */
@Configuration
public class OpenApiConfig {

    public static final String TAG_BUSINESS_SERVICE = "Business Service Integration";
    public static final String TAG_SELF_SERVICE = "Self-Service (Public)";
    public static final String TAG_PLATFORM_ADMIN = "Platform Admin";

    @Bean
    public OpenAPI authServiceOpenApi(@Value("${app.base-url}") String baseUrl) {
        String issuer = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        return new OpenAPI()
                .info(new Info()
                        .title("Raitukashtam auth-service API")
                        .version("v1")
                        .contact(new Contact().name("Raitukashtam"))
                        .description("""
                                Identity/auth platform for all Raitukashtam products and their client apps.

                                **Endpoint groups** (see the group dropdown, top-left of this page):
                                - **Business Service Integration** -- endpoints a backend service (e.g. \
                                product-service in raitukashtam-business) is meant to call, authenticated \
                                with a `client_credentials` token.
                                - **Self-Service (Public)** -- unauthenticated endpoints meant to be called \
                                directly by an end-user's browser/app (registration, login-adjacent flows, \
                                password reset, OTP, Google sign-in).
                                - **Platform Admin** -- `PLATFORM_ADMIN`-gated endpoints for onboarding \
                                products/clients/roles and managing other admins. Not for business-service \
                                or end-user consumption.

                                **How a business service authenticates** (for the Business Service \
                                Integration group): request a token via the standard OAuth2 \
                                `client_credentials` grant, then send it as a Bearer token on this API. \
                                Use the "Authorize" button above with a token you already have (get one \
                                first via curl -- pasting a live client secret into a browser page isn't \
                                appropriate even for internal tooling):

                                ```sh
                                curl -u '<client-id>:<client-secret>' \\
                                  -d 'grant_type=client_credentials' \\
                                  %s/oauth2/token
                                ```

                                To validate a user-facing (Authorization Code/PKCE) access token locally \
                                instead of calling back into auth-service per request, fetch this \
                                service's JWKS once and cache it: `GET %s/oauth2/jwks`.
                                """.formatted(issuer, issuer)))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
                // No global addSecurityItem() here on purpose: springdoc doesn't reliably let a
                // per-operation @SecurityRequirement(name = "") override a global default back to
                // "no auth" (confirmed live -- the override silently no-ops, leaving public
                // endpoints looking protected in the generated spec). Simpler and correct: mark
                // only the actually-protected operations/controllers with
                // @SecurityRequirement(name = "bearerAuth") instead of setting a global default.
    }

    @Bean
    public GroupedOpenApi businessServiceGroup() {
        return GroupedOpenApi.builder()
                .group("1-business-service")
                .displayName(TAG_BUSINESS_SERVICE)
                .addOpenApiMethodFilter(method -> hasTag(method, TAG_BUSINESS_SERVICE))
                .build();
    }

    @Bean
    public GroupedOpenApi selfServiceGroup() {
        return GroupedOpenApi.builder()
                .group("2-self-service")
                .displayName(TAG_SELF_SERVICE)
                .addOpenApiMethodFilter(method -> hasTag(method, TAG_SELF_SERVICE))
                .build();
    }

    @Bean
    public GroupedOpenApi platformAdminGroup() {
        return GroupedOpenApi.builder()
                .group("3-platform-admin")
                .displayName(TAG_PLATFORM_ADMIN)
                .addOpenApiMethodFilter(method -> hasTag(method, TAG_PLATFORM_ADMIN))
                .build();
    }

    @Bean
    public GroupedOpenApi allEndpointsGroup() {
        return GroupedOpenApi.builder()
                .group("0-all")
                .displayName("All endpoints")
                .pathsToMatch("/**")
                .build();
    }

    /**
     * addOpenApiMethodFilter's Predicate<Method> is a raw reflection handle
     * on the controller method, not the generated Operation -- checks both
     * the method's own @Tag (per-endpoint overrides, e.g. UserController)
     * and its declaring class's @Tag (whole-controller tags, e.g.
     * ProductController) since method-level Method#getAnnotation doesn't
     * see class-level annotations.
     */
    private static boolean hasTag(java.lang.reflect.Method method, String tagName) {
        io.swagger.v3.oas.annotations.tags.Tag methodTag =
                method.getAnnotation(io.swagger.v3.oas.annotations.tags.Tag.class);
        if (methodTag != null) {
            return tagName.equals(methodTag.name());
        }
        io.swagger.v3.oas.annotations.tags.Tag classTag =
                method.getDeclaringClass().getAnnotation(io.swagger.v3.oas.annotations.tags.Tag.class);
        return classTag != null && tagName.equals(classTag.name());
    }
}
