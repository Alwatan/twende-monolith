package tz.co.twende.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.user.dto.DestinationSuggestionsDto;
import tz.co.twende.user.entity.UserDestinationStats;
import tz.co.twende.user.repository.UserDestinationStatsRepository;
import tz.co.twende.user.repository.UserProfileRepository;

@ExtendWith(MockitoExtension.class)
class DestinationSuggestionServiceTest {

    @Mock private UserDestinationStatsRepository destinationStatsRepository;
    @Mock private UserProfileRepository userProfileRepository;

    @InjectMocks private DestinationSuggestionService service;

    @Test
    void givenNewDestination_whenRideCompleted_thenCreateStats() {
        UUID riderId = UUID.randomUUID();
        RideCompletedEvent event = createEvent(riderId, "-6.7924", "39.2083", "Mikocheni");

        when(userProfileRepository.existsById(riderId)).thenReturn(true);
        when(destinationStatsRepository.findByUserIdAndCityIdAndDestinationLatAndDestinationLng(
                        eq(riderId), any(), any(), any()))
                .thenReturn(Optional.empty());

        service.onRideCompleted(event);

        ArgumentCaptor<UserDestinationStats> captor =
                ArgumentCaptor.forClass(UserDestinationStats.class);
        verify(destinationStatsRepository).save(captor.capture());
        UserDestinationStats saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(riderId);
        assertThat(saved.getTripCount()).isEqualTo(1);
        assertThat(saved.getDestinationAddress()).isEqualTo("Mikocheni");
        assertThat(saved.getDestinationLat()).isEqualByComparingTo(new BigDecimal("-6.7924"));
        assertThat(saved.getDestinationLng()).isEqualByComparingTo(new BigDecimal("39.2083"));
    }

    @Test
    void givenExistingDestination_whenRideCompleted_thenIncrementTripCount() {
        UUID riderId = UUID.randomUUID();
        RideCompletedEvent event = createEvent(riderId, "-6.7924", "39.2083", "Mikocheni");

        UserDestinationStats existing = new UserDestinationStats();
        existing.setUserId(riderId);
        existing.setTripCount(3);
        existing.setLastTripAt(Instant.now().minusSeconds(86400));
        existing.setDestinationAddress("Mikocheni Old");

        when(userProfileRepository.existsById(riderId)).thenReturn(true);
        when(destinationStatsRepository.findByUserIdAndCityIdAndDestinationLatAndDestinationLng(
                        eq(riderId), any(), any(), any()))
                .thenReturn(Optional.of(existing));

        service.onRideCompleted(event);

        ArgumentCaptor<UserDestinationStats> captor =
                ArgumentCaptor.forClass(UserDestinationStats.class);
        verify(destinationStatsRepository).save(captor.capture());
        UserDestinationStats saved = captor.getValue();

        assertThat(saved.getTripCount()).isEqualTo(4);
        assertThat(saved.getDestinationAddress()).isEqualTo("Mikocheni");
    }

    @Test
    void givenMissingRiderId_whenRideCompleted_thenSkip() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        // riderId is null

        service.onRideCompleted(event);

        verify(destinationStatsRepository, never()).save(any());
    }

    @Test
    void givenMissingDropoffCoordinates_whenRideCompleted_thenSkip() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        // dropoff lat/lng null

        service.onRideCompleted(event);

        verify(destinationStatsRepository, never()).save(any());
    }

    @Test
    void givenNonExistentUser_whenRideCompleted_thenSkip() {
        UUID riderId = UUID.randomUUID();
        RideCompletedEvent event = createEvent(riderId, "-6.7924", "39.2083", "Mikocheni");

        when(userProfileRepository.existsById(riderId)).thenReturn(false);

        service.onRideCompleted(event);

        verify(destinationStatsRepository, never()).save(any());
    }

    @Test
    void givenDestinationStats_whenGetSuggestions_thenReturnFrequentDestinations() {
        UUID userId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");

        UserDestinationStats stat1 = createStats("Mikocheni", 5, "-6.7900", "39.2100");
        UserDestinationStats stat2 = createStats("CBD", 3, "-6.8100", "39.2700");

        when(destinationStatsRepository.findByUserIdAndCityIdOrderByTripCountDesc(
                        eq(userId), any(), any()))
                .thenReturn(List.of(stat1, stat2));

        DestinationSuggestionsDto result = service.getSuggestions(userId, lat, lng);

        assertThat(result.getFrequent()).hasSize(2);
        assertThat(result.getFrequent().get(0).getAddress()).isEqualTo("Mikocheni");
        assertThat(result.getFrequent().get(0).getVisitCount()).isEqualTo(5);
        assertThat(result.getFrequent().get(1).getAddress()).isEqualTo("CBD");
        assertThat(result.getRecent()).isEmpty();
    }

    @Test
    void givenNoStats_whenGetSuggestions_thenReturnEmptyLists() {
        UUID userId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");

        when(destinationStatsRepository.findByUserIdAndCityIdOrderByTripCountDesc(
                        eq(userId), any(), any()))
                .thenReturn(List.of());

        DestinationSuggestionsDto result = service.getSuggestions(userId, lat, lng);

        assertThat(result.getFrequent()).isEmpty();
        assertThat(result.getRecent()).isEmpty();
    }

    @Test
    void givenCoordinates_whenRoundTo4Dp_thenRoundedCorrectly() {
        BigDecimal value = new BigDecimal("-6.79243567");
        BigDecimal rounded = DestinationSuggestionService.roundTo4Dp(value);
        assertThat(rounded).isEqualByComparingTo(new BigDecimal("-6.7924"));
    }

    @Test
    void givenEventWithCityId_whenRideCompleted_thenUsesEventCityId() {
        UUID riderId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        RideCompletedEvent event = createEvent(riderId, "-6.7924", "39.2083", "Mikocheni");
        event.setCityId(cityId);

        when(userProfileRepository.existsById(riderId)).thenReturn(true);
        when(destinationStatsRepository.findByUserIdAndCityIdAndDestinationLatAndDestinationLng(
                        eq(riderId), eq(cityId), any(), any()))
                .thenReturn(Optional.empty());

        service.onRideCompleted(event);

        ArgumentCaptor<UserDestinationStats> captor =
                ArgumentCaptor.forClass(UserDestinationStats.class);
        verify(destinationStatsRepository).save(captor.capture());
        assertThat(captor.getValue().getCityId()).isEqualTo(cityId);
    }

    @Test
    void givenEventWithNullAddress_whenUpdateExisting_thenAddressNotOverwritten() {
        UUID riderId = UUID.randomUUID();
        RideCompletedEvent event = createEvent(riderId, "-6.7924", "39.2083", null);

        UserDestinationStats existing = new UserDestinationStats();
        existing.setUserId(riderId);
        existing.setTripCount(2);
        existing.setLastTripAt(Instant.now().minusSeconds(3600));
        existing.setDestinationAddress("Original Address");

        when(userProfileRepository.existsById(riderId)).thenReturn(true);
        when(destinationStatsRepository.findByUserIdAndCityIdAndDestinationLatAndDestinationLng(
                        eq(riderId), any(), any(), any()))
                .thenReturn(Optional.of(existing));

        service.onRideCompleted(event);

        ArgumentCaptor<UserDestinationStats> captor =
                ArgumentCaptor.forClass(UserDestinationStats.class);
        verify(destinationStatsRepository).save(captor.capture());
        assertThat(captor.getValue().getDestinationAddress()).isEqualTo("Original Address");
        assertThat(captor.getValue().getTripCount()).isEqualTo(3);
    }

    private RideCompletedEvent createEvent(UUID riderId, String lat, String lng, String address) {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(riderId);
        event.setDriverId(UUID.randomUUID());
        event.setDropoffLat(new BigDecimal(lat));
        event.setDropoffLng(new BigDecimal(lng));
        event.setDropoffAddress(address);
        event.setCountryCode("TZ");
        event.setCompletedAt(Instant.now());
        event.setFinalFare(new BigDecimal("5000"));
        return event;
    }

    private UserDestinationStats createStats(
            String address, int tripCount, String lat, String lng) {
        UserDestinationStats stats = new UserDestinationStats();
        stats.setId(UUID.randomUUID());
        stats.setDestinationAddress(address);
        stats.setTripCount(tripCount);
        stats.setDestinationLat(new BigDecimal(lat));
        stats.setDestinationLng(new BigDecimal(lng));
        stats.setLastTripAt(Instant.now());
        return stats;
    }
}
