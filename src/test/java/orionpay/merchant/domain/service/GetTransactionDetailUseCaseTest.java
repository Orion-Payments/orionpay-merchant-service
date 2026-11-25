package orionpay.merchant.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import orionpay.merchant.domain.excepion.TransactionNotFoundException;
import orionpay.merchant.domain.model.ExtratoTransactionDetail;
import orionpay.merchant.domain.model.Transaction;
import orionpay.merchant.infrastructure.adapters.output.persistence.mapper.TransactionMapper;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.TransactionRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTransactionDetailUseCaseTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private GetTransactionDetailUseCase useCase;

    private UUID merchantId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
    }

    @Test
    void execute_ShouldReturnDetail_WhenFound() {
        Transaction transaction = new Transaction();
        transaction.setId(transactionId);
        
        ExtratoTransactionDetail detail = mock(ExtratoTransactionDetail.class);

        when(transactionRepository.findByIdAndMerchantId(transactionId, merchantId)).thenReturn(Optional.of(transaction));
        when(transactionMapper.toDetailDomain(transaction)).thenReturn(detail);

        ExtratoTransactionDetail result = useCase.execute(transactionId, merchantId);

        assertThat(result).isNotNull();
    }

    @Test
    void execute_ShouldThrowException_WhenNotFound() {
        when(transactionRepository.findByIdAndMerchantId(transactionId, merchantId)).thenReturn(Optional.empty());

        assertThrows(TransactionNotFoundException.class, () -> useCase.execute(transactionId, merchantId));
    }
}
