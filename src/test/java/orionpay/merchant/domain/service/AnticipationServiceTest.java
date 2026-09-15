package orionpay.merchant.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import orionpay.merchant.domain.excepion.DomainException;
import orionpay.merchant.domain.model.LedgerAccount;
import orionpay.merchant.domain.model.enums.SettlementStatus;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.AnticipationRequest;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.AnticipationResponse;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnticipationServiceTest {

    @Mock
    private JpaSettlementEntryRepository settlementRepository;
    @Mock
    private JpaPricingRepository pricingRepository;
    @Mock
    private LedgerRepository ledgerRepository;

    @InjectMocks
    private AnticipationService service;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
    }

    @Test
    void getAnticipationFee_ShouldReturnFee_WhenPricingExists() {
        PricingEntity pricing = new PricingEntity();
        pricing.setAnticipationFee(new BigDecimal("3.00"));
        when(pricingRepository.findByMerchantId(merchantId)).thenReturn(List.of(pricing));

        BigDecimal fee = service.getAnticipationFee(merchantId);

        assertThat(fee).isEqualTo(new BigDecimal("3.00"));
    }

    @Test
    void getAnticipationFee_ShouldThrowDomainException_WhenPricingNotExists() {
        when(pricingRepository.findByMerchantId(merchantId)).thenReturn(List.of());

        assertThrows(DomainException.class, () -> service.getAnticipationFee(merchantId));
    }

    @Test
    void getAvailableSettlements_ShouldCalculateCorrectly() {
        PricingEntity pricing = new PricingEntity();
        pricing.setAnticipationFee(new BigDecimal("3.00")); // 3% a.m
        
        SettlementEntryEntity entry = new SettlementEntryEntity();
        entry.setId(UUID.randomUUID());
        entry.setExpectedSettlementDate(LocalDateTime.now().plusDays(30)); // 30 days
        entry.setAmount(new BigDecimal("100.00"));
        entry.setNetAmount(new BigDecimal("100.00"));

        when(pricingRepository.findByMerchantId(merchantId)).thenReturn(List.of(pricing));
        when(settlementRepository.findAvailableForAnticipation(merchantId)).thenReturn(List.of(entry));

        AnticipationResponse response = service.getAvailableSettlements(merchantId);

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        
        // 3% am = 0.1% ao dia. 30 dias = 3%. Cost = 3.00
        BigDecimal cost = response.getItems().get(0).getAnticipationCost();
        assertThat(cost).isEqualByComparingTo(new BigDecimal("3.00"));
    }

    @Test
    void executeAnticipation_ShouldProcessAndCreditAccount() {
        PricingEntity pricing = new PricingEntity();
        pricing.setAnticipationFee(new BigDecimal("3.00"));
        
        UUID settlementId = UUID.randomUUID();
        SettlementEntryEntity entry = new SettlementEntryEntity();
        entry.setId(settlementId);
        entry.setExpectedSettlementDate(LocalDateTime.now().plusDays(30));
        entry.setNetAmount(new BigDecimal("100.00"));
        entry.setAnticipated(false);

        AnticipationRequest request = new AnticipationRequest(List.of(settlementId));

        LedgerAccount account = LedgerAccount.create(UUID.randomUUID(), merchantId, "123", BigDecimal.ZERO);

        when(pricingRepository.findByMerchantId(merchantId)).thenReturn(List.of(pricing));
        when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(entry));
        when(ledgerRepository.findByMerchantId(merchantId)).thenReturn(Optional.of(account));

        service.executeAnticipation(merchantId, request);

        assertThat(entry.getAnticipated()).isTrue();
        assertThat(entry.getStatus()).isEqualTo(SettlementStatus.ANTICIPATED);
        verify(settlementRepository).save(entry);
        verify(ledgerRepository).saveAccount(account);
        verify(ledgerRepository).saveEntry(eq(account), any(), any(), anyString(), any(), any());
    }
}
