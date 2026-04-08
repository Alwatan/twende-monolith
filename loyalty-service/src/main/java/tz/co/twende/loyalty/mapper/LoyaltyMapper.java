package tz.co.twende.loyalty.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import tz.co.twende.loyalty.dto.response.FreeRideOfferDto;
import tz.co.twende.loyalty.dto.response.LoyaltyRuleDto;
import tz.co.twende.loyalty.dto.response.RiderProgressDto;
import tz.co.twende.loyalty.entity.FreeRideOffer;
import tz.co.twende.loyalty.entity.LoyaltyRule;
import tz.co.twende.loyalty.entity.RiderProgress;

@Mapper(componentModel = "spring")
public interface LoyaltyMapper {

    RiderProgressDto toRiderProgressDto(RiderProgress entity);

    List<RiderProgressDto> toRiderProgressDtoList(List<RiderProgress> entities);

    FreeRideOfferDto toFreeRideOfferDto(FreeRideOffer entity);

    List<FreeRideOfferDto> toFreeRideOfferDtoList(List<FreeRideOffer> entities);

    LoyaltyRuleDto toLoyaltyRuleDto(LoyaltyRule entity);

    List<LoyaltyRuleDto> toLoyaltyRuleDtoList(List<LoyaltyRule> entities);
}
