package tz.co.twende.matching.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_RIDES_REQUESTED = "twende.rides.requested";
    public static final String TOPIC_RIDES_FARE_BOOSTED = "twende.rides.fare-boosted";
    public static final String TOPIC_RIDES_CANCELLED = "twende.rides.cancelled";
    public static final String TOPIC_RIDES_OFFER_ACCEPTED = "twende.rides.offer-accepted";
    public static final String TOPIC_DRIVERS_REJECTED_RIDE = "twende.drivers.rejected-ride";
    public static final String TOPIC_DRIVERS_OFFER_NOTIFICATION =
            "twende.drivers.offer-notification";
    public static final String TOPIC_RIDES_NO_DRIVER_FOUND = "twende.rides.no-driver-found";
    public static final String TOPIC_DRIVERS_STATUS_UPDATED = "twende.drivers.status-updated";

    public static final String TOPIC_BOOKING_REQUESTED = "twende.rides.booking-requested";
    public static final String TOPIC_BOOKING_COMPLETED = "twende.rides.booking-completed";
}
