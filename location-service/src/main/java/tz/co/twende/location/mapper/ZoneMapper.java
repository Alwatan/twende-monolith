package tz.co.twende.location.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tz.co.twende.location.dto.ZoneDto;
import tz.co.twende.location.entity.Zone;

@Mapper(componentModel = "spring")
public interface ZoneMapper {

    @Mapping(source = "active", target = "active")
    ZoneDto toDto(Zone zone);
}
