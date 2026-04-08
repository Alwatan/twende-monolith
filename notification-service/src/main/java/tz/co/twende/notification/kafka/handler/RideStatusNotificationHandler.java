package tz.co.twende.notification.kafka.handler;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.common.event.ride.RideStatusUpdatedEvent;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideStatusNotificationHandler {

    private final NotificationService notificationService;
    private final TemplateResolver templateResolver;

    public void handle(RideStatusUpdatedEvent event) {
        log.debug(
                "Handling ride status update: {} -> {}",
                event.getPreviousStatus(),
                event.getNewStatus());

        if (event.getNewStatus() == RideStatus.DRIVER_ASSIGNED) {
            handleDriverAssigned(event);
        } else if (event.getNewStatus() == RideStatus.DRIVER_ARRIVED) {
            handleDriverArrived(event);
        }
    }

    private void handleDriverAssigned(RideStatusUpdatedEvent event) {
        String countryCode = event.getCountryCode();
        String locale = resolveLocale(countryCode);

        String body =
                templateResolver.resolveTemplate(
                        "ride.assigned.rider", locale, Map.of("driverName", "Driver", "eta", "5"));

        notificationService.sendPush(
                countryCode,
                event.getRideId(),
                "Driver Assigned",
                body,
                Map.of(
                        "type", "RIDE_STATUS",
                        "rideId", event.getRideId().toString(),
                        "status", event.getNewStatus().name()),
                "ride.assigned.rider");
    }

    private void handleDriverArrived(RideStatusUpdatedEvent event) {
        String countryCode = event.getCountryCode();

        notificationService.sendPushData(
                countryCode,
                event.getRideId(),
                Map.of(
                        "type", "DRIVER_ARRIVED",
                        "rideId", event.getRideId().toString(),
                        "status", event.getNewStatus().name()),
                "trip.otp");
    }

    private String resolveLocale(String countryCode) {
        if ("TZ".equals(countryCode)) return "sw-TZ";
        return "en";
    }
}
