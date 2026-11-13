package com.chubb.claimops.common.config;

import com.chubb.claimops.common.auth.CurrentUser;
import com.chubb.claimops.common.auth.HeaderAuthInterceptor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The API contract, generated from the controllers and DTOs: JSON at {@code /v3/api-docs}, Swagger UI at
 * {@code /swagger-ui.html}.
 *
 * <p><b>Who defines the contract:</b> the backend does, code-first. The Java records are the source of truth and this
 * spec is derived from them, so the documentation can't drift from the implementation. The Angular models mirror
 * these DTOs today; generating them from this spec is the next step.
 *
 * <p>The two identity headers are declared as API-key schemes, so Swagger UI's "Authorize" button sets them once for
 * every call. {@link CurrentUser} is resolved from those headers, not from the request, so it's hidden from the
 * parameter list.
 */
@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    public OpenAPI claimOpsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ClaimOps API")
                        .version("v1")
                        .description("APAC motor and property claims. Errors use RFC 9457 Problem Details."))
                .components(new Components()
                        .addSecuritySchemes("role", header(HeaderAuthInterceptor.ROLE_HEADER, "CLAIMANT or STAFF"))
                        .addSecuritySchemes("user", header(HeaderAuthInterceptor.NAME_HEADER, "Caller's name")))
                .addSecurityItem(new SecurityRequirement().addList("role").addList("user"));
    }

    private static SecurityScheme header(String name, String description) {
        return new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(name)
                .description(description);
    }
}
