package tz.co.twende.compliance.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.compliance.adapter.ComplianceAdapter;
import tz.co.twende.compliance.entity.TripReport;
import tz.co.twende.compliance.repository.TripReportRepository;

@ExtendWith(MockitoExtension.class)
class ComplianceServiceTest {

    @Mock private TripReportRepository tripReportRepository;

    private ComplianceService complianceService;

    private ComplianceService createServiceWithAdapters(List<ComplianceAdapter> adapters) {
        return new ComplianceService(adapters, tripReportRepository);
    }

    @Test
    void givenTzCountryCode_whenGetAdapter_thenReturnsSumatraAdapter() {
        ComplianceAdapter tzAdapter = mock(ComplianceAdapter.class);
        when(tzAdapter.getCountryCode()).thenReturn("TZ");
        complianceService = createServiceWithAdapters(List.of(tzAdapter));

        ComplianceAdapter result = complianceService.getAdapter("TZ");
        assertEquals(tzAdapter, result);
    }

    @Test
    void givenUnknownCountryCode_whenGetAdapter_thenThrowsBadRequest() {
        ComplianceAdapter tzAdapter = mock(ComplianceAdapter.class);
        when(tzAdapter.getCountryCode()).thenReturn("TZ");
        complianceService = createServiceWithAdapters(List.of(tzAdapter));

        assertThrows(BadRequestException.class, () -> complianceService.getAdapter("XX"));
    }

    @Test
    void givenMultipleAdapters_whenGetAdapter_thenResolvesCorrectly() {
        ComplianceAdapter tzAdapter = mock(ComplianceAdapter.class);
        when(tzAdapter.getCountryCode()).thenReturn("TZ");
        ComplianceAdapter keAdapter = mock(ComplianceAdapter.class);
        when(keAdapter.getCountryCode()).thenReturn("KE");
        complianceService = createServiceWithAdapters(List.of(tzAdapter, keAdapter));

        assertEquals(tzAdapter, complianceService.getAdapter("TZ"));
        assertEquals(keAdapter, complianceService.getAdapter("KE"));
    }

    @Test
    void givenNewRideEvent_whenCreateTripReport_thenReportCreated() {
        complianceService = createServiceWithAdapters(List.of());
        RideCompletedEvent event = buildEvent();
        when(tripReportRepository.existsByRideId(event.getRideId())).thenReturn(false);
        when(tripReportRepository.save(any(TripReport.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TripReport result = complianceService.createTripReport(event);

        assertNotNull(result);
        assertEquals(event.getRideId(), result.getRideId());
        assertEquals(event.getDriverId(), result.getDriverId());
        assertEquals(event.getFinalFare(), result.getFare());
        verify(tripReportRepository).save(any(TripReport.class));
    }

    @Test
    void givenDuplicateRideEvent_whenCreateTripReport_thenReturnsNull() {
        complianceService = createServiceWithAdapters(List.of());
        RideCompletedEvent event = buildEvent();
        when(tripReportRepository.existsByRideId(event.getRideId())).thenReturn(true);

        TripReport result = complianceService.createTripReport(event);

        assertNull(result);
        verify(tripReportRepository, never()).save(any());
    }

    @Test
    void givenNonExistentId_whenGetReportById_thenThrowsNotFound() {
        complianceService = createServiceWithAdapters(List.of());
        UUID id = UUID.randomUUID();
        when(tripReportRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> complianceService.getReportById(id));
    }

    @Test
    void givenExistingId_whenGetReportById_thenReturnsReport() {
        complianceService = createServiceWithAdapters(List.of());
        UUID id = UUID.randomUUID();
        TripReport report = new TripReport();
        report.setId(id);
        when(tripReportRepository.findById(id)).thenReturn(Optional.of(report));

        TripReport result = complianceService.getReportById(id);
        assertEquals(id, result.getId());
    }

    private RideCompletedEvent buildEvent() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setCountryCode("TZ");
        event.setRideId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setVehicleType("BAJAJ");
        event.setDropoffLat(new BigDecimal("-6.8160"));
        event.setDropoffLng(new BigDecimal("39.2803"));
        event.setDistanceMetres(8200);
        event.setDurationSeconds(900);
        event.setFinalFare(new BigDecimal("3500.00"));
        event.setStartedAt(Instant.now().minusSeconds(900));
        event.setCompletedAt(Instant.now());
        return event;
    }
}
