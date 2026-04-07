package tz.co.twende.common.event.user;

import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.UserRole;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent extends KafkaEvent {
    private UUID userId;
    private String fullName;
    private String phoneNumber;
    private UserRole role;
    private String email;
    private String profilePhotoUrl;
    private String authProvider;
}
