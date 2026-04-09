package tz.co.twende.compliance.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tz.co.twende.compliance.entity.TripReport;

class SumatraAdapterTest {

    @Test
    void givenSumatraAdapter_whenGetCountryCode_thenReturnsTZ() {
        SumatraAdapter adapter = new SumatraAdapter("http://localhost:9999", "key", false);
        assertEquals("TZ", adapter.getCountryCode());
    }

    @Test
    void givenSumatraAdapter_whenIsTripReportingRequired_thenReturnsTrue() {
        SumatraAdapter adapter = new SumatraAdapter("http://localhost:9999", "key", false);
        assertTrue(adapter.isTripReportingRequired());
    }

    @Test
    void givenDisabledSumatra_whenSubmitTripReport_thenSimulatesSubmission() {
        SumatraAdapter adapter = new SumatraAdapter("http://localhost:9999", "key", false);

        TripReport report = buildReport();
        adapter.submitTripReport(report);

        assertNotNull(report.getSubmissionRef());
        assertTrue(report.getSubmissionRef().startsWith("SIMULATED-"));
    }

    @Test
    void givenNtsaAdapter_whenGetCountryCode_thenReturnsKE() {
        NtsaAdapter adapter = new NtsaAdapter();
        assertEquals("KE", adapter.getCountryCode());
        assertFalse(adapter.isTripReportingRequired());
    }

    @Test
    void givenNtsaAdapter_whenSubmit_thenThrowsUnsupported() {
        NtsaAdapter adapter = new NtsaAdapter();
        assertThrows(
                UnsupportedOperationException.class, () -> adapter.submitTripReport(buildReport()));
    }

    @Test
    void givenKccaAdapter_whenGetCountryCode_thenReturnsUG() {
        KccaAdapter adapter = new KccaAdapter();
        assertEquals("UG", adapter.getCountryCode());
        assertFalse(adapter.isTripReportingRequired());
    }

    @Test
    void givenKccaAdapter_whenSubmit_thenThrowsUnsupported() {
        KccaAdapter adapter = new KccaAdapter();
        assertThrows(
                UnsupportedOperationException.class, () -> adapter.submitTripReport(buildReport()));
    }

    private TripReport buildReport() {
        TripReport report = new TripReport();
        report.setId(UUID.randomUUID());
        report.setCountryCode("TZ");
        report.setRideId(UUID.randomUUID());
        report.setDriverId(UUID.randomUUID());
        report.setRiderId(UUID.randomUUID());
        report.setVehicleType("BAJAJ");
        report.setPickupLat(new BigDecimal("-6.7728"));
        report.setPickupLng(new BigDecimal("39.2310"));
        report.setDropoffLat(new BigDecimal("-6.8160"));
        report.setDropoffLng(new BigDecimal("39.2803"));
        report.setDistanceMetres(8200);
        report.setDurationSeconds(900);
        report.setFare(new BigDecimal("3500.00"));
        report.setCurrency("TZS");
        report.setStartedAt(Instant.now().minusSeconds(900));
        report.setCompletedAt(Instant.now());
        return report;
    }
}
