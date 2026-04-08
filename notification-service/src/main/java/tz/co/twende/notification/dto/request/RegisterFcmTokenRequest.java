package tz.co.twende.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterFcmTokenRequest {

    @NotBlank(message = "FCM token is required")
    private String token;

    @NotBlank(message = "Platform is required (ANDROID or IOS)")
    private String platform;
}
