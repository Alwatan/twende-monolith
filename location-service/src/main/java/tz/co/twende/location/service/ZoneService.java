package tz.co.twende.location.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.location.dto.CreateZoneRequest;
import tz.co.twende.location.dto.UpdateZoneRequest;
import tz.co.twende.location.entity.Zone;
import tz.co.twende.location.repository.ZoneRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoneService {

    private final ZoneRepository zoneRepository;

    public List<Zone> listZones(UUID cityId) {
        return zoneRepository.findByCityId(cityId);
    }

    @Transactional
    public Zone createZone(UUID cityId, String countryCode, CreateZoneRequest request) {
        Zone zone = new Zone();
        zone.setCityId(cityId);
        zone.setCountryCode(countryCode);
        zone.setName(request.getName());
        zone.setBoundary(request.getBoundary());
        zone.setType(request.getType());
        zone.setConfig(request.getConfig() != null ? request.getConfig() : "{}");
        zone.setActive(true);
        Zone saved = zoneRepository.save(zone);
        log.info(
                "Created zone '{}' (type={}) for city {}",
                saved.getName(),
                saved.getType(),
                cityId);
        return saved;
    }

    @Transactional
    public Zone updateZone(UUID zoneId, UpdateZoneRequest request) {
        Zone zone =
                zoneRepository
                        .findById(zoneId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Zone not found: " + zoneId));
        if (request.getName() != null) {
            zone.setName(request.getName());
        }
        if (request.getBoundary() != null) {
            zone.setBoundary(request.getBoundary());
        }
        if (request.getType() != null) {
            zone.setType(request.getType());
        }
        if (request.getConfig() != null) {
            zone.setConfig(request.getConfig());
        }
        if (request.getActive() != null) {
            zone.setActive(request.getActive());
        }
        return zoneRepository.save(zone);
    }

    @Transactional
    public void deactivateZone(UUID zoneId) {
        Zone zone =
                zoneRepository
                        .findById(zoneId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Zone not found: " + zoneId));
        zone.setActive(false);
        zoneRepository.save(zone);
        log.info("Deactivated zone '{}'", zone.getName());
    }
}
