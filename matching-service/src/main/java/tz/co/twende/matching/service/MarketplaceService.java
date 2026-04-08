package tz.co.twende.matching.service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tz.co.twende.common.event.ride.BookingRequestedEvent;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.matching.dto.MarketplaceBookingDto;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceService {

    private static final String MARKETPLACE_KEY_PREFIX = "marketplace:";
    private static final String BOOKING_DATA_KEY_PREFIX = "marketplace:booking:";
    private static final String BOOKING_DRIVER_KEY_PREFIX = "marketplace:booking:driver:";
    private static final Duration BOOKING_TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;

    public void addBookingToMarketplace(BookingRequestedEvent event) {
        String marketplaceKey =
                MARKETPLACE_KEY_PREFIX
                        + event.getCountryCode()
                        + ":"
                        + event.getServiceCategory()
                        + ":"
                        + event.getVehicleType();

        String bookingId = event.getBookingId().toString();
        double score =
                event.getScheduledPickupAt() != null
                        ? (double) event.getScheduledPickupAt().toEpochMilli()
                        : (double) Instant.now().toEpochMilli();

        // Add to sorted set (scored by scheduledPickupAt)
        stringRedisTemplate.opsForZSet().add(marketplaceKey, bookingId, score);
        stringRedisTemplate.expire(marketplaceKey, BOOKING_TTL);

        // Store booking data as hash
        String dataKey = BOOKING_DATA_KEY_PREFIX + bookingId;
        Map<String, String> bookingData = new HashMap<>();
        bookingData.put("bookingId", bookingId);
        bookingData.put("serviceCategory", event.getServiceCategory());
        bookingData.put("vehicleType", event.getVehicleType());
        bookingData.put(
                "qualityTier", event.getQualityTier() != null ? event.getQualityTier() : "");
        bookingData.put(
                "scheduledPickupAt",
                event.getScheduledPickupAt() != null
                        ? event.getScheduledPickupAt().toString()
                        : "");
        bookingData.put(
                "pickupAddress", event.getPickupAddress() != null ? event.getPickupAddress() : "");
        bookingData.put(
                "dropoffAddress",
                event.getDropoffAddress() != null ? event.getDropoffAddress() : "");
        bookingData.put(
                "estimatedFare",
                event.getEstimatedFare() != null ? event.getEstimatedFare().toPlainString() : "0");
        bookingData.put(
                "currencyCode", event.getCurrencyCode() != null ? event.getCurrencyCode() : "TZS");
        bookingData.put("riderId", event.getRiderId() != null ? event.getRiderId().toString() : "");
        bookingData.put("weightTier", event.getWeightTier() != null ? event.getWeightTier() : "");
        bookingData.put("driverProvidesLoading", String.valueOf(event.isDriverProvidesLoading()));
        stringRedisTemplate.opsForHash().putAll(dataKey, bookingData);
        stringRedisTemplate.expire(dataKey, BOOKING_TTL);

        log.info(
                "Added booking {} to marketplace: {} {}",
                bookingId,
                event.getServiceCategory(),
                event.getVehicleType());
    }

    public List<MarketplaceBookingDto> getAvailableBookings(
            String countryCode,
            String serviceCategory,
            String vehicleType,
            String qualityTier,
            Instant fromDate,
            Instant toDate) {

        String marketplaceKey =
                MARKETPLACE_KEY_PREFIX + countryCode + ":" + serviceCategory + ":" + vehicleType;

        double minScore =
                fromDate != null ? (double) fromDate.toEpochMilli() : Double.NEGATIVE_INFINITY;
        double maxScore =
                toDate != null ? (double) toDate.toEpochMilli() : Double.POSITIVE_INFINITY;

        Set<String> bookingIds =
                stringRedisTemplate.opsForZSet().rangeByScore(marketplaceKey, minScore, maxScore);

        if (bookingIds == null || bookingIds.isEmpty()) {
            return Collections.emptyList();
        }

        return bookingIds.stream()
                .map(this::loadBookingDto)
                .filter(Objects::nonNull)
                .filter(
                        b ->
                                qualityTier == null
                                        || qualityTier.isEmpty()
                                        || qualityTier.equals(b.getQualityTier()))
                .collect(Collectors.toList());
    }

    public void requestBooking(UUID bookingId, UUID driverId) {
        String dataKey = BOOKING_DATA_KEY_PREFIX + bookingId;
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(dataKey))) {
            throw new ResourceNotFoundException("Booking not found: " + bookingId);
        }

        String driverKey = BOOKING_DRIVER_KEY_PREFIX + bookingId;
        stringRedisTemplate.opsForSet().add(driverKey, driverId.toString());
        stringRedisTemplate.expire(driverKey, BOOKING_TTL);

        log.info("Driver {} requested booking {}", driverId, bookingId);
    }

    public void confirmDriver(UUID bookingId, UUID driverId) {
        String driverKey = BOOKING_DRIVER_KEY_PREFIX + bookingId;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(driverKey, driverId.toString());
        if (!Boolean.TRUE.equals(isMember)) {
            throw new BadRequestException("Driver " + driverId + " has not requested this booking");
        }

        removeFromMarketplace(bookingId);
        log.info("Driver {} confirmed for booking {}", driverId, bookingId);
    }

    public void removeFromMarketplace(UUID bookingId) {
        String bookingIdStr = bookingId.toString();
        String dataKey = BOOKING_DATA_KEY_PREFIX + bookingIdStr;

        // Read data to find marketplace key
        Map<Object, Object> data = stringRedisTemplate.opsForHash().entries(dataKey);
        if (!data.isEmpty()) {
            String countryCode = data.getOrDefault("countryCode", "TZ").toString();
            // We need serviceCategory and vehicleType to find the sorted set
            String serviceCategory = data.getOrDefault("serviceCategory", "").toString();
            String vehicleType = data.getOrDefault("vehicleType", "").toString();

            if (!serviceCategory.isEmpty() && !vehicleType.isEmpty()) {
                String marketplaceKey =
                        MARKETPLACE_KEY_PREFIX
                                + countryCode
                                + ":"
                                + serviceCategory
                                + ":"
                                + vehicleType;
                stringRedisTemplate.opsForZSet().remove(marketplaceKey, bookingIdStr);
            }
        }

        stringRedisTemplate.delete(dataKey);
        stringRedisTemplate.delete(BOOKING_DRIVER_KEY_PREFIX + bookingIdStr);

        log.info("Removed booking {} from marketplace", bookingId);
    }

    private MarketplaceBookingDto loadBookingDto(String bookingId) {
        String dataKey = BOOKING_DATA_KEY_PREFIX + bookingId;
        Map<Object, Object> data = stringRedisTemplate.opsForHash().entries(dataKey);
        if (data.isEmpty()) {
            return null;
        }

        String scheduledStr = data.getOrDefault("scheduledPickupAt", "").toString();
        Instant scheduledPickupAt = scheduledStr.isEmpty() ? null : Instant.parse(scheduledStr);

        String fareStr = data.getOrDefault("estimatedFare", "0").toString();
        java.math.BigDecimal estimatedFare = new java.math.BigDecimal(fareStr);

        return MarketplaceBookingDto.builder()
                .bookingId(UUID.fromString(bookingId))
                .serviceCategory(data.getOrDefault("serviceCategory", "").toString())
                .vehicleType(data.getOrDefault("vehicleType", "").toString())
                .qualityTier(data.getOrDefault("qualityTier", "").toString())
                .scheduledPickupAt(scheduledPickupAt)
                .pickupAddress(data.getOrDefault("pickupAddress", "").toString())
                .dropoffAddress(data.getOrDefault("dropoffAddress", "").toString())
                .estimatedFare(estimatedFare)
                .currencyCode(data.getOrDefault("currencyCode", "TZS").toString())
                .weightTier(data.getOrDefault("weightTier", "").toString())
                .driverProvidesLoading(
                        "true"
                                .equals(
                                        data.getOrDefault("driverProvidesLoading", "false")
                                                .toString()))
                .build();
    }
}
