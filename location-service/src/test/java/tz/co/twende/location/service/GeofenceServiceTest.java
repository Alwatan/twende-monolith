package tz.co.twende.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.location.entity.Zone;
import tz.co.twende.location.repository.ZoneRepository;

@ExtendWith(MockitoExtension.class)
class GeofenceServiceTest {

    @Mock private ZoneRepository zoneRepository;
    @InjectMocks private GeofenceService geofenceService;

    @Test
    void givenPointInOperatingZone_whenIsInServiceArea_thenReturnTrue() {
        UUID cityId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");
        Zone zone = new Zone();
        zone.setType("OPERATING");

        when(zoneRepository.findActiveZoneContainingPoint(cityId, "OPERATING", lng, lat))
                .thenReturn(Optional.of(zone));

        assertThat(geofenceService.isInServiceArea(cityId, lat, lng)).isTrue();
    }

    @Test
    void givenPointOutsideOperatingZone_whenIsInServiceArea_thenReturnFalse() {
        UUID cityId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");

        when(zoneRepository.findActiveZoneContainingPoint(cityId, "OPERATING", lng, lat))
                .thenReturn(Optional.empty());

        assertThat(geofenceService.isInServiceArea(cityId, lat, lng)).isFalse();
    }

    @Test
    void givenPointInRestrictedZone_whenIsRestricted_thenReturnTrue() {
        UUID cityId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");
        Zone zone = new Zone();
        zone.setType("RESTRICTED");

        when(zoneRepository.findActiveZoneContainingPoint(cityId, "RESTRICTED", lng, lat))
                .thenReturn(Optional.of(zone));

        assertThat(geofenceService.isRestricted(cityId, lat, lng)).isTrue();
    }

    @Test
    void givenPointNotInRestrictedZone_whenIsRestricted_thenReturnFalse() {
        UUID cityId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");

        when(zoneRepository.findActiveZoneContainingPoint(cityId, "RESTRICTED", lng, lat))
                .thenReturn(Optional.empty());

        assertThat(geofenceService.isRestricted(cityId, lat, lng)).isFalse();
    }

    @Test
    void givenPointInMultipleZones_whenFindAllZonesContaining_thenReturnAll() {
        UUID cityId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");
        Zone operating = new Zone();
        operating.setType("OPERATING");
        Zone surge = new Zone();
        surge.setType("SURGE");

        when(zoneRepository.findAllActiveZonesContainingPoint(cityId, lng, lat))
                .thenReturn(List.of(operating, surge));

        List<Zone> result = geofenceService.findAllZonesContaining(cityId, lat, lng);
        assertThat(result).hasSize(2);
    }

    @Test
    void givenPointInNoZones_whenFindAllZonesContaining_thenReturnEmpty() {
        UUID cityId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");

        when(zoneRepository.findAllActiveZonesContainingPoint(cityId, lng, lat))
                .thenReturn(Collections.emptyList());

        List<Zone> result = geofenceService.findAllZonesContaining(cityId, lat, lng);
        assertThat(result).isEmpty();
    }

    @Test
    void givenCityAndType_whenFindZone_thenDelegatesToRepository() {
        UUID cityId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");
        Zone airport = new Zone();
        airport.setType("AIRPORT");

        when(zoneRepository.findActiveZoneContainingPoint(cityId, "AIRPORT", lng, lat))
                .thenReturn(Optional.of(airport));

        Optional<Zone> result = geofenceService.findZone(cityId, "AIRPORT", lat, lng);
        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo("AIRPORT");
    }
}
