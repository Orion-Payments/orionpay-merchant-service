package orionpay.merchant.infrastructure.adapters.input.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import orionpay.merchant.domain.service.AuthenticationService;
import orionpay.merchant.domain.service.RegisterMerchantUseCase;
import java.util.UUID;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.LoginRequest;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.MerchantRegistrationRequest;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.RefreshTokenRequest;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.TokenResponse;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import orionpay.merchant.config.SecurityConfig;
import orionpay.merchant.config.JpaUserDetailsService;
import orionpay.merchant.config.JpaConfig;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JpaUserDetailsService.class, JpaConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private RegisterMerchantUseCase registerMerchantUseCase;

    @MockBean
    private orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaAuthUserRepository authUserRepository;

    @MockBean
    private orionpay.merchant.config.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void login_ShouldReturnOk() throws Exception {
        LoginRequest request = new LoginRequest("admin@test.com", "password");

        TokenResponse response = new TokenResponse("access_token", "refresh_token", "Bearer", 3600L, 7200L, "MERCHANT", UUID.randomUUID());
        when(authenticationService.login(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access_token"));
    }

    @Test
    void refresh_ShouldReturnOk() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh_token");

        TokenResponse response = new TokenResponse("new_access_token", "new_refresh_token", "Bearer", 3600L, 7200L, "MERCHANT", UUID.randomUUID());
        when(authenticationService.refresh(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new_access_token"));
    }

    @Test
    void register_ShouldReturnCreated() throws Exception {
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Test Merchant", "12345678909", "test@test.com", "senhasegura",
                "12345678", "Street", "123", "Comp", "Bairro", "City", "SP",
                "341", "0001", "12345", "6", orionpay.merchant.domain.model.enums.AccountType.CHECKING);

        when(registerMerchantUseCase.execute(any())).thenReturn(null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
