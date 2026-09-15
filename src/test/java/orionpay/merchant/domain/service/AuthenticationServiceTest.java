package orionpay.merchant.domain.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import orionpay.merchant.config.AuthenticatedUser;
import orionpay.merchant.config.JpaUserDetailsService;
import orionpay.merchant.config.JwtProperties;
import orionpay.merchant.config.JwtService;
import orionpay.merchant.domain.excepion.DomainException;
import orionpay.merchant.domain.model.enums.UserRole;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.LoginRequest;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.RefreshTokenRequest;
import orionpay.merchant.infrastructure.adapters.input.rest.dto.TokenResponse;
import orionpay.merchant.infrastructure.adapters.output.persistence.entity.AuthUserEntity;
import orionpay.merchant.infrastructure.adapters.output.persistence.entity.MerchantEntity;
import orionpay.merchant.infrastructure.adapters.output.persistence.entity.RefreshTokenEntity;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaAuthUserRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaMerchantRepository;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaRefreshTokenRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JpaUserDetailsService userDetailsService;
    @Mock private JwtService jwtService;
    @Mock private JwtProperties jwtProperties;
    @Mock private JpaAuthUserRepository authUserRepository;
    @Mock private JpaRefreshTokenRepository refreshTokenRepository;
    @Mock private JpaMerchantRepository merchantRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogService auditLogService;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthenticationService service;

    private UUID merchantId;
    private AuthenticatedUser authUser;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        AuthUserEntity userEntity = new AuthUserEntity();
        userEntity.setId(UUID.randomUUID());
        userEntity.setEmail("test@test.com");
        userEntity.setPasswordHash("pass");
        userEntity.setRole(UserRole.ROLE_MERCHANT);
        userEntity.setMerchantId(merchantId);
        userEntity.setEnabled(true);
        authUser = new AuthenticatedUser(userEntity);
    }

    @Test
    void registerMerchantUser_ShouldCreateUser_WhenEmailIsNew() {
        when(authUserRepository.existsByEmailIgnoreCase("test@test.com")).thenReturn(false);
        when(passwordEncoder.encode("rawPass")).thenReturn("encodedPass");

        service.registerMerchantUser("test@test.com", "rawPass", merchantId);

        verify(authUserRepository).save(any(AuthUserEntity.class));
    }

    @Test
    void registerMerchantUser_ShouldThrowDomainException_WhenEmailExists() {
        when(authUserRepository.existsByEmailIgnoreCase("test@test.com")).thenReturn(true);

        assertThrows(DomainException.class, () -> service.registerMerchantUser("test@test.com", "rawPass", merchantId));
    }

    @Test
    void login_ShouldReturnTokenResponse_WhenCredentialsAreValid() {
        LoginRequest request = new LoginRequest("test@test.com", "pass");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        
        when(userDetailsService.loadUserByUsername("test@test.com")).thenReturn(authUser);
        when(jwtService.generateAccessToken(authUser)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(authUser)).thenReturn("refresh-token");
        when(jwtProperties.accessTokenExpirationMinutes()).thenReturn(60L);
        when(jwtProperties.refreshTokenExpirationDays()).thenReturn(7L);
        when(refreshTokenRepository.findByUserIdAndRevokedFalse(authUser.getUserId())).thenReturn(List.of());

        TokenResponse response = service.login(request, httpRequest);

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(auditLogService).logLoginAttempt(eq("test@test.com"), eq("LOGIN_SUCCESS"), anyString(), eq("127.0.0.1"));
    }

    @Test
    void login_ShouldThrowBadCredentialsAndAudit_WhenFails() {
        LoginRequest request = new LoginRequest("test@test.com", "pass");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad"));

        assertThrows(BadCredentialsException.class, () -> service.login(request, httpRequest));
        verify(auditLogService).logLoginAttempt(eq("test@test.com"), eq("LOGIN_FAILURE"), anyString(), eq("127.0.0.1"));
    }
}
