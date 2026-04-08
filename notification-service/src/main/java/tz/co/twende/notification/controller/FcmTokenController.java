package tz.co.twende.notification.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.notification.dto.request.RegisterFcmTokenRequest;
import tz.co.twende.notification.dto.response.FcmTokenDto;
import tz.co.twende.notification.entity.FcmToken;
import tz.co.twende.notification.mapper.NotificationMapper;
import tz.co.twende.notification.repository.FcmTokenRepository;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationMapper notificationMapper;

    @PostMapping("/fcm-token")
    public ResponseEntity<ApiResponse<FcmTokenDto>> registerFcmToken(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Country-Code", defaultValue = "TZ") String countryCode,
            @Valid @RequestBody RegisterFcmTokenRequest request) {

        FcmToken token =
                fcmTokenRepository
                        .findByUserIdAndToken(userId, request.getToken())
                        .map(
                                existing -> {
                                    existing.setActive(true);
                                    existing.setPlatform(request.getPlatform());
                                    return existing;
                                })
                        .orElseGet(
                                () -> {
                                    FcmToken newToken = new FcmToken();
                                    newToken.setUserId(userId);
                                    newToken.setToken(request.getToken());
                                    newToken.setPlatform(request.getPlatform());
                                    newToken.setActive(true);
                                    newToken.setCountryCode(countryCode);
                                    return newToken;
                                });

        FcmToken saved = fcmTokenRepository.save(token);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.ok(notificationMapper.toFcmTokenDto(saved)));
    }
}
