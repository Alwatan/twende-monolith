package tz.co.twende.notification.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FcmTokenDto {
    private UUID id;
    private UUID userId;
    private String token;
    private String platform;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
