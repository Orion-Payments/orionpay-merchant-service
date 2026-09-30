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
import orionpay.merchant.domain.service.GetSettlementScheduleUseCase;
import orionpay.merchant.domain.service.GetSettlementDayDetailUseCase;
import orionpay.merchant.infrastructure.adapters.input.rest.security.SecurityContextService;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaAuthUserRepository;
import orionpay.merchant.config.JwtAuthenticationFilter;

@WebMvcTest(
        controllers = SettlementController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JpaUserDetailsService.class, JpaConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class SettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetSettlementScheduleUseCase scheduleUseCase;

    @MockBean
    private GetSettlementDayDetailUseCase dayDetailUseCase;

    @MockBean
    private SecurityContextService securityContextService;

    @MockBean
    private JpaAuthUserRepository authUserRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void loadContext() {
    }
}
