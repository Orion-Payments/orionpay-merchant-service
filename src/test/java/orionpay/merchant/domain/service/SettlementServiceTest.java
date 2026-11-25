package orionpay.merchant.domain.service;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import orionpay.merchant.domain.model.TransactionEvent;
import orionpay.merchant.domain.model.enums.ProductType;
import orionpay.merchant.domain.model.MerchantPricing;
import orionpay.merchant.domain.model.LedgerAccount;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaDailySummaryRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaSettlementEntryRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.LedgerRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.PricingRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private JpaSettlementEntryRepository settlementRepository;
    @Mock
    private JpaDailySummaryRepository dailySummaryRepository;
    @Mock
    private PricingRepository pricingRepository;
    @Mock
    private LedgerRepository ledgerRepository;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ObjectProvider<SettlementService> selfProvider;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private Channel channel;

    @InjectMocks
    private SettlementService settlementService;

    private TransactionEvent event;

    @BeforeEach
    void setUp() {
        event = TransactionEvent.builder()
                .id(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .terminalId("TERM01")
                .amount(new BigDecimal("100.00"))
                .productType(ProductType.CREDIT_A_VISTA)
                .installments(1)
                .occurredAt(LocalDateTime.now())
                .status(orionpay.merchant.domain.model.enums.TransactionStatus.APPROVED)
                .build();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.springframework.test.util.ReflectionTestUtils.setField(settlementService, "selfProvider", selfProvider);
        lenient().when(selfProvider.getIfAvailable()).thenReturn(settlementService);
    }

    @Test
    void processTransactionSettlement_ShouldAck_WhenAlreadyProcessingInRedis() throws Exception {
        when(valueOperations.setIfAbsent(anyString(), eq("PROCESSING"), any())).thenReturn(false);

        settlementService.processTransactionSettlement(event, channel, 1L);

        verify(channel).basicAck(1L, false);
        verifyNoInteractions(pricingRepository);
    }

    @Test
    void processTransactionSettlement_ShouldNackAndUnlock_WhenExceptionOccurs() throws Exception {
        when(valueOperations.setIfAbsent(anyString(), eq("PROCESSING"), any())).thenReturn(true);
        when(pricingRepository.findCurrentPricing(event.merchantId(), event.productType())).thenThrow(new RuntimeException("DB down"));

        settlementService.processTransactionSettlement(event, channel, 1L);

        verify(channel).basicNack(1L, false, false);
        verify(redisTemplate).delete("settlement:lock:" + event.transactionId());
    }

    @Test
    void processTransactionSettlement_ShouldAck_WhenIdempotencyViolatedOnDB() throws Exception {
        when(valueOperations.setIfAbsent(anyString(), eq("PROCESSING"), any())).thenReturn(true);
        when(pricingRepository.findCurrentPricing(event.merchantId(), event.productType())).thenThrow(new DataIntegrityViolationException("Dup key"));

        settlementService.processTransactionSettlement(event, channel, 1L);

        verify(channel).basicAck(1L, false);
    }

    @Test
    void processTransactionSettlement_ShouldProcessSuccessfully() throws Exception {
        when(valueOperations.setIfAbsent(anyString(), eq("PROCESSING"), any())).thenReturn(true);
        
        MerchantPricing pricing = new MerchantPricing();
        pricing.setMdrPercentage(new BigDecimal("2.00"));
        when(pricingRepository.findCurrentPricing(event.merchantId(), event.productType())).thenReturn(Optional.of(pricing));
        
        LedgerAccount account = LedgerAccount.create(UUID.randomUUID(), event.merchantId(), "ACC-123", BigDecimal.ZERO);
        when(ledgerRepository.findByMerchantId(event.merchantId())).thenReturn(Optional.of(account));
        
        when(selfProvider.getIfAvailable()).thenReturn(settlementService);
        
        // Mocking ensureLedgerAccountExists and processSingleInstallment is tricky when calling itself via selfProvider.
        // It's sufficient to verify that the daily metrics was called and the channel was acked.

        settlementService.processTransactionSettlement(event, channel, 1L);

        verify(dailySummaryRepository).upsertDailyMetrics(
                eq(event.merchantId()), any(), eq(event.amount()), any(), eq(1L), eq(1L));
        
        verify(valueOperations).set(anyString(), eq("COMPLETED"), any());
        verify(channel).basicAck(1L, false);
    }
}
