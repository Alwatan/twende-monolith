package tz.co.twende.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.common.response.PagedResponse;
import tz.co.twende.user.client.RideServiceClient;
import tz.co.twende.user.dto.RideHistoryResponse;
import tz.co.twende.user.dto.UpdateProfileRequest;
import tz.co.twende.user.dto.UserProfileDto;
import tz.co.twende.user.entity.UserProfile;
import tz.co.twende.user.kafka.UserProfileUpdatedProducer;
import tz.co.twende.user.kafka.event.UserProfileUpdatedEvent;
import tz.co.twende.user.mapper.UserMapper;
import tz.co.twende.user.repository.UserProfileRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserProfileUpdatedProducer profileUpdatedProducer;
    @Mock private RideServiceClient rideServiceClient;
    @Mock private UserMapper userMapper;

    @InjectMocks private UserService userService;

    @Test
    void givenExistingUser_whenGetProfile_thenReturnDto() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = createProfile(userId, "Jane Doe", "TZ");
        UserProfileDto dto =
                UserProfileDto.builder().id(userId).fullName("Jane Doe").countryCode("TZ").build();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(userMapper.toDto(profile)).thenReturn(dto);

        UserProfileDto result = userService.getProfile(userId);

        assertThat(result.getFullName()).isEqualTo("Jane Doe");
        assertThat(result.getId()).isEqualTo(userId);
    }

    @Test
    void givenNonExistingUser_whenGetProfile_thenThrowNotFound() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(userId.toString());
    }

    @Test
    void givenValidUpdate_whenUpdateProfile_thenSaveAndPublishEvent() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = createProfile(userId, "Jane Doe", "TZ");
        UpdateProfileRequest request =
                UpdateProfileRequest.builder()
                        .fullName("Jane Updated")
                        .email("jane@test.com")
                        .build();
        UserProfileDto dto =
                UserProfileDto.builder()
                        .id(userId)
                        .fullName("Jane Updated")
                        .email("jane@test.com")
                        .countryCode("TZ")
                        .build();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(profile);
        when(userMapper.toDto(any(UserProfile.class))).thenReturn(dto);

        UserProfileDto result = userService.updateProfile(userId, request);

        assertThat(result.getFullName()).isEqualTo("Jane Updated");
        verify(userProfileRepository).save(any(UserProfile.class));

        ArgumentCaptor<UserProfileUpdatedEvent> captor =
                ArgumentCaptor.forClass(UserProfileUpdatedEvent.class);
        verify(profileUpdatedProducer).send(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void givenPartialUpdate_whenUpdateProfile_thenOnlyUpdateProvidedFields() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = createProfile(userId, "Jane Doe", "TZ");
        profile.setEmail("old@test.com");
        UpdateProfileRequest request = UpdateProfileRequest.builder().fullName("New Name").build();
        UserProfileDto dto = UserProfileDto.builder().id(userId).fullName("New Name").build();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(profile);
        when(userMapper.toDto(any(UserProfile.class))).thenReturn(dto);

        userService.updateProfile(userId, request);

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getFullName()).isEqualTo("New Name");
        assertThat(profileCaptor.getValue().getEmail()).isEqualTo("old@test.com");
    }

    @Test
    void givenNonExistingUser_whenUpdateProfile_thenThrowNotFound() {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = UpdateProfileRequest.builder().fullName("Test").build();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(userId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void givenUserId_whenGetRideHistory_thenDelegatesToRideServiceClient() {
        UUID userId = UUID.randomUUID();
        PagedResponse<RideHistoryResponse> expected =
                PagedResponse.<RideHistoryResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(20)
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build();
        when(rideServiceClient.getRideHistory(userId, 0, 20)).thenReturn(expected);

        PagedResponse<RideHistoryResponse> result = userService.getRideHistory(userId, 0, 20);

        assertThat(result).isEqualTo(expected);
        verify(rideServiceClient).getRideHistory(userId, 0, 20);
    }

    @Test
    void givenUpdateWithPreferredPaymentMethod_whenUpdateProfile_thenFieldUpdated() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = createProfile(userId, "Jane Doe", "TZ");
        UpdateProfileRequest request =
                UpdateProfileRequest.builder().preferredPaymentMethod("CASH").build();
        UserProfileDto dto = UserProfileDto.builder().id(userId).fullName("Jane Doe").build();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(profile);
        when(userMapper.toDto(any(UserProfile.class))).thenReturn(dto);

        userService.updateProfile(userId, request);

        org.mockito.ArgumentCaptor<UserProfile> captor =
                org.mockito.ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getPreferredPaymentMethod()).isEqualTo("CASH");
    }

    private UserProfile createProfile(UUID id, String fullName, String countryCode) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setFullName(fullName);
        profile.setCountryCode(countryCode);
        profile.setIsActive(true);
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());
        return profile;
    }
}
