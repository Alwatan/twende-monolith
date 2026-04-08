package tz.co.twende.loyalty.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.loyalty.dto.response.FreeRideOfferDto;
import tz.co.twende.loyalty.entity.FreeRideOffer;
import tz.co.twende.loyalty.mapper.LoyaltyMapper;
import tz.co.twende.loyalty.service.LoyaltyService;
import tz.co.twende.loyalty.service.OfferRedemptionService;

@ExtendWith(MockitoExtension.class)
class LoyaltyInternalControllerTest {

    @Mock private LoyaltyService loyaltyService;
    @Mock private OfferRedemptionService offerRedemptionService;
    @Mock private LoyaltyMapper loyaltyMapper;

    @InjectMocks private LoyaltyInternalController loyaltyInternalController;

    @Test
    void givenApplicableOffer_whenFindApplicable_thenOfferReturned() {
        UUID riderId = UUID.randomUUID();
        FreeRideOffer offer = new FreeRideOffer();
        offer.setRiderId(riderId);
        FreeRideOfferDto dto = new FreeRideOfferDto();

        when(loyaltyService.findApplicableOffer(riderId, "TZ", "BAJAJ", new BigDecimal("3.00")))
                .thenReturn(offer);
        when(loyaltyMapper.toFreeRideOfferDto(offer)).thenReturn(dto);

        ResponseEntity<ApiResponse<FreeRideOfferDto>> response =
                loyaltyInternalController.findApplicableOffer(
                        riderId, "TZ", "BAJAJ", new BigDecimal("3.00"));

        assertTrue(response.getBody().isSuccess());
    }

    @Test
    void givenNoApplicableOffer_whenFindApplicable_thenNotFoundThrown() {
        UUID riderId = UUID.randomUUID();

        when(loyaltyService.findApplicableOffer(riderId, "TZ", "BAJAJ", new BigDecimal("3.00")))
                .thenReturn(null);

        assertThrows(
                ResourceNotFoundException.class,
                () ->
                        loyaltyInternalController.findApplicableOffer(
                                riderId, "TZ", "BAJAJ", new BigDecimal("3.00")));
    }

    @Test
    void givenValidOfferId_whenRedeem_thenSuccessReturned() {
        UUID offerId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> response =
                loyaltyInternalController.redeemOffer(offerId, rideId);

        verify(offerRedemptionService).redeemOffer(offerId, rideId);
        assertTrue(response.getBody().isSuccess());
    }
}
