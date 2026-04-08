package tz.co.twende.notification.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.notification.dto.request.RegisterFcmTokenRequest;
import tz.co.twende.notification.dto.response.FcmTokenDto;
import tz.co.twende.notification.entity.FcmToken;
import tz.co.twende.notification.mapper.NotificationMapper;
import tz.co.twende.notification.repository.FcmTokenRepository;

@ExtendWith(MockitoExtension.class)
class FcmTokenControllerTest {

    @Mock private FcmTokenRepository fcmTokenRepository;
    @Mock private NotificationMapper notificationMapper;

    @InjectMocks private FcmTokenController controller;

    @Test
    void givenNewToken_whenRegister_thenCreated() {
        UUID userId = UUID.randomUUID();
        RegisterFcmTokenRequest request = new RegisterFcmTokenRequest("token-123", "ANDROID");

        when(fcmTokenRepository.findByUserIdAndToken(userId, "token-123"))
                .thenReturn(Optional.empty());

        FcmToken saved = new FcmToken();
        saved.setUserId(userId);
        saved.setToken("token-123");
        saved.setPlatform("ANDROID");
        saved.setActive(true);
        when(fcmTokenRepository.save(any(FcmToken.class))).thenReturn(saved);

        FcmTokenDto dto = new FcmTokenDto();
        dto.setToken("token-123");
        when(notificationMapper.toFcmTokenDto(any())).thenReturn(dto);

        ResponseEntity<ApiResponse<FcmTokenDto>> response =
                controller.registerFcmToken(userId, "TZ", request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        ArgumentCaptor<FcmToken> captor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getToken()).isEqualTo("token-123");
    }

    @Test
    void givenExistingToken_whenRegister_thenUpdated() {
        UUID userId = UUID.randomUUID();
        RegisterFcmTokenRequest request = new RegisterFcmTokenRequest("token-123", "IOS");

        FcmToken existing = new FcmToken();
        existing.setUserId(userId);
        existing.setToken("token-123");
        existing.setPlatform("ANDROID");
        existing.setActive(false);

        when(fcmTokenRepository.findByUserIdAndToken(userId, "token-123"))
                .thenReturn(Optional.of(existing));
        when(fcmTokenRepository.save(any(FcmToken.class))).thenReturn(existing);

        FcmTokenDto dto = new FcmTokenDto();
        when(notificationMapper.toFcmTokenDto(any())).thenReturn(dto);

        controller.registerFcmToken(userId, "TZ", request);

        ArgumentCaptor<FcmToken> captor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getPlatform()).isEqualTo("IOS");
    }
}
