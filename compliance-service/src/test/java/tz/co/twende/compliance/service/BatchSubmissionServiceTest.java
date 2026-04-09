package tz.co.twende.compliance.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import tz.co.twende.compliance.adapter.ComplianceAdapter;
import tz.co.twende.compliance.entity.TripReport;
import tz.co.twende.compliance.exception.ComplianceException;
import tz.co.twende.compliance.repository.TripReportRepository;

@ExtendWith(MockitoExtension.class)
class BatchSubmissionServiceTest {

    @Mock private TripReportRepository tripReportRepository;
    @Mock private ComplianceService complianceService;

    private BatchSubmissionService batchSubmissionService;

    @BeforeEach
    void setUp() {
        batchSubmissionService =
                new BatchSubmissionService(tripReportRepository, complianceService);
    }

    @Test
    void givenPendingReports_whenBatchRuns_thenReportsSubmitted() {
        ComplianceAdapter adapter = mock(ComplianceAdapter.class);
        when(adapter.isTripReportingRequired()).thenReturn(true);
        when(complianceService.getAdapters()).thenReturn(Map.of("TZ", adapter));

        TripReport report = buildReport();
        when(tripReportRepository.findByCountryCodeAndSubmittedFalseOrderByCreatedAtAsc(
                        eq("TZ"), any(Pageable.class)))
                .thenReturn(List.of(report));

        batchSubmissionService.submitPendingReports();

        verify(adapter).submitTripReport(report);
        verify(tripReportRepository).save(report);
        assert report.isSubmitted();
        assert report.getSubmittedAt() != null;
    }

    @Test
    void givenAdapterFailure_whenBatchRuns_thenErrorStoredAndContinues() {
        ComplianceAdapter adapter = mock(ComplianceAdapter.class);
        when(adapter.isTripReportingRequired()).thenReturn(true);
        doThrow(new ComplianceException("Connection timeout"))
                .when(adapter)
                .submitTripReport(any());
        when(complianceService.getAdapters()).thenReturn(Map.of("TZ", adapter));

        TripReport report = buildReport();
        when(tripReportRepository.findByCountryCodeAndSubmittedFalseOrderByCreatedAtAsc(
                        eq("TZ"), any(Pageable.class)))
                .thenReturn(List.of(report));

        batchSubmissionService.submitPendingReports();

        verify(tripReportRepository).save(report);
        assert !report.isSubmitted();
        assert report.getSubmissionError() != null;
        assert report.getSubmissionError().contains("Connection timeout");
    }

    @Test
    void givenReportingNotRequired_whenBatchRuns_thenSkipsCountry() {
        ComplianceAdapter adapter = mock(ComplianceAdapter.class);
        when(adapter.isTripReportingRequired()).thenReturn(false);
        when(complianceService.getAdapters()).thenReturn(Map.of("KE", adapter));

        batchSubmissionService.submitPendingReports();

        verify(tripReportRepository, never())
                .findByCountryCodeAndSubmittedFalseOrderByCreatedAtAsc(any(), any());
        verify(adapter, never()).submitTripReport(any());
    }

    @Test
    void givenNoPendingReports_whenBatchRuns_thenNoSubmissions() {
        ComplianceAdapter adapter = mock(ComplianceAdapter.class);
        when(adapter.isTripReportingRequired()).thenReturn(true);
        when(complianceService.getAdapters()).thenReturn(Map.of("TZ", adapter));
        when(tripReportRepository.findByCountryCodeAndSubmittedFalseOrderByCreatedAtAsc(
                        eq("TZ"), any(Pageable.class)))
                .thenReturn(List.of());

        batchSubmissionService.submitPendingReports();

        verify(adapter, never()).submitTripReport(any());
    }

    @Test
    void givenMixedResults_whenBatchRuns_thenProcessesAll() {
        ComplianceAdapter adapter = mock(ComplianceAdapter.class);
        when(adapter.isTripReportingRequired()).thenReturn(true);
        when(complianceService.getAdapters()).thenReturn(Map.of("TZ", adapter));

        TripReport report1 = buildReport();
        TripReport report2 = buildReport();

        doNothing().when(adapter).submitTripReport(report1);
        doThrow(new ComplianceException("fail")).when(adapter).submitTripReport(report2);

        when(tripReportRepository.findByCountryCodeAndSubmittedFalseOrderByCreatedAtAsc(
                        eq("TZ"), any(Pageable.class)))
                .thenReturn(List.of(report1, report2));

        batchSubmissionService.submitPendingReports();

        verify(tripReportRepository, times(2)).save(any());
        assert report1.isSubmitted();
        assert !report2.isSubmitted();
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
        report.setFare(new BigDecimal("3500.00"));
        return report;
    }
}
