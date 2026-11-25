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
import orionpay.merchant.domain.excepion.DomainException;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.FinancialAgendaResponse;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.SettlementDetailResponse;
import orionpay.merchant.infrastructure.adapters.output.persistence.projection.AgendaItemProjection;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaSettlementEntryRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialAgendaServiceTest {

    @Mock
    private JpaSettlementEntryRepository repository;

    @InjectMocks
    private FinancialAgendaService service;

    private UUID merchantId;
    private UUID settlementId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        settlementId = UUID.randomUUID();
    }

    @Test
    void getAgenda_ShouldReturnCorrectResponse() {
        when(repository.sumGrossAmountByPeriod(eq(merchantId), any(), any())).thenReturn(new BigDecimal("1000.00"));
        when(repository.sumCommittedAmountByPeriod(eq(merchantId), any(), any())).thenReturn(new BigDecimal("200.00"));
        when(repository.sumAvailableAmountByPeriod(eq(merchantId), any(), any())).thenReturn(new BigDecimal("800.00"));

        AgendaItemProjection projection = mock(AgendaItemProjection.class);
        when(projection.getStatus()).thenReturn("SCHEDULED");
        when(projection.getBlocked()).thenReturn(false);
        when(projection.getAnticipated()).thenReturn(false);
        
        Page<AgendaItemProjection> page = new PageImpl<>(List.of(projection), PageRequest.of(0, 10), 1);
        when(repository.findAgendaByPeriod(eq(merchantId), any(), any(), eq("ALL"), any())).thenReturn(page);

        FinancialAgendaResponse response = service.getAgenda(merchantId, 2026, 9, 0, 10, "ALL");

        assertThat(response).isNotNull();
        assertThat(response.getSummary().getTotalGrossAmount()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getStatus()).isEqualTo("AGENDADO");
        assertThat(response.getItems().get(0).getTitularidade()).isEqualTo("DISPONÍVEL");
    }

    @Test
    void getSettlementDetail_ShouldReturnDetail_WhenFound() {
        AgendaItemProjection projection = mock(AgendaItemProjection.class);
        when(projection.getIdExt()).thenReturn(settlementId);
        when(projection.getStatus()).thenReturn("ANTICIPATED");
        when(projection.getBlocked()).thenReturn(true);
        
        when(repository.findDetailById(settlementId)).thenReturn(Optional.of(projection));

        SettlementDetailResponse response = service.getSettlementDetail(settlementId);

        assertThat(response).isNotNull();
        assertThat(response.getIdExt()).isEqualTo(settlementId);
        assertThat(response.getStatus()).isEqualTo("ANTECIPADO");
        assertThat(response.getTitularidade()).isEqualTo("VINCULADO A GARANTIA");
    }

    @Test
    void getSettlementDetail_ShouldThrowException_WhenNotFound() {
        when(repository.findDetailById(settlementId)).thenReturn(Optional.empty());

        assertThrows(DomainException.class, () -> service.getSettlementDetail(settlementId));
    }
}
