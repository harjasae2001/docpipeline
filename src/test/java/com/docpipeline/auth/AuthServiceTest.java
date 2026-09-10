package com.docpipeline.auth;

import com.docpipeline.auth.dto.AuthResponse;
import com.docpipeline.auth.dto.LoginRequest;
import com.docpipeline.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthServiceTest {
    @Test
    void loginReturnsSupabaseSessionUsingCompatibleResponseFields() {
        AppProperties properties = new AppProperties();
        properties.getSupabase().setUrl("https://project.supabase.co");
        properties.getSupabase().setPublishableKey("sb_publishable_test");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://project.supabase.co/auth/v1/token?grant_type=password"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"access_token":"access","refresh_token":"refresh","expires_in":3600,
                         "user":{"email":"demo@example.com","user_metadata":{"full_name":"Demo User"}}}
                        """, MediaType.APPLICATION_JSON));

        AuthService service = new AuthService(builder, properties, new ObjectMapper());
        AuthResponse response = service.login(new LoginRequest("demo@example.com", "Password123!"));

        assertThat(response.token()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.fullName()).isEqualTo("Demo User");
        server.verify();
    }
}
