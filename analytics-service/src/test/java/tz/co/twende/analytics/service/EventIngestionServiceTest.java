package tz.co.twende.analytics.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;
import tz.co.twende.analytics.entity.AnalyticsEvent;
import tz.co.twende.analytics.repository.AnalyticsEventRepository;
import tz.co.twende.common.event.payment.PaymentCompletedEvent;
import tz.co.twende.common.event.rating.RatingSubmittedEvent;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.event.subscription.SubscriptionActivatedEvent;
import tz.co.twende.common.event.user.UserRegisteredEvent;

@ExtendWith(MockitoExtension.class)
class EventIngestionServiceTest {

    @Mock private AnalyticsEventRepository eventRepository;

    @Mock private JsonMapper jsonMapper;

    @InjectMocks private EventIngestionService eventIngestionService;

    @Test
    void givenRideCompletedEvent_whenIngest_thenActorIdIsDriverId() throws Exception {
        UUID driverId = UUID.randomUUID();
        RideCompletedEvent event = new RideCompletedEvent();
        event.setEventType("RIDE_COMPLETED");
        event.setCountryCode("TZ");
        event.setDriverId(driverId);
        event.setRiderId(UUID.randomUUID());
        event.setFinalFare(BigDecimal.valueOf(5000));
        event.setTimestamp(Instant.now());

        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");
        when(eventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        eventIngestionService.ingest(event);

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(eventRepository).save(captor.capture());

        AnalyticsEvent saved = captor.getValue();
        assertThat(saved.getActorId()).isEqualTo(driverId);
        assertThat(saved.getEventType()).isEqualTo("RIDE_COMPLETED");
        assertThat(saved.getCountryCode()).isEqualTo("TZ");
    }

    @Test
    void givenPaymentCompletedEvent_whenIngest_thenActorIdIsUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent();
        event.setEventType("PAYMENT_COMPLETED");
        event.setCountryCode("TZ");
        event.setUserId(userId);
        event.setTimestamp(Instant.now());

        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");
        when(eventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        eventIngestionService.ingest(event);

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getActorId()).isEqualTo(userId);
    }

    @Test
    void givenSubscriptionActivatedEvent_whenIngest_thenActorIdIsDriverId() throws Exception {
        UUID driverId = UUID.randomUUID();
        SubscriptionActivatedEvent event = new SubscriptionActivatedEvent();
        event.setEventType("SUBSCRIPTION_ACTIVATED");
        event.setCountryCode("TZ");
        event.setDriverId(driverId);
        event.setTimestamp(Instant.now());

        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");
        when(eventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        eventIngestionService.ingest(event);

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getActorId()).isEqualTo(driverId);
    }

    @Test
    void givenUserRegisteredEvent_whenIngest_thenActorIdIsUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setEventType("USER_REGISTERED");
        event.setCountryCode("TZ");
        event.setUserId(userId);
        event.setTimestamp(Instant.now());

        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");
        when(eventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        eventIngestionService.ingest(event);

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getActorId()).isEqualTo(userId);
    }

    @Test
    void givenRatingSubmittedEvent_whenIngest_thenActorIdIsRaterUserId() throws Exception {
        UUID raterUserId = UUID.randomUUID();
        RatingSubmittedEvent event = new RatingSubmittedEvent();
        event.setEventType("RATING_SUBMITTED");
        event.setCountryCode("TZ");
        event.setRaterUserId(raterUserId);
        event.setTimestamp(Instant.now());

        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");
        when(eventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        eventIngestionService.ingest(event);

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getActorId()).isEqualTo(raterUserId);
    }

    @Test
    void givenNullCountryCode_whenIngest_thenDefaultsToXX() throws Exception {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setEventType("RIDE_COMPLETED");
        event.setCountryCode(null);
        event.setDriverId(UUID.randomUUID());
        event.setTimestamp(Instant.now());

        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");
        when(eventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        eventIngestionService.ingest(event);

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getCountryCode()).isEqualTo("XX");
    }

    @Test
    void givenNullTimestamp_whenIngest_thenUsesCurrentTime() throws Exception {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setEventType("RIDE_COMPLETED");
        event.setCountryCode("TZ");
        event.setDriverId(UUID.randomUUID());
        event.setTimestamp(null);

        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");
        when(eventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Instant before = Instant.now();
        eventIngestionService.ingest(event);

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getOccurredAt()).isAfterOrEqualTo(before);
    }
}
