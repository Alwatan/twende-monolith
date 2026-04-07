package tz.co.twende.user.kafka;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.enums.UserRole;
import tz.co.twende.common.event.user.UserRegisteredEvent;
import tz.co.twende.user.entity.UserProfile;
import tz.co.twende.user.repository.UserProfileRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredConsumer {

    private final UserProfileRepository userProfileRepository;
    private final EntityManager entityManager;

    @KafkaListener(topics = "twende.users.registered", groupId = "user-service-group")
    @Transactional
    public void onUserRegistered(UserRegisteredEvent event) {
        if (event.getRole() != UserRole.RIDER) {
            log.debug("Ignoring non-RIDER registration for user {}", event.getUserId());
            return;
        }

        if (userProfileRepository.existsById(event.getUserId())) {
            log.debug("User profile already exists for {}, skipping", event.getUserId());
            return;
        }

        UserProfile profile = new UserProfile();
        profile.setId(event.getUserId());
        profile.setFullName(event.getFullName());
        profile.setCountryCode(event.getCountryCode());
        profile.setIsActive(true);

        // Use merge() instead of save() because the ID is pre-set from the event.
        // save() calls persist() which fails with "Detached entity" for pre-set IDs
        // with @GeneratedValue on BaseEntity.
        entityManager.merge(profile);

        log.info("Created user profile for rider {}", event.getUserId());
    }
}
