package tz.co.twende.driver.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Collections;
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
import tz.co.twende.driver.dto.request.ApprovalRequest;
import tz.co.twende.driver.dto.response.DriverDetailDto;
import tz.co.twende.driver.dto.response.DriverProfileDto;
import tz.co.twende.driver.service.DocumentService;
import tz.co.twende.driver.service.DriverApprovalService;
import tz.co.twende.driver.service.DriverService;
import tz.co.twende.driver.service.VehicleService;

@ExtendWith(MockitoExtension.class)
class DriverAdminControllerTest {

    @Mock private DriverService driverService;
    @Mock private DriverApprovalService approvalService;
    @Mock private DocumentService documentService;
    @Mock private VehicleService vehicleService;

    @InjectMocks private DriverAdminController controller;

    @Test
    void givenAdminRole_whenGetDriverById_thenReturnsDriver() {
        UUID driverId = UUID.randomUUID();
        DriverProfileDto profile = buildProfileDto(driverId);
        when(driverService.getProfile(driverId)).thenReturn(profile);
        when(vehicleService.listVehicles(driverId)).thenReturn(Collections.emptyList());
        when(documentService.listDocuments(driverId)).thenReturn(Collections.emptyList());

        ResponseEntity<ApiResponse<DriverDetailDto>> response =
                controller.getDriverDetail("ADMIN", driverId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getProfile().getId()).isEqualTo(driverId);
        assertThat(response.getBody().getData().getVehicles()).isEmpty();
        assertThat(response.getBody().getData().getDocuments()).isEmpty();
        verify(driverService).getProfile(driverId);
    }

    @Test
    void givenNonAdminRole_whenGetDriverById_thenThrowsUnauthorized() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.getDriverDetail("DRIVER", driverId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Admin role required");
    }

    @Test
    void givenApprovalRequest_whenApprove_thenReturnsOk() {
        UUID driverId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ApprovalRequest request = ApprovalRequest.builder().approved(true).build();
        DriverProfileDto approved = buildProfileDto(driverId);
        approved.setStatus(DriverStatus.APPROVED);
        approved.setApprovedAt(Instant.now());
        when(approvalService.processApproval(eq(driverId), eq(adminId), any(ApprovalRequest.class)))
                .thenReturn(approved);

        ResponseEntity<ApiResponse<DriverProfileDto>> response =
                controller.processApproval(adminId, "ADMIN", driverId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getStatus()).isEqualTo(DriverStatus.APPROVED);
        verify(approvalService).processApproval(driverId, adminId, request);
    }

    @Test
    void givenNonAdminRole_whenApprove_thenThrowsUnauthorized() {
        UUID driverId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ApprovalRequest request = ApprovalRequest.builder().approved(true).build();

        assertThatThrownBy(() -> controller.processApproval(userId, "RIDER", driverId, request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Admin role required");
    }

    @Test
    void givenAdminRole_whenSuspendDriver_thenReturnsOk() {
        UUID driverId = UUID.randomUUID();
        DriverProfileDto suspended = buildProfileDto(driverId);
        suspended.setStatus(DriverStatus.SUSPENDED);
        when(approvalService.suspendDriver(driverId, "Violation")).thenReturn(suspended);

        ResponseEntity<ApiResponse<DriverProfileDto>> response =
                controller.suspendDriver("ADMIN", driverId, "Violation");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getStatus()).isEqualTo(DriverStatus.SUSPENDED);
        verify(approvalService).suspendDriver(driverId, "Violation");
    }

    private DriverProfileDto buildProfileDto(UUID id) {
        return DriverProfileDto.builder()
                .id(id)
                .countryCode("TZ")
                .fullName("Test Driver")
                .status(DriverStatus.PENDING_APPROVAL)
                .tripCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
