package tz.co.twende.matching.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcceptRejectResponse {

    private UUID rideId;
    private UUID driverId;
    private String action;
    private boolean success;
    private String message;
}
