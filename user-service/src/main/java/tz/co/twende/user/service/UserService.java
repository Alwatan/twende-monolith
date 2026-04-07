package tz.co.twende.user.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserProfileUpdatedProducer profileUpdatedProducer;
    private final RideServiceClient rideServiceClient;
    private final UserMapper userMapper;

    public UserProfileDto getProfile(UUID userId) {
        UserProfile profile =
                userProfileRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "User not found with id: " + userId));
        return userMapper.toDto(profile);
    }

    @Transactional
    public UserProfileDto updateProfile(UUID userId, UpdateProfileRequest request) {
        UserProfile profile =
                userProfileRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "User not found with id: " + userId));

        if (request.getFullName() != null) {
            profile.setFullName(request.getFullName());
        }
        if (request.getEmail() != null) {
            profile.setEmail(request.getEmail());
        }
        if (request.getPreferredLocale() != null) {
            profile.setPreferredLocale(request.getPreferredLocale());
        }
        if (request.getPreferredPaymentMethod() != null) {
            profile.setPreferredPaymentMethod(request.getPreferredPaymentMethod());
        }

        UserProfile saved = userProfileRepository.save(profile);

        profileUpdatedProducer.send(
                UserProfileUpdatedEvent.builder()
                        .userId(saved.getId())
                        .fullName(saved.getFullName())
                        .email(saved.getEmail())
                        .countryCode(saved.getCountryCode())
                        .timestamp(Instant.now())
                        .build());

        return userMapper.toDto(saved);
    }

    public PagedResponse<RideHistoryResponse> getRideHistory(UUID userId, int page, int size) {
        return rideServiceClient.getRideHistory(userId, page, size);
    }
}
