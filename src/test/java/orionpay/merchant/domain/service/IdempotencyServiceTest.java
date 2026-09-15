package orionpay.merchant.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import orionpay.merchant.domain.excepion.DomainException;
import orionpay.merchant.domain.model.IdempotencyResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private IdempotencyService service;

    @Test
    void checkAndLock_ShouldReturnNull_WhenFirstTime() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);

        IdempotencyResult result = service.checkAndLock("key-1");

        assertThat(result).isNull();
    }

    @Test
    void checkAndLock_ShouldThrowException_WhenProcessing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any())).thenReturn(false);
        IdempotencyResult cached = new IdempotencyResult("PROCESSING", null, null);
        when(valueOperations.get(anyString())).thenReturn(cached);

        assertThrows(DomainException.class, () -> service.checkAndLock("key-1"));
    }

    @Test
    void checkAndLock_ShouldReturnResult_WhenSuccess() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any())).thenReturn(false);
        IdempotencyResult cached = new IdempotencyResult("SUCCESS", "Data", null);
        when(valueOperations.get(anyString())).thenReturn(cached);

        IdempotencyResult result = service.checkAndLock("key-1");

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getResponseBody()).isEqualTo("Data");
    }

    @Test
    void saveSuccess_ShouldSaveInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service.saveSuccess("key-1", "Done");
        verify(valueOperations).set(eq("idemp:key-1"), any(IdempotencyResult.class), any(Duration.class));
    }
}
