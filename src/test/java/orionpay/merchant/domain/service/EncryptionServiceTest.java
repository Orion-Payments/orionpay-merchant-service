package orionpay.merchant.domain.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import orionpay.merchant.domain.excepion.EncryptionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptionServiceTest {

    @Test
    void decrypt_ShouldThrowEncryptionException_WhenKeyNotConfigured() {
        EncryptionService service = new EncryptionService();
        ReflectionTestUtils.setField(service, "privateKeyBase64", "");

        assertThrows(EncryptionException.class, () -> service.decrypt("U29tZVRleHQ="));
    }

    @Test
    void decrypt_ShouldThrowEncryptionException_WhenKeyIsInvalidBase64() {
        EncryptionService service = new EncryptionService();
        ReflectionTestUtils.setField(service, "privateKeyBase64", "not_a_valid_base64!!!");

        assertThrows(EncryptionException.class, () -> service.decrypt("U29tZVRleHQ="));
    }

    @Test
    void decrypt_ShouldThrowEncryptionException_WhenKeyIsInvalidFormat() {
        EncryptionService service = new EncryptionService();
        ReflectionTestUtils.setField(service, "privateKeyBase64", "dGVzdGU="); // Base64 válido, mas chave PKCS8 inválida

        assertThrows(EncryptionException.class, () -> service.decrypt("U29tZVRleHQ="));
    }
}
