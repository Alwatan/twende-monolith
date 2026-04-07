package tz.co.twende.driver.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.driver.dto.request.RegisterVehicleRequest;
import tz.co.twende.driver.dto.response.ActiveVehicleDto;
import tz.co.twende.driver.dto.response.DriverVehicleDto;
import tz.co.twende.driver.entity.DriverVehicle;
import tz.co.twende.driver.mapper.DriverMapper;
import tz.co.twende.driver.repository.DriverProfileRepository;
import tz.co.twende.driver.repository.DriverVehicleRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleService {

    private final DriverVehicleRepository vehicleRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DriverMapper driverMapper;

    @Transactional
    public DriverVehicleDto registerVehicle(
            UUID driverId, String countryCode, RegisterVehicleRequest request) {
        if (!driverProfileRepository.existsById(driverId)) {
            throw new ResourceNotFoundException("Driver not found with id: " + driverId);
        }

        if (vehicleRepository.existsByDriverIdAndPlateNumber(driverId, request.getPlateNumber())) {
            throw new ConflictException(
                    "Vehicle with plate number "
                            + request.getPlateNumber()
                            + " is already registered");
        }

        DriverVehicle vehicle = new DriverVehicle();
        vehicle.setDriverId(driverId);
        vehicle.setCountryCode(countryCode);
        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setMake(request.getMake());
        vehicle.setModel(request.getModel());
        vehicle.setYear(request.getYear());
        vehicle.setPlateNumber(request.getPlateNumber());
        vehicle.setColor(request.getColor());
        vehicle.setActive(true);

        DriverVehicle saved = vehicleRepository.save(vehicle);
        log.info("Registered vehicle {} for driver {}", saved.getPlateNumber(), driverId);
        return driverMapper.toVehicleDto(saved);
    }

    public List<DriverVehicleDto> listVehicles(UUID driverId) {
        if (!driverProfileRepository.existsById(driverId)) {
            throw new ResourceNotFoundException("Driver not found with id: " + driverId);
        }
        return driverMapper.toVehicleDtoList(vehicleRepository.findByDriverId(driverId));
    }

    public ActiveVehicleDto getActiveVehicle(UUID driverId) {
        DriverVehicle vehicle =
                vehicleRepository
                        .findByDriverIdAndIsActiveTrue(driverId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "No active vehicle found for"
                                                        + " driver: "
                                                        + driverId));
        return driverMapper.toActiveVehicleDto(vehicle);
    }

    @Transactional
    public DriverVehicleDto setActiveVehicle(UUID driverId, UUID vehicleId) {
        DriverVehicle vehicle =
                vehicleRepository
                        .findById(vehicleId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Vehicle not found: " + vehicleId));

        if (!vehicle.getDriverId().equals(driverId)) {
            throw new BadRequestException("Vehicle does not belong to this driver");
        }

        // Deactivate all other vehicles
        vehicleRepository.findByDriverId(driverId).stream()
                .filter(v -> !v.getId().equals(vehicleId))
                .forEach(
                        v -> {
                            v.setActive(false);
                            vehicleRepository.save(v);
                        });

        vehicle.setActive(true);
        DriverVehicle saved = vehicleRepository.save(vehicle);
        return driverMapper.toVehicleDto(saved);
    }
}
