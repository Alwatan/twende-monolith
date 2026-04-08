package tz.co.twende.subscription.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import tz.co.twende.subscription.dto.SubscriptionDto;
import tz.co.twende.subscription.dto.SubscriptionPlanDto;
import tz.co.twende.subscription.entity.Subscription;
import tz.co.twende.subscription.entity.SubscriptionPlan;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SubscriptionMapper {

    SubscriptionPlanDto toPlanDto(SubscriptionPlan entity);

    List<SubscriptionPlanDto> toPlanDtoList(List<SubscriptionPlan> entities);

    SubscriptionDto toDto(Subscription entity);

    List<SubscriptionDto> toDtoList(List<Subscription> entities);
}
