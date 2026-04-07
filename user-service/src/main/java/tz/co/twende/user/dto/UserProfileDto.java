package tz.co.twende.user.dto;

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
public class UserProfileDto {
    private UUID id;
    private String countryCode;
    private String fullName;
    private String email;
    private String profilePhotoUrl;
    private String preferredLocale;
    private String preferredPaymentMethod;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
