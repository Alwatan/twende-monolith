package tz.co.twende.location.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.location.dto.CreateZoneRequest;
import tz.co.twende.location.dto.UpdateZoneRequest;
import tz.co.twende.location.dto.ZoneCheckResponse;
import tz.co.twende.location.dto.ZoneDto;
import tz.co.twende.location.entity.Zone;
import tz.co.twende.location.mapper.ZoneMapper;
import tz.co.twende.location.service.GeofenceService;
import tz.co.twende.location.service.ZoneService;

@ExtendWith(MockitoExtension.class)
class ZoneControllerTest {

    @Mock private ZoneService zoneService;
    @Mock private GeofenceService geofenceService;
    @Mock private ZoneMapper zoneMapper;

    @InjectMocks private ZoneController zoneController;

    @Test
    void givenPointInOperatingZone_whenCheckZones_thenReturnInServiceArea() {
        UUID cityId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");

        Zone operatingZone = new Zone();
        operatingZone.setType("OPERATING");
        operatingZone.setName("Dar es Salaam");

        ZoneDto zoneDto = ZoneDto.builder().type("OPERATING").name("Dar es Salaam").build();

        when(geofenceService.findAllZonesContaining(cityId, lat, lng))
                .thenReturn(List.of(operatingZone));
        when(zoneMapper.toDto(operatingZone)).thenReturn(zoneDto);

        ResponseEntity<ApiResponse<ZoneCheckResponse>> response =
                zoneController.checkZones(lat, lng, cityId);

        assertThat(response.getBody().getData().isInServiceArea()).isTrue();
        assertThat(response.getBody().getData().isRestricted()).isFalse();
        assertThat(response.getBody().getData().getZones()).hasSize(1);
    }

    @Test
    void givenPointInRestrictedZone_whenCheckZones_thenReturnRestricted() {
        UUID cityId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");

        Zone restrictedZone = new Zone();
        restrictedZone.setType("RESTRICTED");
        restrictedZone.setName("Restricted Area");

        ZoneDto zoneDto = ZoneDto.builder().type("RESTRICTED").name("Restricted Area").build();

        when(geofenceService.findAllZonesContaining(cityId, lat, lng))
                .thenReturn(List.of(restrictedZone));
        when(zoneMapper.toDto(restrictedZone)).thenReturn(zoneDto);

        ResponseEntity<ApiResponse<ZoneCheckResponse>> response =
                zoneController.checkZones(lat, lng, cityId);

        assertThat(response.getBody().getData().isRestricted()).isTrue();
    }

    @Test
    void givenCityWithZones_whenListZones_thenReturnZoneList() {
        UUID cityId = UUID.randomUUID();
        Zone zone = new Zone();
        zone.setName("Test Zone");
        ZoneDto zoneDto = ZoneDto.builder().name("Test Zone").build();

        when(zoneService.listZones(cityId)).thenReturn(List.of(zone));
        when(zoneMapper.toDto(zone)).thenReturn(zoneDto);

        ResponseEntity<ApiResponse<List<ZoneDto>>> response = zoneController.listZones(cityId);

        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).getName()).isEqualTo("Test Zone");
    }

    @Test
    void givenAdminRole_whenCreateZone_thenZoneCreated() {
        UUID cityId = UUID.randomUUID();
        CreateZoneRequest request = new CreateZoneRequest();
        request.setName("New Zone");
        request.setBoundary("POLYGON((...))");
        request.setType("OPERATING");

        Zone created = new Zone();
        created.setName("New Zone");
        ZoneDto dto = ZoneDto.builder().name("New Zone").build();

        when(zoneService.createZone(eq(cityId), eq("TZ"), any(CreateZoneRequest.class)))
                .thenReturn(created);
        when(zoneMapper.toDto(created)).thenReturn(dto);

        ResponseEntity<ApiResponse<ZoneDto>> response =
                zoneController.createZone(cityId, "ADMIN", "TZ", request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getData().getName()).isEqualTo("New Zone");
    }

    @Test
    void givenNonAdminRole_whenCreateZone_thenThrowUnauthorized() {
        UUID cityId = UUID.randomUUID();
        CreateZoneRequest request = new CreateZoneRequest();

        assertThatThrownBy(() -> zoneController.createZone(cityId, "DRIVER", "TZ", request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void givenAdminRole_whenUpdateZone_thenZoneUpdated() {
        UUID zoneId = UUID.randomUUID();
        UpdateZoneRequest request = new UpdateZoneRequest();

        Zone updated = new Zone();
        updated.setName("Updated Zone");
        ZoneDto dto = ZoneDto.builder().name("Updated Zone").build();

        when(zoneService.updateZone(zoneId, request)).thenReturn(updated);
        when(zoneMapper.toDto(updated)).thenReturn(dto);

        ResponseEntity<ApiResponse<ZoneDto>> response =
                zoneController.updateZone(zoneId, "ADMIN", request);

        assertThat(response.getBody().getData().getName()).isEqualTo("Updated Zone");
    }

    @Test
    void givenNonAdminRole_whenUpdateZone_thenThrowUnauthorized() {
        UUID zoneId = UUID.randomUUID();
        UpdateZoneRequest request = new UpdateZoneRequest();

        assertThatThrownBy(() -> zoneController.updateZone(zoneId, "RIDER", request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void givenAdminRole_whenDeactivateZone_thenZoneDeactivated() {
        UUID zoneId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> response = zoneController.deactivateZone(zoneId, "ADMIN");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(zoneService).deactivateZone(zoneId);
    }

    @Test
    void givenNonAdminRole_whenDeactivateZone_thenThrowUnauthorized() {
        UUID zoneId = UUID.randomUUID();

        assertThatThrownBy(() -> zoneController.deactivateZone(zoneId, "DRIVER"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
