package tz.co.twende.driver.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.enums.DriverStatus;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.driver.dto.request.ApprovalRequest;
import tz.co.twende.driver.dto.response.DriverProfileDto;
import tz.co.twende.driver.entity.DriverProfile;
import tz.co.twende.driver.kafka.DriverEventPublisher;
import tz.co.twende.driver.mapper.DriverMapper;
import tz.co.twende.driver.repository.DriverProfileRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverApprovalService {

    private final DriverProfileRepository driverProfileRepository;
    private final DriverService driverService;
    private final DriverEventPublisher driverEventPublisher;
    private final DriverMapper driverMapper;

    @Transactional
    public DriverProfileDto processApproval(
            UUID driverId, UUID adminId, ApprovalRequest request) {
        DriverProfile driver = driverService.findById(driverId);

        if (driver.getStatus() != DriverStatus.PENDING_APPROVAL) {
            throw new BadRequestException(
                    "Driver is not in PENDING_APPROVAL status. Current: "
                            + driver.getStatus());
        }

        if (Boolean.TRUE.equals(request.getApproved())) {
            return approveDriver(driver, adminId);
        } else {
            return rejectDriver(driver, request.getRejectionReason());
        }
    }

    private DriverProfileDto approveDriver(DriverProfile driver, UUID adminId) {
        DriverStatus oldStatus = driver.getStatus();
        driver.setStatus(DriverStatus.APPROVED);
        driver.setApprovedAt(Instant.now());
        driver.setApprovedBy(adminId);
        driver.setRejectionReason(null);
        driverProfileRepository.save(driver);

        driverService.logStatusChange(
                driver.getId(),
                driver.getCountryCode(),
                oldStatus,
                DriverStatus.APPROVED,
                "Admin approved");
        driverEventPublisher.publishDriverApproved(driver);

        log.info("Driver {} approved by admin {}", driver.getId(), adminId);
        return driverMapper.toProfileDto(driver);
    }

    private DriverProfileDto rejectDriver(
            DriverProfile driver, String rejectionReason) {
        DriverStatus oldStatus = driver.getStatus();
        driver.setStatus(DriverStatus.REJECTED);
        driver.setRejectionReason(rejectionReason);
        driverProfileRepository.save(driver);

        driverService.logStatusChange(
                driver.getId(),
                driver.getCountryCode(),
                oldStatus,
                DriverStatus.REJECTED,
                rejectionReason);

        log.info("Driver {} rejected: {}", driver.getId(), rejectionReason);
        return driverMapper.toProfileDto(driver);
    }

    @Transactional
    public DriverProfileDto suspendDriver(UUID driverId, String reason) {
        DriverProfile driver = driverService.findById(driverId);
        DriverStatus oldStatus = driver.getStatus();
        driver.setStatus(DriverStatus.SUSPENDED);
        driver.setRejectionReason(reason);
        driverProfileRepository.save(driver);

        driverService.logStatusChange(
                driver.getId(),
                driver.getCountryCode(),
                oldStatus,
                DriverStatus.SUSPENDED,
                reason);
        driverEventPublisher.publishStatusUpdated(driver, oldStatus);

        log.info("Driver {} suspended: {}", driver.getId(), reason);
        return driverMapper.toProfileDto(driver);
    }

    @Transactional
    public DriverProfileDto reinstateDriver(UUID driverId) {
        DriverProfile driver = driverService.findById(driverId);

        if (driver.getStatus() != DriverStatus.SUSPENDED) {
            throw new BadRequestException(
                    "Driver is not suspended. Current: " + driver.getStatus());
        }

        DriverStatus oldStatus = driver.getStatus();
        driver.setStatus(DriverStatus.APPROVED);
        driver.setRejectionReason(null);
        driverProfileRepository.save(driver);

        driverService.logStatusChange(
                driver.getId(),
                driver.getCountryCode(),
                oldStatus,
                DriverStatus.APPROVED,
                "Admin reinstated");
        driverEventPublisher.publishStatusUpdated(driver, oldStatus);

        log.info("Driver {} reinstated", driver.getId());
        return driverMapper.toProfileDto(driver);
    }
}
