package tz.co.twende.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkSocialRequestDto {

    @NotBlank(message = "Provider is required")
    @Pattern(regexp = "GOOGLE|APPLE", message = "Provider must be GOOGLE or APPLE")
    private String provider;

    @NotBlank(message = "ID token is required")
    private String idToken;
}
