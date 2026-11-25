package orionpay.merchant.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import orionpay.merchant.application.ports.output.AcquiringSwitchPort;
import orionpay.merchant.domain.excepion.DomainException;
import orionpay.merchant.domain.model.LedgerAccount;
import orionpay.merchant.domain.model.Merchant;
import orionpay.merchant.domain.model.Transaction;
import orionpay.merchant.domain.model.enums.EntryType;
import orionpay.merchant.domain.model.enums.TransactionStatus;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.RefundRequest;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.LedgerRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundTransactionUseCaseTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private LedgerRepository ledgerRepository;
    @Mock
    private AcquiringSwitchPort acquiringSwitch;

    @InjectMocks
    private RefundTransactionUseCase useCase;

    private UUID merchantId;
    private UUID transactionId;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        Merchant merchant = Merchant.create(merchantId, "Empresa", "12345678901", "teste@empresa.com");
        
        transaction = new Transaction();
        transaction.setId(transactionId);
        transaction.setMerchant(merchant);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setStatus(TransactionStatus.APPROVED);
        transaction.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void execute_ShouldThrowUnsupportedOperation_WhenValid() {
        RefundRequest request = new RefundRequest(transactionId, "Motivo do cliente");

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(ledgerRepository.findRealAvailableBalance(merchantId)).thenReturn(new BigDecimal("200.00"));

        assertThrows(UnsupportedOperationException.class, () -> useCase.execute(merchantId, request));
    }

    @Test
    void execute_ShouldThrowDomainException_WhenTransactionNotFound() {
        RefundRequest request = new RefundRequest(transactionId, "Motivo do cliente");
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(merchantId, request));
        assertThat(ex.getMessage()).isEqualTo("Transação original não encontrada.");
    }

    @Test
    void execute_ShouldThrowDomainException_WhenNotApproved() {
        transaction.setStatus(TransactionStatus.DECLINED);
        RefundRequest request = new RefundRequest(transactionId, "Motivo do cliente");
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(merchantId, request));
        assertThat(ex.getMessage()).contains("Apenas transações APROVADAS podem ser estornadas.");
    }

    @Test
    void execute_ShouldThrowDomainException_WhenInsufficientBalance() {
        RefundRequest request = new RefundRequest(transactionId, "Motivo do cliente");
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(ledgerRepository.findRealAvailableBalance(merchantId)).thenReturn(new BigDecimal("50.00")); // Less than 100

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(merchantId, request));
        assertThat(ex.getMessage()).isEqualTo("Saldo insuficiente para realizar o estorno.");
    }

    @Test
    void execute_ShouldThrowDomainException_WhenWrongMerchant() {
        RefundRequest request = new RefundRequest(transactionId, "Motivo do cliente");
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(UUID.randomUUID(), request));
        assertThat(ex.getMessage()).isEqualTo("Acesso negado: Transação não pertence a este lojista.");
    }

    @Test
    void reserveRefundBalance_ShouldReserveAndSave() {
        LedgerAccount account = LedgerAccount.create(UUID.randomUUID(), merchantId, "123", new BigDecimal("500.00"));

        when(ledgerRepository.findByMerchantId(merchantId)).thenReturn(Optional.of(account));

        useCase.reserveRefundBalance(transaction);

        verify(ledgerRepository).saveAccount(account);
        verify(ledgerRepository).saveEntry(eq(account), eq(new BigDecimal("100.00")), eq(EntryType.REFUND_HOLD), anyString(), eq(transactionId), any());
    }

    @Test
    void confirmRefund_ShouldUpdateStatusAndSave() {
        LedgerAccount account = LedgerAccount.create(UUID.randomUUID(), merchantId, "123", BigDecimal.ZERO);

        when(ledgerRepository.findByMerchantId(merchantId)).thenReturn(Optional.of(account));

        useCase.confirmRefund(transaction, "Motivo");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        verify(transactionRepository).save(transaction);
        verify(ledgerRepository).saveEntry(eq(account), eq(new BigDecimal("100.00")), eq(EntryType.REFUND_DEBIT), anyString(), eq(transactionId), any());
    }
}
