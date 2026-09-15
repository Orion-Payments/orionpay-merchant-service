package orionpay.merchant.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import orionpay.merchant.application.ports.input.rest.dto.MerchantResponse;
import orionpay.merchant.domain.event.MerchantCreatedEvent;
import orionpay.merchant.domain.excepion.DomainException;
import orionpay.merchant.domain.model.enums.AccountType;
import orionpay.merchant.domain.model.Merchant;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.MerchantRegistrationRequest;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.MerchantRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterMerchantUseCaseTest {

    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private RegisterMerchantUseCase useCase;

    private MerchantRegistrationRequest request;

    @BeforeEach
    void setUp() {
        request = new MerchantRegistrationRequest(
                "Empresa Falsa",
                "12345678901",
                "teste@falso.com",
                "SenhaSegura123!",
                "01000000",
                "Rua A",
                "123",
                "Complemento",
                "Bairro",
                "Cidade",
                "SP",
                "341",
                "0001",
                "12345",
                "6",
                AccountType.CHECKING
        );
    }

    @Test
    void execute_ShouldCreateMerchant_WhenValid() {
        when(merchantRepository.findByDocument(request.document())).thenReturn(Optional.empty());
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        doNothing().when(authenticationService).registerMerchantUser(anyString(), anyString(), any());
        doNothing().when(eventPublisher).publishEvent(any(MerchantCreatedEvent.class));

        MerchantResponse response = useCase.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Empresa Falsa");
        
        verify(merchantRepository, times(1)).save(any(Merchant.class));
        verify(authenticationService, times(1)).registerMerchantUser(eq("teste@falso.com"), eq("SenhaSegura123!"), any());
        verify(eventPublisher, times(1)).publishEvent(any(MerchantCreatedEvent.class));
    }

    @Test
    void execute_ShouldThrowDomainException_WhenDocumentAlreadyExists() {
        Merchant existingMerchant = Merchant.create(null, "Existente", request.document(), "e@m.com");
        when(merchantRepository.findByDocument(request.document())).thenReturn(Optional.of(existingMerchant));

        DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(request));
        assertThat(ex.getMessage()).isEqualTo("Lojista já cadastrado.");

        verify(merchantRepository, never()).save(any(Merchant.class));
        verify(authenticationService, never()).registerMerchantUser(anyString(), anyString(), any());
    }
}
