package orionpay.merchant.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import orionpay.merchant.domain.model.ExtratoTransaction;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.TransactionExtratoResponse;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.TransactionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTransactionExtratoUseCaseTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private GetTransactionExtratoUseCase useCase;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
    }

    @Test
    void execute_ShouldReturnMappedExtrato() {
        ExtratoTransaction extrato = ExtratoTransaction.builder().build();
        extrato.setId(UUID.randomUUID());
        extrato.setAmount(new BigDecimal("100.00"));
        extrato.setNsu("12345");

        Page<ExtratoTransaction> page = new PageImpl<>(List.of(extrato), PageRequest.of(0, 10), 1);
        when(transactionRepository.findCustomExtrato(eq(merchantId), any(), any(Pageable.class))).thenReturn(page);

        Page<TransactionExtratoResponse> result = useCase.execute(merchantId, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(result.getContent().get(0).getNsu()).isEqualTo("12345");
    }
}
