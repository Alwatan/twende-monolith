package tz.co.twende.driver.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVerifyRequest {

    @NotNull
    private Boolean verified;

    private String rejectionReason;
}
