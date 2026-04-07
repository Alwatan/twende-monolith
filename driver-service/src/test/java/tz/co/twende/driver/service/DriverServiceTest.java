package tz.co.twende.driver.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.DriverStatus;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.driver.client.SubscriptionClient;
import tz.co.twende.driver.dto.request.UpdateDriverRequest;
import tz.co.twende.driver.dto.response.DriverProfileDto;
import tz.co.twende.driver.dto.response.DriverSummaryDto;
import tz.co.twende.driver.entity.DriverProfile;
import tz.co.twende.driver.entity.DriverStatusLog;
import tz.co.twende.driver.kafka.DriverEventPublisher;
import tz.co.twende.driver.mapper.DriverMapper;
import tz.co.twende.driver.repository.DriverProfileRepository;
import tz.co.twende.driver.repository.DriverStatusLogRepository;
import tz.co.twende.driver.repository.DriverVehicleRepository;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock private DriverProfileRepository driverProfileRepository;
    @Mock private DriverVehicleRepository vehicleRepository;
    @Mock private DriverStatusLogRepository statusLogRepository;
    @Mock private SubscriptionClient subscriptionClient;
    @Mock private DriverEventPublisher driverEventPublisher;
    @Mock private DriverMapper driverMapper;

    @InjectMocks private DriverService driverService;

    @Test
    void givenExistingDriver_whenGetProfile_thenReturnDto() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, "John", "TZ");
        DriverProfileDto dto =
                DriverProfileDto.builder()
                        .id(driverId)
                        .fullName("John")
                        .build();

        when(driverProfileRepository.findById(driverId))
                .thenReturn(Optional.of(driver));
        when(driverMapper.toProfileDto(driver)).thenReturn(dto);

        DriverProfileDto result = driverService.getProfile(driverId);
        assertThat(result.getFullName()).isEqualTo("John");
    }

    @Test
    void givenNonExistingDriver_whenGetProfile_thenThrowNotFound() {
        UUID driverId = UUID.randomUUID();
        when(driverProfileRepository.findById(driverId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.getProfile(driverId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void givenValidUpdate_whenUpdateProfile_thenFieldsUpdated() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, "John", "TZ");
        UpdateDriverRequest request =
                UpdateDriverRequest.builder()
                        .fullName("John Updated")
                        .email("john@test.com")
                        .build();
        DriverProfileDto dto =
                DriverProfileDto.builder()
                        .id(driverId)
                        .fullName("John Updated")
                        .build();

        when(driverProfileRepository.findById(driverId))
                .thenReturn(Optional.of(driver));
        when(driverProfileRepository.save(any())).thenReturn(driver);
        when(driverMapper.toProfileDto(any())).thenReturn(dto);

        DriverProfileDto result =
                driverService.updateProfile(driverId, request);
        assertThat(result.getFullName()).isEqualTo("John Updated");

        ArgumentCaptor<DriverProfile> captor =
                ArgumentCaptor.forClass(DriverProfile.class);
        verify(driverProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("John Updated");
        assertThat(captor.getValue().getEmail()).isEqualTo("john@test.com");
    }

    @Test
    void givenApprovedDriverWithSubscriptionAndVehicle_whenGoOnline_thenStatusIsOnlineAvailable() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, "John", "TZ");
        driver.setStatus(DriverStatus.APPROVED);
        DriverProfileDto dto =
                DriverProfileDto.builder()
                        .id(driverId)
                        .status(DriverStatus.ONLINE_AVAILABLE)
                        .build();

        when(driverProfileRepository.findById(driverId))
                .thenReturn(Optional.of(driver));
        when(subscriptionClient.hasActiveSubscription(driverId))
                .thenReturn(true);
        when(vehicleRepository.existsByDriverIdAndIsActiveTrue(driverId))
                .thenReturn(true);
        when(driverProfileRepository.save(any())).thenReturn(driver);
        when(statusLogRepository.save(any()))
                .thenReturn(new DriverStatusLog());
        when(driverMapper.toProfileDto(any())).thenReturn(dto);

        DriverProfileDto result =
                driverService.updateStatus(
                        driverId, DriverStatus.ONLINE_AVAILABLE);
        assertThat(result.getStatus())
                .isEqualTo(DriverStatus.ONLINE_AVAILABLE);
        verify(driverEventPublisher)
                .publishStatusUpdated(any(), eq(DriverStatus.APPROVED));
    }

    @Test
    void givenNoActiveSubscription_whenGoOnline_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, "John", "TZ");
        driver.setStatus(DriverStatus.APPROVED);

        when(driverProfileRepository.findById(driverId))
                .thenReturn(Optional.of(driver));
        when(subscriptionClient.hasActiveSubscription(driverId))
                .thenReturn(false);

        assertThatThrownBy(
                        () ->
                                driverService.updateStatus(
                                        driverId,
                                        DriverStatus.ONLINE_AVAILABLE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bundle");
    }

    @Test
    void givenNoActiveVehicle_whenGoOnline_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, "John", "TZ");
        driver.setStatus(DriverStatus.APPROVED);

        when(driverProfileRepository.findById(driverId))
                .thenReturn(Optional.of(driver));
        when(subscriptionClient.hasActiveSubscription(driverId))
                .thenReturn(true);
        when(vehicleRepository.existsByDriverIdAndIsActiveTrue(driverId))
                .thenReturn(false);

        assertThatThrownBy(
                        () ->
                                driverService.updateStatus(
                                        driverId,
                                        DriverStatus.ONLINE_AVAILABLE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vehicle");
    }

    @Test
    void givenPendingDriver_whenGoOnline_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, "John", "TZ");
        driver.setStatus(DriverStatus.PENDING_APPROVAL);

        when(driverProfileRepository.findById(driverId))
                .thenReturn(Optional.of(driver));

        assertThatThrownBy(
                        () ->
                                driverService.updateStatus(
                                        driverId,
                                        DriverStatus.ONLINE_AVAILABLE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("approved");
    }

    @Test
    void givenOnlineDriver_whenGoOffline_thenStatusIsOffline() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, "John", "TZ");
        driver.setStatus(DriverStatus.ONLINE_AVAILABLE);
        DriverProfileDto dto =
                DriverProfileDto.builder()
                        .id(driverId)
                        .status(DriverStatus.OFFLINE)
                        .build();

        when(driverProfileRepository.findById(driverId))
                .thenReturn(Optional.of(driver));
        when(driverProfileRepository.save(any())).thenReturn(driver);
        when(statusLogRepository.save(any()))
                .thenReturn(new DriverStatusLog());
        when(driverMapper.toProfileDto(any())).thenReturn(dto);

        DriverProfileDto result =
                driverService.updateStatus(driverId, DriverStatus.OFFLINE);
        assertThat(result.getStatus()).isEqualTo(DriverStatus.OFFLINE);
    }

    @Test
    void givenSuspendedDriver_whenGoOffline_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, "John", "TZ");
        driver.setStatus(DriverStatus.SUSPENDED);

        when(driverProfileRepository.findById(driverId))
                .thenReturn(Optional.of(driver));

        assertThatThrownBy(
                        () ->
                                driverService.updateStatus(
                                        driverId, DriverStatus.OFFLINE))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void givenInvalidNewStatus_whenUpdateStatus_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, "John", "TZ");

        when(driverProfileRepository.findById(driverId))
                .thenReturn(Optional.of(driver));

        assertThatThrownBy(
                        () ->
                                driverService.updateStatus(
                                        driverId, DriverStatus.SUSPENDED))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void givenDriver_whenGetSummary_thenReturnSummaryDto() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, "John", "TZ");
        DriverSummaryDto summaryDto =
                DriverSummaryDto.builder()
                        .id(driverId)
                        .fullName("John")
                        .tripCount(5)
                        .build();

        when(driverProfileRepository.findById(driverId))
                .thenReturn(Optional.of(driver));
        when(driverMapper.toSummaryDto(driver)).thenReturn(summaryDto);

        DriverSummaryDto result = driverService.getSummary(driverId);
        assertThat(result.getFullName()).isEqualTo("John");
    }

    @Test
    void givenDriverId_whenLogStatusChange_thenSavesLogEntry() {
        UUID driverId = UUID.randomUUID();
        when(statusLogRepository.save(any()))
                .thenReturn(new DriverStatusLog());

        driverService.logStatusChange(
                driverId,
                "TZ",
                DriverStatus.APPROVED,
                DriverStatus.ONLINE_AVAILABLE,
                "Going online");

        ArgumentCaptor<DriverStatusLog> captor =
                ArgumentCaptor.forClass(DriverStatusLog.class);
        verify(statusLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDriverId()).isEqualTo(driverId);
        assertThat(captor.getValue().getFromStatus())
                .isEqualTo(DriverStatus.APPROVED);
        assertThat(captor.getValue().getToStatus())
                .isEqualTo(DriverStatus.ONLINE_AVAILABLE);
        assertThat(captor.getValue().getReason()).isEqualTo("Going online");
    }

    @Test
    void givenOfflineDriver_whenGoOnline_thenStatusIsOnlineAvailable() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, "John", "TZ");
        driver.setStatus(DriverStatus.OFFLINE);
        DriverProfileDto dto =
                DriverProfileDto.builder()
                        .id(driverId)
                        .status(DriverStatus.ONLINE_AVAILABLE)
                        .build();

        when(driverProfileRepository.findById(driverId))
                .thenReturn(Optional.of(driver));
        when(subscriptionClient.hasActiveSubscription(driverId))
                .thenReturn(true);
        when(vehicleRepository.existsByDriverIdAndIsActiveTrue(driverId))
                .thenReturn(true);
        when(driverProfileRepository.save(any())).thenReturn(driver);
        when(statusLogRepository.save(any()))
                .thenReturn(new DriverStatusLog());
        when(driverMapper.toProfileDto(any())).thenReturn(dto);

        DriverProfileDto result =
                driverService.updateStatus(
                        driverId, DriverStatus.ONLINE_AVAILABLE);
        assertThat(result.getStatus())
                .isEqualTo(DriverStatus.ONLINE_AVAILABLE);
    }

    private DriverProfile createDriver(
            UUID id, String fullName, String countryCode) {
        DriverProfile driver = new DriverProfile();
        driver.setId(id);
        driver.setFullName(fullName);
        driver.setCountryCode(countryCode);
        driver.setStatus(DriverStatus.PENDING_APPROVAL);
        driver.setCreatedAt(Instant.now());
        driver.setUpdatedAt(Instant.now());
        return driver;
    }
}
