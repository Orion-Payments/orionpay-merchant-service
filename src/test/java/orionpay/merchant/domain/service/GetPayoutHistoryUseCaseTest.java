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
import orionpay.merchant.infrastructure.adapters.input.rest.dto.PayoutHistoryResponse;
import orionpay.merchant.infrastructure.adapters.output.persistence.entity.PayoutEntity;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaPayoutRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPayoutHistoryUseCaseTest {

    @Mock
    private JpaPayoutRepository payoutRepository;

    @InjectMocks
    private GetPayoutHistoryUseCase useCase;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
    }

    @Test
    void execute_ShouldReturnMaskedPixKeysAndMappedStatus() {
        PayoutEntity p1 = new PayoutEntity();
        p1.setId(UUID.randomUUID());
        p1.setAmount(new BigDecimal("100.00"));
        p1.setPixKey("12345678901"); // 11 chars
        p1.setStatus(PayoutEntity.PayoutStatus.COMPLETED);
        p1.setCreatedAt(LocalDateTime.now());

        PayoutEntity p2 = new PayoutEntity();
        p2.setId(UUID.randomUUID());
        p2.setAmount(new BigDecimal("50.00"));
        p2.setPixKey("123"); // < 5 chars
        p2.setStatus(PayoutEntity.PayoutStatus.FAILED);
        p2.setCreatedAt(LocalDateTime.now());

        Page<PayoutEntity> page = new PageImpl<>(List.of(p1, p2), PageRequest.of(0, 10), 2);
        when(payoutRepository.findByMerchantIdOrderByCreatedAtDesc(eq(merchantId), any(Pageable.class))).thenReturn(page);

        Page<PayoutHistoryResponse> result = useCase.execute(merchantId, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        
        // P1: COMPLETED, masked pixKey
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("CONCLUIDO");
        assertThat(result.getContent().get(0).getPixKey()).isEqualTo("123********01");

        // P2: FAILED, "***"
        assertThat(result.getContent().get(1).getStatus()).isEqualTo("FALHOU");
        assertThat(result.getContent().get(1).getPixKey()).isEqualTo("***");
        assertThat(result.getContent().get(1).getErrorMessage()).isEqualTo("O banco recusou o pagamento.");
    }
}
