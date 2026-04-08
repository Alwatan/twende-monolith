package tz.co.twende.location.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tz.co.twende.location.entity.Zone;
import tz.co.twende.location.repository.ZoneRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeofenceService {

    private final ZoneRepository zoneRepository;

    public Optional<Zone> findZone(UUID cityId, String type, BigDecimal lat, BigDecimal lng) {
        return zoneRepository.findActiveZoneContainingPoint(cityId, type, lng, lat);
    }

    public boolean isInServiceArea(UUID cityId, BigDecimal lat, BigDecimal lng) {
        return findZone(cityId, "OPERATING", lat, lng).isPresent();
    }

    public boolean isRestricted(UUID cityId, BigDecimal lat, BigDecimal lng) {
        return findZone(cityId, "RESTRICTED", lat, lng).isPresent();
    }

    public List<Zone> findAllZonesContaining(UUID cityId, BigDecimal lat, BigDecimal lng) {
        return zoneRepository.findAllActiveZonesContainingPoint(cityId, lng, lat);
    }
}
