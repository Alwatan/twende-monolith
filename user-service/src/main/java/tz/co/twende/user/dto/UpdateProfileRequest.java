package tz.co.twende.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(min = 2, max = 150)
    private String fullName;

    @Email private String email;

    @Size(max = 10)
    private String preferredLocale;

    @Size(max = 30)
    private String preferredPaymentMethod;
}
