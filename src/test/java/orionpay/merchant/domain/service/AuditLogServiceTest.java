package orionpay.merchant.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import orionpay.merchant.domain.model.AuditLog;
import orionpay.merchant.infrastructure.adapters.output.persistence.entity.AuditLogEntity;
import orionpay.merchant.infrastructure.adapters.output.persistence.mapper.AuditLogMapper;
import orionpay.merchant.infrastructure.adapters.output.persistence.reposittory.JpaAuditLogRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private JpaAuditLogRepository auditLogRepository;
    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogService service;

    @Test
    void logLoginAttempt_ShouldSaveLog() {
        when(auditLogMapper.toEntity(any(AuditLog.class))).thenReturn(new AuditLogEntity());
        
        service.logLoginAttempt("test@test.com", "LOGIN_SUCCESS", "Ok", "127.0.0.1");

        verify(auditLogRepository).save(any(AuditLogEntity.class));
    }
}
