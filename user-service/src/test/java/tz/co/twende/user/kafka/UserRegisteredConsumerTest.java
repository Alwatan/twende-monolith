package tz.co.twende.user.kafka;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.UserRole;
import tz.co.twende.common.event.user.UserRegisteredEvent;
import tz.co.twende.user.entity.UserProfile;
import tz.co.twende.user.repository.UserProfileRepository;

@ExtendWith(MockitoExtension.class)
class UserRegisteredConsumerTest {

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private EntityManager entityManager;

    @InjectMocks private UserRegisteredConsumer consumer;

    @Test
    void givenRiderRegistration_whenConsumed_thenProfileCreated() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = createEvent(userId, "Jane Doe", UserRole.RIDER);

        when(userProfileRepository.existsById(userId)).thenReturn(false);

        consumer.onUserRegistered(event);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(entityManager).merge(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(userId);
        assertThat(captor.getValue().getFullName()).isEqualTo("Jane Doe");
        assertThat(captor.getValue().getCountryCode()).isEqualTo("TZ");
        assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    void givenDriverRegistration_whenConsumed_thenIgnored() {
        UserRegisteredEvent event = createEvent(UUID.randomUUID(), "John Driver", UserRole.DRIVER);

        consumer.onUserRegistered(event);

        verify(entityManager, never()).merge(any());
    }

    @Test
    void givenDuplicateRegistration_whenConsumed_thenNoError() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = createEvent(userId, "Jane Doe", UserRole.RIDER);

        when(userProfileRepository.existsById(userId)).thenReturn(true);

        consumer.onUserRegistered(event);

        verify(entityManager, never()).merge(any());
    }

    @Test
    void givenAdminRegistration_whenConsumed_thenIgnored() {
        UserRegisteredEvent event = createEvent(UUID.randomUUID(), "Admin User", UserRole.ADMIN);

        consumer.onUserRegistered(event);

        verify(entityManager, never()).merge(any());
    }

    private UserRegisteredEvent createEvent(UUID userId, String fullName, UserRole role) {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUserId(userId);
        event.setFullName(fullName);
        event.setPhoneNumber("+255712345678");
        event.setRole(role);
        event.setCountryCode("TZ");
        return event;
    }
}
