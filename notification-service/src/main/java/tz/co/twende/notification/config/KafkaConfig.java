package tz.co.twende.notification.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_RIDES_STATUS_UPDATED = "twende.rides.status-updated";
    public static final String TOPIC_RIDES_COMPLETED = "twende.rides.completed";
    public static final String TOPIC_DRIVERS_APPROVED = "twende.drivers.approved";
    public static final String TOPIC_DRIVERS_OFFER_NOTIFICATION =
            "twende.drivers.offer-notification";
    public static final String TOPIC_PAYMENTS_COMPLETED = "twende.payments.completed";
    public static final String TOPIC_SUBSCRIPTIONS_EXPIRED = "twende.subscriptions.expired";
    public static final String TOPIC_LOYALTY_FREE_RIDE_EARNED = "twende.loyalty.free-ride-earned";
    public static final String TOPIC_NOTIFICATIONS_SEND = "twende.notifications.send";
}
