package tz.co.twende.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.location.dto.CreateZoneRequest;
import tz.co.twende.location.dto.UpdateZoneRequest;
import tz.co.twende.location.entity.Zone;
import tz.co.twende.location.repository.ZoneRepository;

@ExtendWith(MockitoExtension.class)
class ZoneServiceTest {

    @Mock private ZoneRepository zoneRepository;
    @InjectMocks private ZoneService zoneService;

    @Test
    void givenCityId_whenListZones_thenReturnZones() {
        UUID cityId = UUID.randomUUID();
        Zone zone = new Zone();
        zone.setName("Downtown");
        when(zoneRepository.findByCityId(cityId)).thenReturn(List.of(zone));

        List<Zone> result = zoneService.listZones(cityId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Downtown");
    }

    @Test
    void givenValidRequest_whenCreateZone_thenZoneSaved() {
        UUID cityId = UUID.randomUUID();
        CreateZoneRequest request = new CreateZoneRequest();
        request.setName("Airport Zone");
        request.setBoundary("POLYGON((39.2 -6.8, 39.3 -6.8, 39.3 -6.7, 39.2 -6.7, 39.2 -6.8))");
        request.setType("AIRPORT");

        Zone saved = new Zone();
        saved.setName("Airport Zone");
        saved.setType("AIRPORT");
        when(zoneRepository.save(any(Zone.class))).thenReturn(saved);

        Zone result = zoneService.createZone(cityId, "TZ", request);
        assertThat(result.getName()).isEqualTo("Airport Zone");
        verify(zoneRepository).save(any(Zone.class));
    }

    @Test
    void givenExistingZone_whenUpdateZone_thenFieldsUpdated() {
        UUID zoneId = UUID.randomUUID();
        Zone existing = new Zone();
        existing.setId(zoneId);
        existing.setName("Old Name");
        existing.setType("OPERATING");

        UpdateZoneRequest request = new UpdateZoneRequest();
        request.setName("New Name");

        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(existing));
        when(zoneRepository.save(any(Zone.class))).thenReturn(existing);

        Zone result = zoneService.updateZone(zoneId, request);
        assertThat(result.getName()).isEqualTo("New Name");
    }

    @Test
    void givenNonExistentZone_whenUpdateZone_thenThrowNotFound() {
        UUID zoneId = UUID.randomUUID();
        when(zoneRepository.findById(zoneId)).thenReturn(Optional.empty());

        UpdateZoneRequest request = new UpdateZoneRequest();
        request.setName("Test");

        assertThatThrownBy(() -> zoneService.updateZone(zoneId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void givenExistingZone_whenDeactivateZone_thenActiveSetFalse() {
        UUID zoneId = UUID.randomUUID();
        Zone zone = new Zone();
        zone.setId(zoneId);
        zone.setActive(true);

        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(zone));
        when(zoneRepository.save(any(Zone.class))).thenReturn(zone);

        zoneService.deactivateZone(zoneId);
        assertThat(zone.isActive()).isFalse();
        verify(zoneRepository).save(zone);
    }

    @Test
    void givenNonExistentZone_whenDeactivateZone_thenThrowNotFound() {
        UUID zoneId = UUID.randomUUID();
        when(zoneRepository.findById(zoneId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> zoneService.deactivateZone(zoneId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
