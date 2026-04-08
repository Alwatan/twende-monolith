package tz.co.twende.loyalty.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.loyalty.dto.request.UpdateLoyaltyRuleRequest;
import tz.co.twende.loyalty.dto.response.FreeRideOfferDto;
import tz.co.twende.loyalty.dto.response.LoyaltyRuleDto;
import tz.co.twende.loyalty.dto.response.RiderProgressDto;
import tz.co.twende.loyalty.entity.FreeRideOffer;
import tz.co.twende.loyalty.entity.LoyaltyRule;
import tz.co.twende.loyalty.entity.RiderProgress;
import tz.co.twende.loyalty.mapper.LoyaltyMapper;
import tz.co.twende.loyalty.service.LoyaltyService;

@ExtendWith(MockitoExtension.class)
class LoyaltyControllerTest {

    @Mock private LoyaltyService loyaltyService;
    @Mock private LoyaltyMapper loyaltyMapper;

    @InjectMocks private LoyaltyController loyaltyController;

    @Test
    void givenRiderId_whenGetProgress_thenProgressReturned() {
        UUID userId = UUID.randomUUID();
        List<RiderProgress> progressList = List.of(new RiderProgress(userId, "TZ", "BAJAJ"));
        List<RiderProgressDto> dtoList = List.of(new RiderProgressDto());

        when(loyaltyService.getProgress(userId)).thenReturn(progressList);
        when(loyaltyMapper.toRiderProgressDtoList(progressList)).thenReturn(dtoList);

        ResponseEntity<ApiResponse<List<RiderProgressDto>>> response =
                loyaltyController.getProgress(userId);

        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    void givenRiderId_whenGetOffers_thenOffersReturned() {
        UUID userId = UUID.randomUUID();
        List<FreeRideOffer> offers = List.of(new FreeRideOffer());
        List<FreeRideOfferDto> dtoList = List.of(new FreeRideOfferDto());

        when(loyaltyService.getAvailableOffers(userId)).thenReturn(offers);
        when(loyaltyMapper.toFreeRideOfferDtoList(offers)).thenReturn(dtoList);

        ResponseEntity<ApiResponse<List<FreeRideOfferDto>>> response =
                loyaltyController.getOffers(userId);

        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    void givenCountryCode_whenGetRules_thenRulesReturned() {
        LoyaltyRule rule = new LoyaltyRule();
        rule.setCountryCode("TZ");
        List<LoyaltyRuleDto> dtoList = List.of(new LoyaltyRuleDto());

        when(loyaltyService.getRules("TZ")).thenReturn(List.of(rule));
        when(loyaltyMapper.toLoyaltyRuleDtoList(any())).thenReturn(dtoList);

        ResponseEntity<ApiResponse<List<LoyaltyRuleDto>>> response =
                loyaltyController.getRules("TZ");

        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    void givenAdminRole_whenUpdateRule_thenRuleUpdated() {
        UUID ruleId = UUID.randomUUID();
        UpdateLoyaltyRuleRequest request = new UpdateLoyaltyRuleRequest();
        request.setRequiredRides(25);

        LoyaltyRule updatedRule = new LoyaltyRule();
        updatedRule.setRequiredRides(25);
        LoyaltyRuleDto dto = new LoyaltyRuleDto();
        dto.setRequiredRides(25);

        when(loyaltyService.updateRule(eq(ruleId), any())).thenReturn(updatedRule);
        when(loyaltyMapper.toLoyaltyRuleDto(updatedRule)).thenReturn(dto);

        ResponseEntity<ApiResponse<LoyaltyRuleDto>> response =
                loyaltyController.updateRule("ADMIN", ruleId, request);

        assertTrue(response.getBody().isSuccess());
        assertEquals(25, response.getBody().getData().getRequiredRides());
    }

    @Test
    void givenNonAdminRole_whenUpdateRule_thenUnauthorizedThrown() {
        UUID ruleId = UUID.randomUUID();
        UpdateLoyaltyRuleRequest request = new UpdateLoyaltyRuleRequest();

        assertThrows(
                UnauthorizedException.class,
                () -> loyaltyController.updateRule("RIDER", ruleId, request));
    }
}
