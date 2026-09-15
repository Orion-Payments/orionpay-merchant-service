package orionpay.merchant.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import orionpay.merchant.domain.excepion.DomainException;
import orionpay.merchant.domain.model.LedgerAccount;
import orionpay.merchant.domain.model.TransactionEvent;
import orionpay.merchant.domain.model.enums.EntryType;
import orionpay.merchant.domain.model.enums.ProductType;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.LedgerRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerIntegrationServiceTest {

    @Mock
    private LedgerRepository ledgerRepository;

    @InjectMocks
    private LedgerIntegrationService service;

    private TransactionEvent event;

    @BeforeEach
    void setUp() {
        event = TransactionEvent.builder()
                .id(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .terminalId("TERM")
                .amount(new BigDecimal("100.00"))
                .productType(ProductType.CREDIT_A_VISTA)
                .occurredAt(LocalDateTime.now())
                .status(orionpay.merchant.domain.model.enums.TransactionStatus.APPROVED)
                .build();
    }

    @Test
    void processAccounting_ShouldCreateAccountAndEntry_WhenAccountNotExists() {
        when(ledgerRepository.findByMerchantId(event.merchantId())).thenReturn(Optional.empty());

        service.processAccounting(event, new BigDecimal("95.00"), LocalDateTime.now(), 1);

        verify(ledgerRepository, times(2)).saveAccount(any(LedgerAccount.class));
        verify(ledgerRepository).saveEntry(any(), eq(new BigDecimal("95.00")), eq(EntryType.CREDIT), anyString(), eq(event.transactionId()), any());
    }

    @Test
    void processAccounting_ShouldApplyEntry_WhenAccountExists() {
        LedgerAccount account = LedgerAccount.create(UUID.randomUUID(), event.merchantId(), "123", BigDecimal.ZERO);
        when(ledgerRepository.findByMerchantId(event.merchantId())).thenReturn(Optional.of(account));

        service.processAccounting(event, new BigDecimal("95.00"), LocalDateTime.now(), 1);

        verify(ledgerRepository, times(1)).saveAccount(account);
        verify(ledgerRepository).saveEntry(eq(account), eq(new BigDecimal("95.00")), eq(EntryType.CREDIT), anyString(), eq(event.transactionId()), any());
    }

    @Test
    void fallbackLedger_ShouldThrowDomainException() {
        DomainException ex = assertThrows(DomainException.class, () -> 
            service.fallbackLedger(event, new BigDecimal("95.00"), LocalDateTime.now(), 1, new RuntimeException("Timeout"))
        );
        assertThat(ex.getMessage()).isEqualTo("Circuit Breaker ou Bulkhead ativo para Ledger.");
    }
}
