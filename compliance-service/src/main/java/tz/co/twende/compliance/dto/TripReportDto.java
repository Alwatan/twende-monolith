package tz.co.twende.compliance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripReportDto {
    private UUID id;
    private UUID rideId;
    private String countryCode;
    private UUID driverId;
    private UUID riderId;
    private String vehicleType;
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private BigDecimal dropoffLat;
    private BigDecimal dropoffLng;
    private Integer distanceMetres;
    private Integer durationSeconds;
    private BigDecimal fare;
    private String currency;
    private Instant startedAt;
    private Instant completedAt;
    private boolean submitted;
    private Instant submittedAt;
    private String submissionRef;
    private String submissionError;
    private Instant createdAt;
}
