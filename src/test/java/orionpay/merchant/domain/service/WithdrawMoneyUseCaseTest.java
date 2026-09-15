package orionpay.merchant.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import orionpay.merchant.application.ports.output.PaymentServicePort;
import orionpay.merchant.domain.excepion.DomainException;
import orionpay.merchant.domain.excepion.IdempotencyException;
import orionpay.merchant.domain.excepion.InsufficientFundsException;
import orionpay.merchant.domain.excepion.PayoutPendingException;
import orionpay.merchant.domain.model.IdempotencyResult;
import orionpay.merchant.domain.model.LedgerAccount;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.WithdrawRequest;
import orionpay.merchant.infrastructure.adapters.output.persistence.entity.PayoutEntity;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaPayoutRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.LedgerRepository;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawMoneyUseCaseTest {

    @Mock
    private LedgerRepository ledgerRepository;
    @Mock
    private JpaPayoutRepository payoutRepository;
    @Mock
    private PaymentServicePort paymentService;
    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private WithdrawMoneyUseCase useCase;

    private WithdrawRequest request;
    private UUID merchantId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        idempotencyKey = "key-123";
        request = new WithdrawRequest(merchantId, new BigDecimal("100.00"), "pix-key-123");
    }

    @Test
    void execute_ShouldProcessWithdrawalSuccessfully() {
        // Arrange
        when(payoutRepository.existsByMerchantIdAndStatusIn(merchantId, List.of(PayoutEntity.PayoutStatus.PENDING, PayoutEntity.PayoutStatus.WAITING_BANK_CONFIRMATION)))
                .thenReturn(false);
        when(idempotencyService.checkAndLock(idempotencyKey)).thenReturn(null);
        when(ledgerRepository.deductBalanceAtomic(merchantId, request.amount())).thenReturn(1);
        
        LedgerAccount account = LedgerAccount.create(UUID.randomUUID(), merchantId, "ACC", new BigDecimal("1000.00"));
        when(ledgerRepository.findByMerchantId(merchantId)).thenReturn(Optional.of(account));
        
        PayoutEntity payout = new PayoutEntity();
        payout.setId(UUID.randomUUID());
        payout.setMerchantId(merchantId);
        payout.setAmount(request.amount());
        when(payoutRepository.save(any())).thenReturn(payout);
        
        when(paymentService.processPixPayout("pix-key-123", request.amount())).thenReturn(true);

        // Act
        useCase.execute(request, idempotencyKey);

        // Assert
        verify(paymentService).processPixPayout("pix-key-123", request.amount());
        verify(idempotencyService).saveSuccess(eq(idempotencyKey), anyString());
    }

    @Test
    void execute_ShouldThrowIdempotencyException_WhenAlreadyLockedAndFailed() {
        // Arrange
        when(payoutRepository.existsByMerchantIdAndStatusIn(merchantId, List.of(PayoutEntity.PayoutStatus.PENDING, PayoutEntity.PayoutStatus.WAITING_BANK_CONFIRMATION)))
                .thenReturn(false);
        IdempotencyResult result = new IdempotencyResult(idempotencyKey, "FAILED", "Erro anterior");
        when(idempotencyService.checkAndLock(idempotencyKey)).thenReturn(result);

        // Act & Assert
        IdempotencyException ex = assertThrows(IdempotencyException.class, () -> useCase.execute(request, idempotencyKey));
        assertThat(ex.getMessage()).isEqualTo("Erro anterior");
    }

    @Test
    void execute_ShouldThrowDomainException_WhenActiveWithdrawalExists() {
        when(payoutRepository.existsByMerchantIdAndStatusIn(merchantId, List.of(PayoutEntity.PayoutStatus.PENDING, PayoutEntity.PayoutStatus.WAITING_BANK_CONFIRMATION)))
                .thenReturn(true);

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(request, idempotencyKey));
        assertThat(ex.getMessage()).isEqualTo("Existe um saque em andamento.");
    }

    @Test
    void execute_ShouldThrowInsufficientFunds_WhenAtomicDeductionFails() {
        when(payoutRepository.existsByMerchantIdAndStatusIn(merchantId, List.of(PayoutEntity.PayoutStatus.PENDING, PayoutEntity.PayoutStatus.WAITING_BANK_CONFIRMATION)))
                .thenReturn(false);
        when(idempotencyService.checkAndLock(idempotencyKey)).thenReturn(null);
        when(ledgerRepository.deductBalanceAtomic(merchantId, request.amount())).thenReturn(0);

        assertThrows(InsufficientFundsException.class, () -> useCase.execute(request, idempotencyKey));
        verify(idempotencyService).saveError(eq(idempotencyKey), anyString());
    }

    @Test
    void execute_ShouldFailPayout_WhenPaymentServiceReturnsFalse() {
        when(payoutRepository.existsByMerchantIdAndStatusIn(merchantId, List.of(PayoutEntity.PayoutStatus.PENDING, PayoutEntity.PayoutStatus.WAITING_BANK_CONFIRMATION)))
                .thenReturn(false);
        when(idempotencyService.checkAndLock(idempotencyKey)).thenReturn(null);
        when(ledgerRepository.deductBalanceAtomic(merchantId, request.amount())).thenReturn(1);
        
        LedgerAccount account = LedgerAccount.create(UUID.randomUUID(), merchantId, "ACC", new BigDecimal("1000.00"));
        when(ledgerRepository.findByMerchantId(merchantId)).thenReturn(Optional.of(account));
        
        PayoutEntity payout = new PayoutEntity();
        payout.setId(UUID.randomUUID());
        payout.setMerchantId(merchantId);
        payout.setAmount(request.amount());
        when(payoutRepository.save(any())).thenReturn(payout);
        when(payoutRepository.findById(any())).thenReturn(Optional.of(payout));
        
        when(paymentService.processPixPayout("pix-key-123", request.amount())).thenReturn(false);

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(request, idempotencyKey));
        assertThat(ex.getMessage()).isEqualTo("O banco recusou o pagamento.");
        verify(idempotencyService, atLeastOnce()).saveError(eq(idempotencyKey), eq("O banco recusou o pagamento."));
    }

    @Test
    void execute_ShouldHandleTimeout_AndThrowPayoutPendingException() {
        when(payoutRepository.existsByMerchantIdAndStatusIn(merchantId, List.of(PayoutEntity.PayoutStatus.PENDING, PayoutEntity.PayoutStatus.WAITING_BANK_CONFIRMATION)))
                .thenReturn(false);
        when(idempotencyService.checkAndLock(idempotencyKey)).thenReturn(null);
        when(ledgerRepository.deductBalanceAtomic(merchantId, request.amount())).thenReturn(1);
        
        LedgerAccount account = LedgerAccount.create(UUID.randomUUID(), merchantId, "ACC", new BigDecimal("1000.00"));
        when(ledgerRepository.findByMerchantId(merchantId)).thenReturn(Optional.of(account));
        
        PayoutEntity payout = new PayoutEntity();
        payout.setId(UUID.randomUUID());
        payout.setMerchantId(merchantId);
        payout.setAmount(request.amount());
        when(payoutRepository.save(any())).thenReturn(payout);
        
        when(paymentService.processPixPayout("pix-key-123", request.amount())).thenThrow(new RuntimeException(new SocketTimeoutException("Read timed out")));

        assertThrows(PayoutPendingException.class, () -> useCase.execute(request, idempotencyKey));
        assertThat(payout.getStatus()).isEqualTo(PayoutEntity.PayoutStatus.WAITING_BANK_CONFIRMATION);
    }
}
