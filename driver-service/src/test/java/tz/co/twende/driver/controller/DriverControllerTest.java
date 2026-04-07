package tz.co.twende.driver.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.enums.DriverStatus;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.driver.dto.request.UpdateDriverRequest;
import tz.co.twende.driver.dto.request.UpdateStatusRequest;
import tz.co.twende.driver.dto.response.DriverProfileDto;
import tz.co.twende.driver.dto.response.DriverSummaryDto;
import tz.co.twende.driver.service.DocumentService;
import tz.co.twende.driver.service.DriverService;
import tz.co.twende.driver.service.VehicleService;

@ExtendWith(MockitoExtension.class)
class DriverControllerTest {

    @Mock private DriverService driverService;
    @Mock private DocumentService documentService;
    @Mock private VehicleService vehicleService;

    @InjectMocks private DriverController controller;

    @Test
    void givenDriverId_whenGetProfile_thenReturnsProfile() {
        UUID userId = UUID.randomUUID();
        DriverProfileDto dto = buildProfileDto(userId);
        when(driverService.getProfile(userId)).thenReturn(dto);

        ResponseEntity<ApiResponse<DriverProfileDto>> response =
                controller.getProfile(userId, "DRIVER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getId()).isEqualTo(userId);
        assertThat(response.getBody().getData().getFullName()).isEqualTo("Test Driver");
        verify(driverService).getProfile(userId);
    }

    @Test
    void givenNonDriverRole_whenGetProfile_thenThrowsUnauthorized() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.getProfile(userId, "RIDER"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Driver role required");
    }

    @Test
    void givenUpdateRequest_whenUpdateProfile_thenReturnsUpdated() {
        UUID userId = UUID.randomUUID();
        UpdateDriverRequest request =
                UpdateDriverRequest.builder().fullName("Updated Name").build();
        DriverProfileDto dto = buildProfileDto(userId);
        dto.setFullName("Updated Name");
        when(driverService.updateProfile(eq(userId), any(UpdateDriverRequest.class)))
                .thenReturn(dto);

        ResponseEntity<ApiResponse<DriverProfileDto>> response =
                controller.updateProfile(userId, "DRIVER", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getFullName()).isEqualTo("Updated Name");
        verify(driverService).updateProfile(userId, request);
    }

    @Test
    void givenGoOnline_whenUpdateStatus_thenReturnsOk() {
        UUID userId = UUID.randomUUID();
        UpdateStatusRequest request =
                UpdateStatusRequest.builder().status(DriverStatus.ONLINE_AVAILABLE).build();
        DriverProfileDto dto = buildProfileDto(userId);
        dto.setStatus(DriverStatus.ONLINE_AVAILABLE);
        when(driverService.updateStatus(userId, DriverStatus.ONLINE_AVAILABLE)).thenReturn(dto);

        ResponseEntity<ApiResponse<DriverProfileDto>> response =
                controller.updateStatus(userId, "DRIVER", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getStatus())
                .isEqualTo(DriverStatus.ONLINE_AVAILABLE);
        verify(driverService).updateStatus(userId, DriverStatus.ONLINE_AVAILABLE);
    }

    @Test
    void givenDriverRole_whenGetSummary_thenReturnsSummary() {
        UUID userId = UUID.randomUUID();
        DriverSummaryDto summary =
                DriverSummaryDto.builder()
                        .id(userId)
                        .fullName("Test Driver")
                        .status(DriverStatus.OFFLINE)
                        .tripCount(10)
                        .build();
        when(driverService.getSummary(userId)).thenReturn(summary);

        ResponseEntity<ApiResponse<DriverSummaryDto>> response =
                controller.getSummary(userId, "DRIVER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getTripCount()).isEqualTo(10);
    }

    private DriverProfileDto buildProfileDto(UUID id) {
        return DriverProfileDto.builder()
                .id(id)
                .countryCode("TZ")
                .fullName("Test Driver")
                .status(DriverStatus.OFFLINE)
                .tripCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
