package tz.co.twende.compliance.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.compliance.adapter.ComplianceAdapter;
import tz.co.twende.compliance.dto.SubmissionStatsDto;
import tz.co.twende.compliance.entity.TripReport;
import tz.co.twende.compliance.exception.ComplianceException;
import tz.co.twende.compliance.repository.TripReportRepository;

@Service
@Slf4j
public class ComplianceService {

    private final Map<String, ComplianceAdapter> adapters;
    private final TripReportRepository tripReportRepository;

    public ComplianceService(
            List<ComplianceAdapter> adapterList, TripReportRepository tripReportRepository) {
        this.adapters =
                adapterList.stream()
                        .collect(Collectors.toMap(ComplianceAdapter::getCountryCode, a -> a));
        this.tripReportRepository = tripReportRepository;
    }

    public ComplianceAdapter getAdapter(String countryCode) {
        ComplianceAdapter adapter = adapters.get(countryCode);
        if (adapter == null) {
            throw new BadRequestException("No compliance adapter for country: " + countryCode);
        }
        return adapter;
    }

    public Map<String, ComplianceAdapter> getAdapters() {
        return adapters;
    }

    public TripReport createTripReport(RideCompletedEvent event) {
        if (tripReportRepository.existsByRideId(event.getRideId())) {
            log.warn("Trip report already exists for ride {}", event.getRideId());
            return null;
        }

        TripReport report = new TripReport();
        report.setCountryCode(event.getCountryCode());
        report.setRideId(event.getRideId());
        report.setDriverId(event.getDriverId());
        report.setRiderId(event.getRiderId());
        report.setVehicleType(event.getVehicleType());
        report.setPickupLat(
                event.getDropoffLat() != null ? event.getDropoffLat() : java.math.BigDecimal.ZERO);
        report.setPickupLng(
                event.getDropoffLng() != null ? event.getDropoffLng() : java.math.BigDecimal.ZERO);
        report.setDropoffLat(
                event.getDropoffLat() != null ? event.getDropoffLat() : java.math.BigDecimal.ZERO);
        report.setDropoffLng(
                event.getDropoffLng() != null ? event.getDropoffLng() : java.math.BigDecimal.ZERO);
        report.setDistanceMetres(event.getDistanceMetres());
        report.setDurationSeconds(event.getDurationSeconds());
        report.setFare(event.getFinalFare());
        report.setCurrency("TZS");
        report.setStartedAt(event.getStartedAt());
        report.setCompletedAt(event.getCompletedAt());

        return tripReportRepository.save(report);
    }

    public Page<TripReport> getReports(
            String countryCode, Boolean submitted, Instant from, Instant to, Pageable pageable) {
        return tripReportRepository.findWithFilters(countryCode, submitted, from, to, pageable);
    }

    public TripReport getReportById(UUID id) {
        return tripReportRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip report not found: " + id));
    }

    public List<SubmissionStatsDto> getStats() {
        List<String> countryCodes = tripReportRepository.findDistinctCountryCodes();
        List<SubmissionStatsDto> stats = new ArrayList<>();

        for (String code : countryCodes) {
            SubmissionStatsDto dto = new SubmissionStatsDto();
            dto.setCountryCode(code);
            dto.setTotalReports(tripReportRepository.countByCountryCode(code));
            dto.setSubmitted(tripReportRepository.countByCountryCodeAndSubmittedTrue(code));
            dto.setPending(tripReportRepository.countByCountryCodeAndSubmittedFalse(code));
            dto.setFailed(tripReportRepository.countByCountryCodeAndSubmissionErrorIsNotNull(code));
            dto.setLastSubmissionAt(tripReportRepository.findLastSubmissionAt(code));
            stats.add(dto);
        }

        return stats;
    }

    public int retryFailedSubmissions(String countryCode) {
        ComplianceAdapter adapter = getAdapter(countryCode);
        if (!adapter.isTripReportingRequired()) {
            return 0;
        }

        List<TripReport> pending =
                tripReportRepository.findByCountryCodeAndSubmittedFalseOrderByCreatedAtAsc(
                        countryCode, PageRequest.of(0, 500));

        int processed = 0;
        for (TripReport report : pending) {
            try {
                adapter.submitTripReport(report);
                report.setSubmitted(true);
                report.setSubmittedAt(Instant.now());
                report.setSubmissionError(null);
            } catch (ComplianceException e) {
                report.setSubmissionError(e.getMessage());
            }
            tripReportRepository.save(report);
            processed++;
        }
        return processed;
    }
}
