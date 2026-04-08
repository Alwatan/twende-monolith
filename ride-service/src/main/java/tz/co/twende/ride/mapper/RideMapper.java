package tz.co.twende.ride.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import tz.co.twende.ride.dto.response.RideHistorySummaryDto;
import tz.co.twende.ride.dto.response.RideResponse;
import tz.co.twende.ride.dto.response.RideStatusEventDto;
import tz.co.twende.ride.entity.Ride;
import tz.co.twende.ride.entity.RideStatusEvent;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RideMapper {

    @Mapping(source = "status", target = "status")
    RideResponse toResponse(Ride ride);

    List<RideResponse> toResponseList(List<Ride> rides);

    RideStatusEventDto toEventDto(RideStatusEvent event);

    List<RideStatusEventDto> toEventDtoList(List<RideStatusEvent> events);

    @Mapping(source = "id", target = "rideId")
    RideHistorySummaryDto toHistorySummary(Ride ride);

    List<RideHistorySummaryDto> toHistorySummaryList(List<Ride> rides);
}
