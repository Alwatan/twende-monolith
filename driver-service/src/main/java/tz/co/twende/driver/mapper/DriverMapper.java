package tz.co.twende.driver.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import tz.co.twende.driver.dto.response.*;
import tz.co.twende.driver.entity.DriverDocument;
import tz.co.twende.driver.entity.DriverProfile;
import tz.co.twende.driver.entity.DriverVehicle;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DriverMapper {

    DriverProfileDto toProfileDto(DriverProfile entity);

    @Mapping(source = "active", target = "active")
    DriverVehicleDto toVehicleDto(DriverVehicle entity);

    DriverDocumentDto toDocumentDto(DriverDocument entity);

    DriverSummaryDto toSummaryDto(DriverProfile entity);

    List<DriverVehicleDto> toVehicleDtoList(List<DriverVehicle> entities);

    List<DriverDocumentDto> toDocumentDtoList(List<DriverDocument> entities);

    @Mapping(source = "id", target = "vehicleId")
    ActiveVehicleDto toActiveVehicleDto(DriverVehicle entity);
}
