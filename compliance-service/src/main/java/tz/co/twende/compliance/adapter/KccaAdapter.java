package tz.co.twende.compliance.adapter;

import org.springframework.stereotype.Component;
import tz.co.twende.compliance.entity.TripReport;

@Component
public class KccaAdapter implements ComplianceAdapter {

    @Override
    public String getCountryCode() {
        return "UG";
    }

    @Override
    public boolean isTripReportingRequired() {
        return false;
    }

    @Override
    public void submitTripReport(TripReport report) {
        throw new UnsupportedOperationException("KCCA integration not yet implemented");
    }
}
