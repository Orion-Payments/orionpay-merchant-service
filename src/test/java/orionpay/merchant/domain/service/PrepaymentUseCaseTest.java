package orionpay.merchant.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import orionpay.merchant.domain.excepion.DomainException;
import orionpay.merchant.domain.model.LedgerAccount;
import orionpay.merchant.domain.model.enums.EntryType;
import orionpay.merchant.domain.model.enums.SettlementStatus;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.AnticipationRequest;
import orionpay.merchant.infrastructure.adapters.output.persistence.entity.PricingEntity;
import orionpay.merchant.infrastructure.adapters.output.persistence.entity.SettlementEntryEntity;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaPricingRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaSettlementEntryRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.LedgerRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrepaymentUseCaseTest {

    @Mock
    private JpaSettlementEntryRepository settlementRepository;
    @Mock
    private JpaPricingRepository pricingRepository;
    @Mock
    private LedgerRepository ledgerRepository;

    @InjectMocks
    private PrepaymentUseCase useCase;

    private UUID merchantId;
    private UUID settlementId;
    private SettlementEntryEntity entry;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        settlementId = UUID.randomUUID();
        
        entry = new SettlementEntryEntity();
        entry.setId(settlementId);
        entry.setMerchantId(merchantId);
        entry.setNetAmount(new BigDecimal("1000.00"));
        entry.setStatus(SettlementStatus.SCHEDULED);
        entry.setExpectedSettlementDate(LocalDateTime.now().plusDays(30));
        entry.setAnticipated(false);
    }

    @Test
    void execute_ShouldProcessAnticipationAndCreditLedger() {
        AnticipationRequest request = new AnticipationRequest(List.of(settlementId));
        PricingEntity pricing = new PricingEntity();
        pricing.setAnticipationFee(new BigDecimal("2.5")); // 2.5% a.m

        LedgerAccount account = LedgerAccount.create(UUID.randomUUID(), merchantId, "123", BigDecimal.ZERO);

        when(pricingRepository.findByMerchantId(merchantId)).thenReturn(List.of(pricing));
        when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(entry));
        when(ledgerRepository.findByMerchantId(merchantId)).thenReturn(Optional.of(account));

        useCase.execute(merchantId, request);

        assertThat(entry.getAnticipated()).isTrue();
        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.ANTICIPATED);
        
        verify(settlementRepository).save(entry);
        verify(ledgerRepository).saveAccount(account);
        verify(ledgerRepository, times(2)).saveEntry(any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void execute_ShouldThrowDomainException_WhenFeeNotDefined() {
        AnticipationRequest request = new AnticipationRequest(List.of(settlementId));
        when(pricingRepository.findByMerchantId(merchantId)).thenReturn(List.of());

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(merchantId, request));
        assertThat(ex.getMessage()).isEqualTo("Taxa de antecipação não definida.");
    }

    @Test
    void execute_ShouldThrowDomainException_WhenEntryBelongsToOtherMerchant() {
        AnticipationRequest request = new AnticipationRequest(List.of(settlementId));
        PricingEntity pricing = new PricingEntity();
        pricing.setAnticipationFee(new BigDecimal("2.5"));

        entry.setMerchantId(UUID.randomUUID()); // Other merchant

        when(pricingRepository.findByMerchantId(merchantId)).thenReturn(List.of(pricing));
        when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(entry));

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(merchantId, request));
        assertThat(ex.getMessage()).isEqualTo("Acesso negado.");
    }

    @Test
    void execute_ShouldThrowDomainException_WhenAlreadyAnticipated() {
        AnticipationRequest request = new AnticipationRequest(List.of(settlementId));
        PricingEntity pricing = new PricingEntity();
        pricing.setAnticipationFee(new BigDecimal("2.5"));

        entry.setAnticipated(true); // Already anticipated

        when(pricingRepository.findByMerchantId(merchantId)).thenReturn(List.of(pricing));
        when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(entry));

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(merchantId, request));
        assertThat(ex.getMessage()).isEqualTo("Já antecipado.");
    }
}
