package tz.co.twende.driver.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {

    private final DriverProfileRepository driverProfileRepository;
    private final DriverVehicleRepository vehicleRepository;
    private final DriverStatusLogRepository statusLogRepository;
    private final SubscriptionClient subscriptionClient;
    private final DriverEventPublisher driverEventPublisher;
    private final DriverMapper driverMapper;

    public DriverProfileDto getProfile(UUID driverId) {
        DriverProfile driver = findById(driverId);
        return driverMapper.toProfileDto(driver);
    }

    @Transactional
    public DriverProfileDto updateProfile(UUID driverId, UpdateDriverRequest request) {
        DriverProfile driver = findById(driverId);

        if (request.getFullName() != null) {
            driver.setFullName(request.getFullName());
        }
        if (request.getEmail() != null) {
            driver.setEmail(request.getEmail());
        }
        if (request.getProfilePhotoUrl() != null) {
            driver.setProfilePhotoUrl(request.getProfilePhotoUrl());
        }

        DriverProfile saved = driverProfileRepository.save(driver);
        return driverMapper.toProfileDto(saved);
    }

    public DriverSummaryDto getSummary(UUID driverId) {
        DriverProfile driver = findById(driverId);
        return driverMapper.toSummaryDto(driver);
    }

    @Transactional
    public DriverProfileDto updateStatus(UUID driverId, DriverStatus newStatus) {
        DriverProfile driver = findById(driverId);

        return switch (newStatus) {
            case ONLINE_AVAILABLE -> goOnline(driver);
            case OFFLINE -> goOffline(driver);
            default ->
                    throw new BadRequestException(
                            "Cannot set status to " + newStatus + " via this endpoint");
        };
    }

    private DriverProfileDto goOnline(DriverProfile driver) {
        if (driver.getStatus() != DriverStatus.APPROVED
                && driver.getStatus() != DriverStatus.OFFLINE) {
            throw new BadRequestException("Driver must be approved to go online");
        }

        boolean hasSubscription = subscriptionClient.hasActiveSubscription(driver.getId());
        if (!hasSubscription) {
            throw new BadRequestException("Purchase a bundle to go online");
        }

        if (!vehicleRepository.existsByDriverIdAndIsActiveTrue(driver.getId())) {
            throw new BadRequestException("Register a vehicle first");
        }

        DriverStatus oldStatus = driver.getStatus();
        driver.setStatus(DriverStatus.ONLINE_AVAILABLE);
        driverProfileRepository.save(driver);

        logStatusChange(
                driver.getId(),
                driver.getCountryCode(),
                oldStatus,
                DriverStatus.ONLINE_AVAILABLE,
                null);
        driverEventPublisher.publishStatusUpdated(driver, oldStatus);

        return driverMapper.toProfileDto(driver);
    }

    private DriverProfileDto goOffline(DriverProfile driver) {
        if (driver.getStatus() != DriverStatus.ONLINE_AVAILABLE
                && driver.getStatus() != DriverStatus.APPROVED
                && driver.getStatus() != DriverStatus.OFFLINE) {
            throw new BadRequestException("Cannot go offline from status " + driver.getStatus());
        }

        DriverStatus oldStatus = driver.getStatus();
        driver.setStatus(DriverStatus.OFFLINE);
        driverProfileRepository.save(driver);

        logStatusChange(
                driver.getId(), driver.getCountryCode(), oldStatus, DriverStatus.OFFLINE, null);
        driverEventPublisher.publishStatusUpdated(driver, oldStatus);

        return driverMapper.toProfileDto(driver);
    }

    public Page<DriverProfileDto> listDrivers(
            DriverStatus status, String countryCode, int page, int size) {
        PageRequest pageRequest =
                PageRequest.of(
                        page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<DriverProfile> drivers;
        if (status != null && countryCode != null) {
            drivers =
                    driverProfileRepository.findByStatusAndCountryCode(
                            status, countryCode, pageRequest);
        } else if (status != null) {
            drivers = driverProfileRepository.findByStatus(status, pageRequest);
        } else if (countryCode != null) {
            drivers = driverProfileRepository.findByCountryCode(countryCode, pageRequest);
        } else {
            drivers = driverProfileRepository.findAll(pageRequest);
        }

        return drivers.map(driverMapper::toProfileDto);
    }

    public DriverProfile findById(UUID driverId) {
        return driverProfileRepository
                .findById(driverId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Driver not found with id: " + driverId));
    }

    public void logStatusChange(
            UUID driverId,
            String countryCode,
            DriverStatus fromStatus,
            DriverStatus toStatus,
            String reason) {
        DriverStatusLog logEntry = new DriverStatusLog();
        logEntry.setDriverId(driverId);
        logEntry.setCountryCode(countryCode);
        logEntry.setFromStatus(fromStatus);
        logEntry.setToStatus(toStatus);
        logEntry.setReason(reason);
        logEntry.setChangedAt(Instant.now());
        statusLogRepository.save(logEntry);
    }
}
