package orionpay.merchant.infrastructure.adapters.input.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import orionpay.merchant.domain.service.AnticipationService;
import orionpay.merchant.domain.service.PrepaymentUseCase;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.AnticipationRequest;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.AnticipationResponse;
import orionpay.merchant.infrastructure.adapters.input.rest.security.SecurityContextService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import orionpay.merchant.config.SecurityConfig;
import orionpay.merchant.config.JpaUserDetailsService;
import orionpay.merchant.config.JpaConfig;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AnticipationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JpaUserDetailsService.class, JpaConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false) // Bypass de security para testes puros de Controller
class AnticipationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnticipationService anticipationService;

    @MockBean
    private PrepaymentUseCase prepaymentUseCase;

    @MockBean
    private SecurityContextService securityContextService;

    @MockBean
    private orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaAuthUserRepository authUserRepository;

    @MockBean
    private orionpay.merchant.config.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void getAvailableForAnticipation_ShouldReturnOk() throws Exception {
        UUID merchantId = UUID.randomUUID();
        AnticipationResponse mockResponse = AnticipationResponse.builder()
                .totalGrossToAnticipate(new BigDecimal("5000.00"))
                .totalCost(new BigDecimal("2.50"))
                .totalNetToReceive(new BigDecimal("4997.50"))
                .items(List.of())
                .build();

        when(anticipationService.getAvailableSettlements(merchantId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/merchants/{id}/anticipation/available", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGrossToAnticipate").value(5000.00));

        verify(anticipationService).getAvailableSettlements(merchantId);
    }

    @Test
    void getAnticipationPricing_ShouldReturnOk() throws Exception {
        UUID merchantId = UUID.randomUUID();
        when(anticipationService.getAnticipationFee(merchantId)).thenReturn(new BigDecimal("2.50"));

        mockMvc.perform(get("/api/v1/merchants/{id}/anticipation/pricing", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anticipationFee").value(2.50));
    }

    @Test
    void executeAnticipation_ShouldReturnOk() throws Exception {
        UUID merchantId = UUID.randomUUID();
        AnticipationRequest request = new AnticipationRequest(List.of(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/merchants/{id}/anticipation/execute", merchantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(prepaymentUseCase).execute(eq(merchantId), any(AnticipationRequest.class));
    }
}
