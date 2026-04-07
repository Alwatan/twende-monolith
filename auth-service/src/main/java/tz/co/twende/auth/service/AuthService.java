package tz.co.twende.auth.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.auth.dto.OtpVerifyDto;
import tz.co.twende.auth.dto.RegisterRequestDto;
import tz.co.twende.auth.dto.TokenResponseDto;
import tz.co.twende.auth.dto.UserInfoDto;
import tz.co.twende.auth.entity.AuthUser;
import tz.co.twende.auth.entity.OtpCode;
import tz.co.twende.auth.repository.AuthUserRepository;
import tz.co.twende.common.enums.UserRole;
import tz.co.twende.common.event.user.UserRegisteredEvent;
import tz.co.twende.common.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private static final String USER_REGISTERED_TOPIC = "twende.users.registered";

    private final AuthUserRepository authUserRepository;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Authenticate a user by verifying their OTP. If the phone number is new, a new AuthUser is
     * created and marked as a new user.
     *
     * @param request the OTP verification request containing phoneNumber and otp
     * @return TokenResponseDto with access/refresh tokens and isNewUser flag
     */
    public TokenResponseDto authenticateWithOtp(OtpVerifyDto request) {
        OtpCode verifiedOtp = otpService.verifyOtp(request.getPhoneNumber(), request.getOtp());

        String normalisedPhone = verifiedOtp.getPhoneNumber();
        boolean isNewUser = false;

        var existingUser = authUserRepository.findByPhoneNumber(normalisedPhone);

        AuthUser user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            isNewUser = !user.isPhoneVerified();
        } else {
            user = new AuthUser();
            user.setPhoneNumber(normalisedPhone);
            user.setCountryCode(verifiedOtp.getCountryCode());
            user.setRole(UserRole.RIDER);
            user.setActive(true);
            user.setPhoneVerified(false);
            user = authUserRepository.save(user);
            isNewUser = true;
        }

        log.info(
                "User authenticated: phone={}, userId={}, isNewUser={}",
                normalisedPhone,
                user.getId(),
                isNewUser);

        return tokenService.issueTokens(user, isNewUser);
    }

    /**
     * Complete registration for a newly authenticated user. Updates the AuthUser with profile
     * details and publishes a UserRegisteredEvent to Kafka.
     *
     * @param userId the ID of the authenticated user (from JWT sub claim)
     * @param request registration details (fullName, role, countryCode)
     */
    public void register(UUID userId, RegisterRequestDto request) {
        AuthUser user =
                authUserRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("User not found: " + userId));

        user.setFullName(request.getFullName());
        user.setRole(request.getRole());
        user.setCountryCode(request.getCountryCode());
        user.setPhoneVerified(true);
        authUserRepository.save(user);

        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setEventType("USER_REGISTERED");
        event.setCountryCode(request.getCountryCode());
        event.setUserId(userId);
        event.setFullName(request.getFullName());
        event.setPhoneNumber(user.getPhoneNumber());
        event.setRole(request.getRole());
        event.setEmail(user.getEmail());
        event.setProfilePhotoUrl(user.getProfilePhotoUrl());
        event.setAuthProvider(user.getAuthProvider());

        kafkaTemplate.send(USER_REGISTERED_TOPIC, userId.toString(), event);
        log.info(
                "User registered: userId={}, role={}, countryCode={}",
                userId,
                request.getRole(),
                request.getCountryCode());
    }

    /**
     * Get current user information by user ID.
     *
     * @param userId the user's UUID (from JWT sub claim)
     * @return UserInfoDto with user details
     */
    @Transactional(readOnly = true)
    public UserInfoDto getCurrentUser(UUID userId) {
        AuthUser user =
                authUserRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("User not found: " + userId));

        return UserInfoDto.builder()
                .userId(user.getId())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .countryCode(user.getCountryCode())
                .phoneVerified(user.isPhoneVerified())
                .build();
    }
}
