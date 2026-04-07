package tz.co.twende.auth.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.auth.config.AuthProperties;
import tz.co.twende.auth.dto.LinkPhoneRequestDto;
import tz.co.twende.auth.dto.LinkSocialRequestDto;
import tz.co.twende.auth.dto.SocialLoginRequestDto;
import tz.co.twende.auth.dto.TokenResponseDto;
import tz.co.twende.auth.entity.AuthUser;
import tz.co.twende.auth.entity.AuthUserLink;
import tz.co.twende.auth.repository.AuthUserLinkRepository;
import tz.co.twende.auth.repository.AuthUserRepository;
import tz.co.twende.common.enums.UserRole;
import tz.co.twende.common.event.user.UserRegisteredEvent;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.common.exception.ResourceNotFoundException;

@Service
@Slf4j
@Transactional
public class SocialLoginService {

    private static final String USER_REGISTERED_TOPIC = "twende.users.registered";

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String APPLE_JWKS_URI = "https://appleid.apple.com/auth/keys";

    private final AuthUserRepository authUserRepository;
    private final AuthUserLinkRepository authUserLinkRepository;
    private final TokenService tokenService;
    private final OtpService otpService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuthProperties authProperties;

    private final Map<String, JwtDecoder> decoderCache = new ConcurrentHashMap<>();

    public SocialLoginService(
            AuthUserRepository authUserRepository,
            AuthUserLinkRepository authUserLinkRepository,
            TokenService tokenService,
            OtpService otpService,
            KafkaTemplate<String, Object> kafkaTemplate,
            AuthProperties authProperties) {
        this.authUserRepository = authUserRepository;
        this.authUserLinkRepository = authUserLinkRepository;
        this.tokenService = tokenService;
        this.otpService = otpService;
        this.kafkaTemplate = kafkaTemplate;
        this.authProperties = authProperties;
    }

    /**
     * Authenticate a user via a social provider (Google or Apple) ID token. Verifies the token,
     * extracts user info, and either finds an existing user or creates a new one. Account linking
     * by email is automatic.
     *
     * @param request the social login request containing provider and idToken
     * @return TokenResponseDto with access/refresh tokens
     */
    public TokenResponseDto socialLogin(SocialLoginRequestDto request) {
        String provider = request.getProvider().toUpperCase();
        Jwt jwt = verifyIdToken(provider, request.getIdToken());

        String providerUserId = jwt.getSubject();
        String email = extractEmail(jwt);
        String fullName = extractFullName(jwt, provider);
        String profilePhotoUrl = extractProfilePhotoUrl(jwt, provider);

        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required from social provider");
        }

        validateAudience(jwt, provider);

        // Check if this social account is already linked
        var existingLink =
                authUserLinkRepository.findByProviderAndProviderUserId(provider, providerUserId);

        if (existingLink.isPresent()) {
            AuthUser user =
                    authUserRepository
                            .findById(existingLink.get().getUserId())
                            .orElseThrow(
                                    () -> new ResourceNotFoundException("Linked user not found"));
            boolean phoneRequired = user.getPhoneNumber() == null;
            TokenResponseDto tokens = tokenService.issueTokens(user, false);
            tokens.setPhoneRequired(phoneRequired);
            log.info(
                    "Social login (existing link): provider={}, userId={}", provider, user.getId());
            return tokens;
        }

        // Try to find existing user by email (auto-link)
        var existingUser = authUserRepository.findByEmail(email);
        boolean isNewUser;
        AuthUser user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
            isNewUser = false;
        } else {
            // Create new user
            String countryCode = request.getCountryCode() != null ? request.getCountryCode() : "TZ";
            user = new AuthUser();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setProfilePhotoUrl(profilePhotoUrl);
            user.setAuthProvider(provider);
            user.setCountryCode(countryCode);
            user.setRole(UserRole.RIDER);
            user.setActive(true);
            user.setPhoneVerified(false);
            user = authUserRepository.save(user);
            isNewUser = true;

            publishUserRegisteredEvent(user);
        }

        // Create the auth link
        createAuthLink(user.getId(), provider, providerUserId, email, user.getCountryCode());

        boolean phoneRequired = user.getPhoneNumber() == null;
        TokenResponseDto tokens = tokenService.issueTokens(user, isNewUser);
        tokens.setPhoneRequired(phoneRequired);

        log.info(
                "Social login: provider={}, userId={}, isNewUser={}, phoneRequired={}",
                provider,
                user.getId(),
                isNewUser,
                phoneRequired);

        return tokens;
    }

    /**
     * Link a phone number to an existing social-only account. Verifies the OTP and associates the
     * phone number with the user.
     *
     * @param userId the authenticated user's ID
     * @param request the link phone request containing phoneNumber, otp, and countryCode
     */
    public void linkPhone(UUID userId, LinkPhoneRequestDto request) {
        AuthUser user =
                authUserRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Verify OTP
        otpService.verifyOtp(request.getPhoneNumber(), request.getOtp());

        // Check phone not already taken by another user
        var existingPhone = authUserRepository.findByPhoneNumber(request.getPhoneNumber());
        if (existingPhone.isPresent() && !existingPhone.get().getId().equals(userId)) {
            throw new ConflictException("Phone number is already linked to another account");
        }

        user.setPhoneNumber(request.getPhoneNumber());
        user.setPhoneVerified(true);
        if (request.getCountryCode() != null) {
            user.setCountryCode(request.getCountryCode());
        }
        authUserRepository.save(user);

        log.info("Phone linked: userId={}, phone={}", userId, request.getPhoneNumber());
    }

    /**
     * Link a social account to an existing phone-authenticated user. Verifies the social ID token
     * and creates an auth link.
     *
     * @param userId the authenticated user's ID
     * @param request the link social request containing provider and idToken
     */
    public void linkSocial(UUID userId, LinkSocialRequestDto request) {
        AuthUser user =
                authUserRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String provider = request.getProvider().toUpperCase();
        Jwt jwt = verifyIdToken(provider, request.getIdToken());

        String providerUserId = jwt.getSubject();
        String email = extractEmail(jwt);

        validateAudience(jwt, provider);

        // Check if this social account is already linked to another user
        var existingLink =
                authUserLinkRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (existingLink.isPresent() && !existingLink.get().getUserId().equals(userId)) {
            throw new ConflictException(
                    "This " + provider + " account is already linked to another user");
        }
        if (existingLink.isPresent()) {
            throw new ConflictException("This " + provider + " account is already linked");
        }

        // Check if user already has a link for this provider
        if (authUserLinkRepository.existsByUserIdAndProvider(userId, provider)) {
            throw new ConflictException("You already have a " + provider + " account linked");
        }

        createAuthLink(userId, provider, providerUserId, email, user.getCountryCode());

        // Update user email/photo if not set
        if (user.getEmail() == null && email != null) {
            user.setEmail(email);
        }
        String photoUrl = extractProfilePhotoUrl(jwt, provider);
        if (user.getProfilePhotoUrl() == null && photoUrl != null) {
            user.setProfilePhotoUrl(photoUrl);
        }
        String fullName = extractFullName(jwt, provider);
        if (user.getFullName() == null && fullName != null) {
            user.setFullName(fullName);
        }
        authUserRepository.save(user);

        log.info("Social linked: userId={}, provider={}", userId, provider);
    }

    /**
     * Verify a social provider ID token using the provider's JWKS endpoint.
     *
     * @param provider the provider name (GOOGLE or APPLE)
     * @param idToken the raw ID token string
     * @return the decoded and verified JWT
     */
    Jwt verifyIdToken(String provider, String idToken) {
        JwtDecoder decoder = getDecoder(provider);
        try {
            return decoder.decode(idToken);
        } catch (Exception e) {
            log.warn("Failed to verify {} ID token: {}", provider, e.getMessage());
            throw new BadRequestException("Invalid " + provider + " ID token");
        }
    }

    private JwtDecoder getDecoder(String provider) {
        return decoderCache.computeIfAbsent(
                provider,
                p -> {
                    String jwksUri =
                            switch (p) {
                                case "GOOGLE" -> GOOGLE_JWKS_URI;
                                case "APPLE" -> APPLE_JWKS_URI;
                                default ->
                                        throw new BadRequestException("Unsupported provider: " + p);
                            };
                    return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
                });
    }

    private void validateAudience(Jwt jwt, String provider) {
        var audiences = jwt.getAudience();
        if (audiences == null || audiences.isEmpty()) {
            throw new BadRequestException("ID token has no audience claim");
        }

        String expectedClientId =
                switch (provider) {
                    case "GOOGLE" -> authProperties.getGoogle().getClientId();
                    case "APPLE" -> authProperties.getApple().getClientId();
                    default -> throw new BadRequestException("Unsupported provider: " + provider);
                };

        if (expectedClientId != null
                && !expectedClientId.isBlank()
                && !audiences.contains(expectedClientId)) {
            throw new BadRequestException("ID token audience does not match expected client ID");
        }
    }

    private String extractEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }

    private String extractFullName(Jwt jwt, String provider) {
        if ("GOOGLE".equals(provider)) {
            return jwt.getClaimAsString("name");
        }
        if ("APPLE".equals(provider)) {
            // Apple sends name only on first authorization
            String firstName = null;
            String lastName = null;
            try {
                firstName = jwt.getClaimAsString("given_name");
                lastName = jwt.getClaimAsString("family_name");
            } catch (Exception e) {
                // name claims not present
            }
            if (firstName != null && lastName != null) {
                return firstName + " " + lastName;
            }
            if (firstName != null) {
                return firstName;
            }
            return null;
        }
        return null;
    }

    private String extractProfilePhotoUrl(Jwt jwt, String provider) {
        if ("GOOGLE".equals(provider)) {
            return jwt.getClaimAsString("picture");
        }
        return null;
    }

    private void createAuthLink(
            UUID userId, String provider, String providerUserId, String email, String countryCode) {
        AuthUserLink link = new AuthUserLink();
        link.setUserId(userId);
        link.setProvider(provider);
        link.setProviderUserId(providerUserId);
        link.setEmail(email);
        link.setCountryCode(countryCode);
        authUserLinkRepository.save(link);
    }

    private void publishUserRegisteredEvent(AuthUser user) {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setEventType("USER_REGISTERED");
        event.setCountryCode(user.getCountryCode());
        event.setUserId(user.getId());
        event.setFullName(user.getFullName());
        event.setPhoneNumber(user.getPhoneNumber());
        event.setEmail(user.getEmail());
        event.setProfilePhotoUrl(user.getProfilePhotoUrl());
        event.setAuthProvider(user.getAuthProvider());
        event.setRole(user.getRole());

        kafkaTemplate.send(USER_REGISTERED_TOPIC, user.getId().toString(), event);
        log.info(
                "Published UserRegisteredEvent for social user: userId={}, provider={}",
                user.getId(),
                user.getAuthProvider());
    }
}
