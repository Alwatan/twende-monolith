package tz.co.twende.driver.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.DocumentType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverDocumentDto {
    private UUID id;
    private UUID driverId;
    private DocumentType documentType;
    private String fileUrl;
    private String status;
    private String rejectionReason;
    private Instant verifiedAt;
    private Instant expiresAt;
    private Instant createdAt;
}
