package tz.co.twende.driver.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @NotNull
    private Boolean approved;

    private String rejectionReason;
}
