package tz.co.twende.driver.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.DriverStatus;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.driver.dto.request.ApprovalRequest;
import tz.co.twende.driver.dto.response.DriverProfileDto;
import tz.co.twende.driver.entity.DriverProfile;
import tz.co.twende.driver.kafka.DriverEventPublisher;
import tz.co.twende.driver.mapper.DriverMapper;
import tz.co.twende.driver.repository.DriverProfileRepository;

@ExtendWith(MockitoExtension.class)
class DriverApprovalServiceTest {

    @Mock private DriverProfileRepository driverProfileRepository;
    @Mock private DriverService driverService;
    @Mock private DriverEventPublisher driverEventPublisher;
    @Mock private DriverMapper driverMapper;

    @InjectMocks private DriverApprovalService approvalService;

    @Test
    void givenPendingDriver_whenApproved_thenStatusIsApproved() {
        UUID driverId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId);
        ApprovalRequest request = ApprovalRequest.builder().approved(true).build();
        DriverProfileDto dto =
                DriverProfileDto.builder().id(driverId).status(DriverStatus.APPROVED).build();

        when(driverService.findById(driverId)).thenReturn(driver);
        when(driverProfileRepository.save(any())).thenReturn(driver);
        when(driverMapper.toProfileDto(any())).thenReturn(dto);

        DriverProfileDto result = approvalService.processApproval(driverId, adminId, request);
        assertThat(result.getStatus()).isEqualTo(DriverStatus.APPROVED);
        verify(driverEventPublisher).publishDriverApproved(any());
        verify(driverService)
                .logStatusChange(
                        eq(driverId),
                        any(),
                        eq(DriverStatus.PENDING_APPROVAL),
                        eq(DriverStatus.APPROVED),
                        any());
    }

    @Test
    void givenPendingDriver_whenRejected_thenStatusIsRejected() {
        UUID driverId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId);
        ApprovalRequest request =
                ApprovalRequest.builder()
                        .approved(false)
                        .rejectionReason("Incomplete documents")
                        .build();
        DriverProfileDto dto =
                DriverProfileDto.builder().id(driverId).status(DriverStatus.REJECTED).build();

        when(driverService.findById(driverId)).thenReturn(driver);
        when(driverProfileRepository.save(any())).thenReturn(driver);
        when(driverMapper.toProfileDto(any())).thenReturn(dto);

        DriverProfileDto result = approvalService.processApproval(driverId, adminId, request);
        assertThat(result.getStatus()).isEqualTo(DriverStatus.REJECTED);
    }

    @Test
    void givenAlreadyApprovedDriver_whenApprove_thenThrowBadRequest() {
        UUID driverId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId);
        driver.setStatus(DriverStatus.APPROVED);
        ApprovalRequest request = ApprovalRequest.builder().approved(true).build();

        when(driverService.findById(driverId)).thenReturn(driver);

        assertThatThrownBy(() -> approvalService.processApproval(driverId, adminId, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void givenAnyDriver_whenSuspend_thenStatusIsSuspended() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId);
        driver.setStatus(DriverStatus.APPROVED);
        DriverProfileDto dto =
                DriverProfileDto.builder().id(driverId).status(DriverStatus.SUSPENDED).build();

        when(driverService.findById(driverId)).thenReturn(driver);
        when(driverProfileRepository.save(any())).thenReturn(driver);
        when(driverMapper.toProfileDto(any())).thenReturn(dto);

        DriverProfileDto result = approvalService.suspendDriver(driverId, "Violation");
        assertThat(result.getStatus()).isEqualTo(DriverStatus.SUSPENDED);
    }

    @Test
    void givenSuspendedDriver_whenReinstate_thenStatusIsApproved() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId);
        driver.setStatus(DriverStatus.SUSPENDED);
        DriverProfileDto dto =
                DriverProfileDto.builder().id(driverId).status(DriverStatus.APPROVED).build();

        when(driverService.findById(driverId)).thenReturn(driver);
        when(driverProfileRepository.save(any())).thenReturn(driver);
        when(driverMapper.toProfileDto(any())).thenReturn(dto);

        DriverProfileDto result = approvalService.reinstateDriver(driverId);
        assertThat(result.getStatus()).isEqualTo(DriverStatus.APPROVED);
    }

    @Test
    void givenNotSuspendedDriver_whenReinstate_thenThrowBadRequest() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId);

        when(driverService.findById(driverId)).thenReturn(driver);

        assertThatThrownBy(() -> approvalService.reinstateDriver(driverId))
                .isInstanceOf(BadRequestException.class);
    }

    private DriverProfile createDriver(UUID id) {
        DriverProfile driver = new DriverProfile();
        driver.setId(id);
        driver.setFullName("John Driver");
        driver.setCountryCode("TZ");
        driver.setStatus(DriverStatus.PENDING_APPROVAL);
        driver.setCreatedAt(Instant.now());
        driver.setUpdatedAt(Instant.now());
        return driver;
    }
}
