package tz.co.twende.ride.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartTripRequest {

    @NotBlank(message = "OTP is required")
    private String otp;
}
