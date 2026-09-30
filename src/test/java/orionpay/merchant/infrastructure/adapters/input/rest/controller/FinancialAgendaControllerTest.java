package orionpay.merchant.infrastructure.adapters.input.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;
import orionpay.merchant.config.JpaConfig;
import orionpay.merchant.config.JpaUserDetailsService;
import orionpay.merchant.config.SecurityConfig;
import orionpay.merchant.domain.service.FinancialAgendaService;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaAuthUserRepository;
import orionpay.merchant.config.JwtAuthenticationFilter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = FinancialAgendaController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JpaUserDetailsService.class, JpaConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class FinancialAgendaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinancialAgendaService service;

    @MockBean
    private JpaAuthUserRepository authUserRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void loadContext() {
    }
}
