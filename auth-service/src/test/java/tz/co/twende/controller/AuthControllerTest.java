package tz.co.twende.auth.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tz.co.twende.auth.dto.OtpRequestDto;
import tz.co.twende.auth.dto.OtpVerifyDto;
import tz.co.twende.auth.dto.TokenResponseDto;
import tz.co.twende.auth.service.AuthService;
import tz.co.twende.auth.service.OtpService;
import tz.co.twende.auth.service.TokenService;
import tz.co.twende.common.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private OtpService otpService;
    @Mock private AuthService authService;
    @Mock private TokenService tokenService;

    @InjectMocks private AuthController authController;

    @Test
    void givenValidPayload_whenRequestOtp_thenReturns200() {
        OtpRequestDto request = new OtpRequestDto("+255712345678", "TZ");
        doNothing().when(otpService).requestOtp("+255712345678", "TZ");

        ResponseEntity<ApiResponse<Void>> response = authController.requestOtp(request);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        verify(otpService).requestOtp("+255712345678", "TZ");
    }

    @Test
    void givenValidPayload_whenVerifyOtp_thenReturnsTokens() {
        OtpVerifyDto request = new OtpVerifyDto("+255712345678", "123456");
        TokenResponseDto tokenResponse =
                TokenResponseDto.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .tokenType("Bearer")
                        .expiresIn(3600)
                        .isNewUser(false)
                        .build();

        when(authService.authenticateWithOtp(any(OtpVerifyDto.class))).thenReturn(tokenResponse);

        ResponseEntity<ApiResponse<TokenResponseDto>> response = authController.verifyOtp(request);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        assertEquals("access-token", response.getBody().getData().getAccessToken());
        verify(authService).authenticateWithOtp(any(OtpVerifyDto.class));
    }

    @Test
    void givenValidPayload_whenVerifyOtp_thenReturnsNewUserFlag() {
        OtpVerifyDto request = new OtpVerifyDto("+255712345678", "654321");
        TokenResponseDto tokenResponse =
                TokenResponseDto.builder()
                        .accessToken("token")
                        .refreshToken("refresh")
                        .tokenType("Bearer")
                        .expiresIn(3600)
                        .isNewUser(true)
                        .build();

        when(authService.authenticateWithOtp(any())).thenReturn(tokenResponse);

        ResponseEntity<ApiResponse<TokenResponseDto>> response = authController.verifyOtp(request);

        assertTrue(response.getBody().getData().isNewUser());
    }
}
