package com.twende.auth.controller;

import com.twende.auth.dto.*;
import com.twende.auth.service.AuthService;
import com.twende.auth.service.OtpService;
import com.twende.auth.service.TokenService;
import com.twende.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OtpService otpService;
    private final AuthService authService;
    private final TokenService tokenService;

    /**
     * Request a 6-digit OTP to be sent to the given phone number. Public endpoint -- no
     * authentication required. Never reveals whether the phone number is already registered.
     */
    @PostMapping("/otp/request")
    public ResponseEntity<ApiResponse<Void>> requestOtp(@Valid @RequestBody OtpRequestDto request) {
        otpService.requestOtp(request.getPhoneNumber(), request.getCountryCode());
        return ResponseEntity.ok(ApiResponse.ok(null, "OTP sent successfully"));
    }

    /**
     * Verify an OTP and receive access + refresh tokens. Public endpoint. If the phone number is
     * new, the response will have isNewUser=true, indicating that the client should call /register
     * next.
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<TokenResponseDto>> verifyOtp(
            @Valid @RequestBody OtpVerifyDto request) {
        TokenResponseDto tokenResponse = authService.authenticateWithOtp(request);
        return ResponseEntity.ok(ApiResponse.ok(tokenResponse));
    }

    /**
     * Complete registration for a new user. Requires a valid Bearer token obtained from OTP
     * verification. Updates the user's profile with fullName, role, and countryCode, then publishes
     * a UserRegisteredEvent to Kafka.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequestDto request) {
        UUID userId = extractUserId();
        authService.register(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Registration completed successfully"));
    }

    /**
     * Revoke the refresh token, effectively logging the user out. Requires authentication. The
     * refresh token's JTI is added to a Redis blocklist and persisted to DB.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequestDto request) {
        tokenService.revokeToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out successfully"));
    }

    /** Get the current authenticated user's information. Requires a valid Bearer token. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoDto>> getCurrentUser() {
        UUID userId = extractUserId();
        UserInfoDto userInfo = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.ok(userInfo));
    }

    private UUID extractUserId() {
        String subject = SecurityContextHolder.getContext().getAuthentication().getName();
        return UUID.fromString(subject);
    }
}
