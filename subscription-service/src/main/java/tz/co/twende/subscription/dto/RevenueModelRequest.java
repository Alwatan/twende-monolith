package tz.co.twende.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevenueModelRequest {

    @NotBlank private String revenueModel;

    @NotBlank private String serviceCategory;
}
