package com.twende.common.event.user;

import com.twende.common.enums.UserRole;
import com.twende.common.event.KafkaEvent;
import java.util.UUID;
import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent extends KafkaEvent {
    private UUID userId;
    private String fullName;
    private String phoneNumber;
    private UserRole role;
}
