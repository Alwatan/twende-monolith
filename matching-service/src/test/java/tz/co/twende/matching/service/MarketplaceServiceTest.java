package tz.co.twende.matching.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import tz.co.twende.common.event.ride.BookingRequestedEvent;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.matching.dto.MarketplaceBookingDto;

@ExtendWith(MockitoExtension.class)
class MarketplaceServiceTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ZSetOperations<String, String> zSetOps;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private SetOperations<String, String> setOps;

    @InjectMocks private MarketplaceService marketplaceService;

    @Test
    void givenBookingRequestedEvent_whenAddToMarketplace_thenStoredInRedis() {
        BookingRequestedEvent event = new BookingRequestedEvent();
        event.setBookingId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setServiceCategory("CHARTER");
        event.setVehicleType("MINIBUS_STANDARD");
        event.setQualityTier("STANDARD");
        event.setScheduledPickupAt(Instant.now().plus(2, ChronoUnit.DAYS));
        event.setPickupAddress("Kariakoo");
        event.setDropoffAddress("Mlimani");
        event.setEstimatedFare(new BigDecimal("50000"));
        event.setCurrencyCode("TZS");
        event.setCountryCode("TZ");

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);

        marketplaceService.addBookingToMarketplace(event);

        verify(zSetOps)
                .add(eq("marketplace:TZ:CHARTER:MINIBUS_STANDARD"), anyString(), anyDouble());
        verify(hashOps).putAll(anyString(), anyMap());
        verify(stringRedisTemplate, times(2)).expire(anyString(), any(Duration.class));
    }

    @Test
    void givenBookingsInRedis_whenGetAvailableBookings_thenReturnList() {
        UUID bookingId = UUID.randomUUID();
        String bookingIdStr = bookingId.toString();

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(Set.of(bookingIdStr));

        when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);
        Map<Object, Object> bookingData = new HashMap<>();
        bookingData.put("bookingId", bookingIdStr);
        bookingData.put("serviceCategory", "CHARTER");
        bookingData.put("vehicleType", "MINIBUS_STANDARD");
        bookingData.put("qualityTier", "STANDARD");
        bookingData.put("scheduledPickupAt", Instant.now().plus(1, ChronoUnit.DAYS).toString());
        bookingData.put("pickupAddress", "Kariakoo");
        bookingData.put("dropoffAddress", "Mlimani");
        bookingData.put("estimatedFare", "50000");
        bookingData.put("currencyCode", "TZS");
        when(hashOps.entries("marketplace:booking:" + bookingIdStr)).thenReturn(bookingData);

        List<MarketplaceBookingDto> result =
                marketplaceService.getAvailableBookings(
                        "TZ", "CHARTER", "MINIBUS_STANDARD", "STANDARD", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBookingId()).isEqualTo(bookingId);
        assertThat(result.get(0).getServiceCategory()).isEqualTo("CHARTER");
    }

    @Test
    void givenBookingsWithQualityFilter_whenGetAvailableBookings_thenFilteredByTier() {
        UUID bookingId = UUID.randomUUID();
        String bookingIdStr = bookingId.toString();

        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(Set.of(bookingIdStr));

        when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);
        Map<Object, Object> bookingData = new HashMap<>();
        bookingData.put("bookingId", bookingIdStr);
        bookingData.put("serviceCategory", "CHARTER");
        bookingData.put("vehicleType", "MINIBUS_STANDARD");
        bookingData.put("qualityTier", "STANDARD");
        bookingData.put("scheduledPickupAt", "");
        bookingData.put("pickupAddress", "Kariakoo");
        bookingData.put("dropoffAddress", "Mlimani");
        bookingData.put("estimatedFare", "50000");
        bookingData.put("currencyCode", "TZS");
        when(hashOps.entries("marketplace:booking:" + bookingIdStr)).thenReturn(bookingData);

        // Filter for LUXURY should exclude STANDARD bookings
        List<MarketplaceBookingDto> result =
                marketplaceService.getAvailableBookings(
                        "TZ", "CHARTER", "MINIBUS_STANDARD", "LUXURY", null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void givenValidBooking_whenRequestBooking_thenDriverAddedToSet() {
        UUID bookingId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        when(stringRedisTemplate.hasKey("marketplace:booking:" + bookingId)).thenReturn(true);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);

        marketplaceService.requestBooking(bookingId, driverId);

        verify(setOps).add(eq("marketplace:booking:driver:" + bookingId), eq(driverId.toString()));
    }

    @Test
    void givenNonExistentBooking_whenRequestBooking_thenThrowNotFound() {
        UUID bookingId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        when(stringRedisTemplate.hasKey("marketplace:booking:" + bookingId)).thenReturn(false);

        assertThatThrownBy(() -> marketplaceService.requestBooking(bookingId, driverId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void givenDriverNotRequested_whenConfirmDriver_thenThrowBadRequest() {
        UUID bookingId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("marketplace:booking:driver:" + bookingId, driverId.toString()))
                .thenReturn(false);

        assertThatThrownBy(() -> marketplaceService.confirmDriver(bookingId, driverId))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void givenEmptyMarketplace_whenGetAvailableBookings_thenReturnEmptyList() {
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(null);

        List<MarketplaceBookingDto> result =
                marketplaceService.getAvailableBookings(
                        "TZ", "CHARTER", "BUS_STANDARD", null, null, null);

        assertThat(result).isEmpty();
    }
}
