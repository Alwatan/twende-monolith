package tz.co.twende.ride.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfferAcceptedRequest {

    @NotNull private UUID driverId;

    private int estimatedArrivalSeconds;
}
