package orionpay.merchant.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import orionpay.merchant.application.ports.input.rest.dto.DashboardSummaryDto;
import orionpay.merchant.infrastructure.adapters.output.persistence.mapper.DashboardMapper;
import orionpay.merchant.infrastructure.adapters.output.persistence.projection.LedgerBalanceProjection;
import orionpay.merchant.infrastructure.adapters.output.persistence.projection.TransactionSummaryProjection;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaDailySummaryRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.LedgerRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.TerminalRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetDashboardSummaryUseCaseTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private JpaDailySummaryRepository dailySummaryRepository;
    @Mock
    private LedgerRepository ledgerRepository;
    @Mock
    private TerminalRepository terminalRepository;
    @Mock
    private DashboardMapper dashboardMapper;

    @InjectMocks
    private GetDashboardSummaryUseCase useCase;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
    }

    @Test
    void execute_ShouldReturnDashboardSummary_WhenCalledWithValidPeriod() {
        // Arrange
        TransactionSummaryProjection currentSummary = mock(TransactionSummaryProjection.class);
        TransactionSummaryProjection prevSummary = mock(TransactionSummaryProjection.class);
        LedgerBalanceProjection balances = mock(LedgerBalanceProjection.class);

        when(balances.getAvailableBalance()).thenReturn(new BigDecimal("1000.00"));
        when(balances.getFutureReceivables()).thenReturn(new BigDecimal("500.00"));

        when(dailySummaryRepository.findConsolidatedSummaryByPeriod(eq(merchantId), any(), any())).thenReturn(currentSummary, prevSummary);
        when(transactionRepository.getHourlyTrend(eq(merchantId), any())).thenReturn(Collections.emptyList());
        when(transactionRepository.getBrandDistribution(eq(merchantId), any())).thenReturn(Collections.emptyList());
        when(ledgerRepository.getLedgerBalances(merchantId)).thenReturn(balances);
        when(terminalRepository.countByMerchantIdAndStatus(merchantId, "ACTIVE")).thenReturn(5L);

        DashboardSummaryDto expectedDto = DashboardSummaryDto.builder().build();
        when(dashboardMapper.toDTO(any(), any(), any(), any(), any(), any(), any())).thenReturn(expectedDto);

        // Act
        DashboardSummaryDto result = useCase.execute(merchantId, "TODAY");

        // Assert
        assertThat(result).isNotNull();
        verify(dailySummaryRepository, times(2)).findConsolidatedSummaryByPeriod(eq(merchantId), any(), any());
        verify(dashboardMapper).toDTO(currentSummary, prevSummary, new BigDecimal("1000.00"), new BigDecimal("500.00"), 5L, Collections.emptyList(), Collections.emptyList());
    }

    @Test
    void execute_ShouldHandleNullBalancesAndReturnZeroes() {
        // Arrange
        when(ledgerRepository.getLedgerBalances(merchantId)).thenReturn(null);
        when(dashboardMapper.toDTO(any(), any(), any(), any(), any(), any(), any())).thenReturn(DashboardSummaryDto.builder().build());

        // Act
        DashboardSummaryDto result = useCase.execute(merchantId, "WEEK");

        // Assert
        assertThat(result).isNotNull();
        verify(dashboardMapper).toDTO(any(), any(), eq(BigDecimal.ZERO), eq(BigDecimal.ZERO), any(), any(), any());
    }
}
