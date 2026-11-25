package orionpay.merchant.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import orionpay.merchant.application.ports.output.AcquiringSwitchPort;
import orionpay.merchant.application.ports.output.EventPublisherPort;
import orionpay.merchant.domain.excepion.DomainException;
import orionpay.merchant.domain.model.*;
import orionpay.merchant.domain.model.enums.ProductType;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.TransactionRequest;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.TransactionResponse;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.MerchantRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.PricingRepository;
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
class AuthorizeTransactionUseCaseTest {

    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AcquiringSwitchPort acquiringSwitch;
    @Mock
    private PricingRepository pricingRepository;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private EventPublisherPort eventPublisher;

    @InjectMocks
    private AuthorizeTransactionUseCase useCase;

    private UUID merchantId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID().toString();
    }

    @Test
    void shouldAuthorizeTransaction_WhenAllValidationsPass() {
        // Arrange
        TransactionRequest request = new TransactionRequest(
                merchantId, 
                BigDecimal.valueOf(100.00), 
                ProductType.CREDIT_A_VISTA, 
                1, 
                "TERM1",
                "REF123", // externalReference
                "EMV",    // entryMode
                "VISA",   // cardBrand
                "JOHN DOE",// cardHolderName
                "1234567812345678", // cardNumber
                "12/29", // expirationDate
                "123",   // cvv
                "123456", // applicationCryptogram
                "1234",  // atc
                "0123456789ABCDEF" // pinBlock (16 hex)
        );

        Merchant merchant = Merchant.create(merchantId, "Empresa Teste", "12345678000199", "teste@empresa.com");
        
        MerchantPricing pricing = new MerchantPricing();
        pricing.setMdrPercentage(BigDecimal.valueOf(2.5));

        Authorization.Result authResult = Authorization.Result.builder()
                .responseCode("00")
                .nsuAcquirer("123")
                .nsuBrand("456")
                .build();

        when(idempotencyService.checkAndLock(idempotencyKey)).thenReturn(null);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(pricingRepository.findCurrentPricing(merchantId, ProductType.CREDIT_A_VISTA)).thenReturn(Optional.of(pricing));
        when(acquiringSwitch.authorizeTransaction(any(Authorization.Request.class))).thenReturn(Optional.of(authResult));

        // Act
        TransactionResponse response = useCase.execute(request, idempotencyKey);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.status().toString()).isEqualTo("APPROVED");

        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(eventPublisher, times(1)).publish(any(TransactionEvent.class));
        verify(idempotencyService, times(1)).saveSuccess(eq(idempotencyKey), any(TransactionResponse.class));
    }

    @Test
    void shouldThrowDomainException_WhenMerchantNotFound() {
        // Arrange
        TransactionRequest request = new TransactionRequest(
                merchantId, 
                BigDecimal.valueOf(100.00), 
                ProductType.CREDIT_A_VISTA, 
                1, 
                "TERM1",
                "REF123", 
                "EMV", 
                "VISA", 
                "JOHN DOE", 
                "1234567812345678", 
                "12/29", 
                "123", 
                "123456", 
                "1234", 
                "0123456789ABCDEF"
        );

        when(idempotencyService.checkAndLock(idempotencyKey)).thenReturn(null);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        // Act & Assert
        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(request, idempotencyKey));
        assertThat(exception.getMessage()).isEqualTo("Lojista não encontrado.");

        verify(acquiringSwitch, never()).authorizeTransaction(any());
    }
}
