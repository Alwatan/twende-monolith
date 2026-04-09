package tz.co.twende.compliance.adapter;

import tz.co.twende.compliance.entity.TripReport;
import tz.co.twende.compliance.exception.ComplianceException;

public interface ComplianceAdapter {

    /** Returns the ISO 3166-1 alpha-2 country code this adapter handles. */
    String getCountryCode();

    /**
     * Submit a single trip report to the country's regulatory authority.
     *
     * @throws ComplianceException if submission fails (will be retried in next batch)
     */
    void submitTripReport(TripReport report) throws ComplianceException;

    /** Whether this country requires trip reporting. */
    boolean isTripReportingRequired();
}
