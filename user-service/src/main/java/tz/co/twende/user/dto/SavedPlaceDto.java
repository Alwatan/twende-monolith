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
public class SavedPlaceDto {
    private UUID id;
    private UUID userId;
    private String label;
    private String address;
    private Double latitude;
    private Double longitude;
    private Instant createdAt;
}
