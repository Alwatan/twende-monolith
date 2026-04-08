package tz.co.twende.rating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitRatingRequest {

    @NotNull private UUID rideId;

    @NotNull
    @Min(1)
    @Max(5)
    private Short score;

    private String comment;
}
