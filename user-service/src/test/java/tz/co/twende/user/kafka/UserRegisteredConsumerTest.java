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
        UserRegisteredEvent event =
                new UserRegisteredEvent(userId, "Jane Doe", "+255712345678", UserRole.RIDER);
        event.setCountryCode("TZ");

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
        UserRegisteredEvent event =
                new UserRegisteredEvent(
                        UUID.randomUUID(), "John Driver", "+255712345678", UserRole.DRIVER);
        event.setCountryCode("TZ");

        consumer.onUserRegistered(event);

        verify(entityManager, never()).merge(any());
    }

    @Test
    void givenDuplicateRegistration_whenConsumed_thenNoError() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event =
                new UserRegisteredEvent(userId, "Jane Doe", "+255712345678", UserRole.RIDER);
        event.setCountryCode("TZ");

        when(userProfileRepository.existsById(userId)).thenReturn(true);

        consumer.onUserRegistered(event);

        verify(entityManager, never()).merge(any());
    }

    @Test
    void givenAdminRegistration_whenConsumed_thenIgnored() {
        UserRegisteredEvent event =
                new UserRegisteredEvent(
                        UUID.randomUUID(), "Admin User", "+255712345678", UserRole.ADMIN);
        event.setCountryCode("TZ");

        consumer.onUserRegistered(event);

        verify(entityManager, never()).merge(any());
    }
}
