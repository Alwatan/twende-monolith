package tz.co.twende.compliance.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tz.co.twende.compliance.adapter.ComplianceAdapter;
import tz.co.twende.compliance.entity.TripReport;
import tz.co.twende.compliance.exception.ComplianceException;
import tz.co.twende.compliance.repository.TripReportRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchSubmissionService {

    private final TripReportRepository tripReportRepository;
    private final ComplianceService complianceService;

    @Scheduled(cron = "0 0 * * * *")
    public void submitPendingReports() {
        Map<String, ComplianceAdapter> adapters = complianceService.getAdapters();

        for (Map.Entry<String, ComplianceAdapter> entry : adapters.entrySet()) {
            String countryCode = entry.getKey();
            ComplianceAdapter adapter = entry.getValue();

            if (!adapter.isTripReportingRequired()) {
                log.debug("Skipping batch for {} - reporting not required", countryCode);
                continue;
            }

            List<TripReport> pending =
                    tripReportRepository.findByCountryCodeAndSubmittedFalseOrderByCreatedAtAsc(
                            countryCode, PageRequest.of(0, 500));

            int successCount = 0;
            int failCount = 0;

            for (TripReport report : pending) {
                try {
                    adapter.submitTripReport(report);
                    report.setSubmitted(true);
                    report.setSubmittedAt(Instant.now());
                    report.setSubmissionError(null);
                    successCount++;
                } catch (ComplianceException e) {
                    report.setSubmissionError(e.getMessage());
                    failCount++;
                }
                tripReportRepository.save(report);
            }

            log.info(
                    "Compliance batch for {}: {} pending, {} submitted, {} failed",
                    countryCode,
                    pending.size(),
                    successCount,
                    failCount);
        }
    }
}
