package tz.co.twende.location.dto;

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
public class ZoneDto {
    private UUID id;
    private String countryCode;
    private UUID cityId;
    private String name;
    private String boundary;
    private String type;
    private String config;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
