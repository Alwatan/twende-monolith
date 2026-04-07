package tz.co.twende.driver.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.VehicleType;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.driver.dto.request.RegisterVehicleRequest;
import tz.co.twende.driver.dto.response.ActiveVehicleDto;
import tz.co.twende.driver.dto.response.DriverVehicleDto;
import tz.co.twende.driver.entity.DriverVehicle;
import tz.co.twende.driver.mapper.DriverMapper;
import tz.co.twende.driver.repository.DriverProfileRepository;
import tz.co.twende.driver.repository.DriverVehicleRepository;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock private DriverVehicleRepository vehicleRepository;
    @Mock private DriverProfileRepository driverProfileRepository;
    @Mock private DriverMapper driverMapper;

    @InjectMocks private VehicleService vehicleService;

    @Test
    void givenValidRequest_whenRegisterVehicle_thenVehicleCreated() {
        UUID driverId = UUID.randomUUID();
        RegisterVehicleRequest request =
                RegisterVehicleRequest.builder()
                        .vehicleType(VehicleType.BAJAJ)
                        .plateNumber("T123ABC")
                        .make("Bajaj")
                        .build();
        DriverVehicle saved = createVehicle(driverId, VehicleType.BAJAJ);
        DriverVehicleDto dto =
                DriverVehicleDto.builder()
                        .driverId(driverId)
                        .vehicleType(VehicleType.BAJAJ)
                        .build();

        when(driverProfileRepository.existsById(driverId)).thenReturn(true);
        when(vehicleRepository.existsByDriverIdAndPlateNumber(driverId, "T123ABC"))
                .thenReturn(false);
        when(vehicleRepository.save(any())).thenReturn(saved);
        when(driverMapper.toVehicleDto(any())).thenReturn(dto);

        DriverVehicleDto result = vehicleService.registerVehicle(driverId, "TZ", request);
        assertThat(result.getVehicleType()).isEqualTo(VehicleType.BAJAJ);
    }

    @Test
    void givenDuplicatePlate_whenRegisterVehicle_thenThrowConflict() {
        UUID driverId = UUID.randomUUID();
        RegisterVehicleRequest request =
                RegisterVehicleRequest.builder()
                        .vehicleType(VehicleType.BAJAJ)
                        .plateNumber("T123ABC")
                        .build();

        when(driverProfileRepository.existsById(driverId)).thenReturn(true);
        when(vehicleRepository.existsByDriverIdAndPlateNumber(driverId, "T123ABC"))
                .thenReturn(true);

        assertThatThrownBy(() -> vehicleService.registerVehicle(driverId, "TZ", request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void givenNonExistingDriver_whenRegisterVehicle_thenThrowNotFound() {
        UUID driverId = UUID.randomUUID();
        RegisterVehicleRequest request =
                RegisterVehicleRequest.builder()
                        .vehicleType(VehicleType.BAJAJ)
                        .plateNumber("T123ABC")
                        .build();

        when(driverProfileRepository.existsById(driverId)).thenReturn(false);

        assertThatThrownBy(() -> vehicleService.registerVehicle(driverId, "TZ", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void givenDriverWithVehicles_whenListVehicles_thenReturnAll() {
        UUID driverId = UUID.randomUUID();
        List<DriverVehicle> vehicles = List.of(createVehicle(driverId, VehicleType.BAJAJ));
        List<DriverVehicleDto> dtos =
                List.of(DriverVehicleDto.builder().vehicleType(VehicleType.BAJAJ).build());

        when(driverProfileRepository.existsById(driverId)).thenReturn(true);
        when(vehicleRepository.findByDriverId(driverId)).thenReturn(vehicles);
        when(driverMapper.toVehicleDtoList(vehicles)).thenReturn(dtos);

        List<DriverVehicleDto> result = vehicleService.listVehicles(driverId);
        assertThat(result).hasSize(1);
    }

    @Test
    void givenDriverWithActiveVehicle_whenGetActiveVehicle_thenReturnDto() {
        UUID driverId = UUID.randomUUID();
        DriverVehicle vehicle = createVehicle(driverId, VehicleType.BAJAJ);
        ActiveVehicleDto dto =
                ActiveVehicleDto.builder()
                        .driverId(driverId)
                        .vehicleType(VehicleType.BAJAJ)
                        .build();

        when(vehicleRepository.findByDriverIdAndIsActiveTrue(driverId))
                .thenReturn(Optional.of(vehicle));
        when(driverMapper.toActiveVehicleDto(vehicle)).thenReturn(dto);

        ActiveVehicleDto result = vehicleService.getActiveVehicle(driverId);
        assertThat(result.getVehicleType()).isEqualTo(VehicleType.BAJAJ);
    }

    @Test
    void givenNoActiveVehicle_whenGetActiveVehicle_thenThrowNotFound() {
        UUID driverId = UUID.randomUUID();
        when(vehicleRepository.findByDriverIdAndIsActiveTrue(driverId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.getActiveVehicle(driverId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private DriverVehicle createVehicle(UUID driverId, VehicleType type) {
        DriverVehicle vehicle = new DriverVehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setDriverId(driverId);
        vehicle.setVehicleType(type);
        vehicle.setPlateNumber("T123ABC");
        vehicle.setActive(true);
        vehicle.setCountryCode("TZ");
        vehicle.setCreatedAt(Instant.now());
        vehicle.setUpdatedAt(Instant.now());
        return vehicle;
    }
}
