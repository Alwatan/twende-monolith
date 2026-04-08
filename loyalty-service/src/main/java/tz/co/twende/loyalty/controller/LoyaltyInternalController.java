package tz.co.twende.loyalty.controller;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.loyalty.dto.response.FreeRideOfferDto;
import tz.co.twende.loyalty.entity.FreeRideOffer;
import tz.co.twende.loyalty.mapper.LoyaltyMapper;
import tz.co.twende.loyalty.service.LoyaltyService;
import tz.co.twende.loyalty.service.OfferRedemptionService;

@RestController
@RequestMapping("/internal/loyalty")
@RequiredArgsConstructor
public class LoyaltyInternalController {

    private final LoyaltyService loyaltyService;
    private final OfferRedemptionService offerRedemptionService;
    private final LoyaltyMapper loyaltyMapper;

    @GetMapping("/offers/applicable")
    public ResponseEntity<ApiResponse<FreeRideOfferDto>> findApplicableOffer(
            @RequestParam UUID riderId,
            @RequestParam String countryCode,
            @RequestParam String vehicleType,
            @RequestParam BigDecimal distanceKm) {
        FreeRideOffer offer =
                loyaltyService.findApplicableOffer(riderId, countryCode, vehicleType, distanceKm);
        if (offer == null) {
            throw new ResourceNotFoundException("No applicable free ride offer found");
        }
        return ResponseEntity.ok(ApiResponse.ok(loyaltyMapper.toFreeRideOfferDto(offer)));
    }

    @PostMapping("/offers/{id}/redeem")
    public ResponseEntity<ApiResponse<Void>> redeemOffer(
            @PathVariable UUID id, @RequestParam UUID rideId) {
        offerRedemptionService.redeemOffer(id, rideId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Offer redeemed successfully"));
    }
}
