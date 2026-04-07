package tz.co.twende.driver.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDriverRequest {

    @Size(min = 2, max = 150)
    private String fullName;

    @Email
    private String email;

    @Size(max = 500)
    private String profilePhotoUrl;
}
