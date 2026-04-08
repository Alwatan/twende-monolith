package tz.co.twende.notification.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.common.event.ride.RideStatusUpdatedEvent;
import tz.co.twende.notification.kafka.handler.RideStatusNotificationHandler;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@ExtendWith(MockitoExtension.class)
class RideStatusNotificationHandlerTest {

    @Mock private NotificationService notificationService;
    @Mock private TemplateResolver templateResolver;

    @InjectMocks private RideStatusNotificationHandler handler;

    @Test
    void givenRideAssignedEvent_whenProcessed_thenPushSentToRider() {
        RideStatusUpdatedEvent event = new RideStatusUpdatedEvent();
        event.setRideId(UUID.randomUUID());
        event.setPreviousStatus(RideStatus.REQUESTED);
        event.setNewStatus(RideStatus.DRIVER_ASSIGNED);
        event.setCountryCode("TZ");

        when(templateResolver.resolveTemplate(eq("ride.assigned.rider"), eq("sw-TZ"), any()))
                .thenReturn("Dereva John yuko njiani.");

        handler.handle(event);

        verify(notificationService)
                .sendPush(
                        eq("TZ"),
                        eq(event.getRideId()),
                        eq("Driver Assigned"),
                        eq("Dereva John yuko njiani."),
                        any(Map.class),
                        eq("ride.assigned.rider"));
    }

    @Test
    void givenDriverArrivedEvent_whenProcessed_thenDataPushSent() {
        RideStatusUpdatedEvent event = new RideStatusUpdatedEvent();
        event.setRideId(UUID.randomUUID());
        event.setPreviousStatus(RideStatus.DRIVER_ASSIGNED);
        event.setNewStatus(RideStatus.DRIVER_ARRIVED);
        event.setCountryCode("TZ");

        handler.handle(event);

        verify(notificationService)
                .sendPushData(eq("TZ"), eq(event.getRideId()), any(Map.class), eq("trip.otp"));
    }

    @Test
    void givenInProgressEvent_whenProcessed_thenNoPushSent() {
        RideStatusUpdatedEvent event = new RideStatusUpdatedEvent();
        event.setRideId(UUID.randomUUID());
        event.setPreviousStatus(RideStatus.DRIVER_ARRIVED);
        event.setNewStatus(RideStatus.IN_PROGRESS);
        event.setCountryCode("TZ");

        handler.handle(event);

        verify(notificationService, never()).sendPush(any(), any(), any(), any(), any(), any());
        verify(notificationService, never()).sendPushData(any(), any(), any(), any());
    }
}
