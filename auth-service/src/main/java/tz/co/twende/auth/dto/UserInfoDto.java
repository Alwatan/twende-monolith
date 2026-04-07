package tz.co.twende.auth.dto;

import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoDto {

    private UUID userId;
    private String phoneNumber;
    private String fullName;
    private String role;
    private String countryCode;
    private boolean phoneVerified;
}
