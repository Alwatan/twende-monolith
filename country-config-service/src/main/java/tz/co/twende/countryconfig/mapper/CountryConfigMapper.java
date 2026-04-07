package tz.co.twende.countryconfig.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import tz.co.twende.countryconfig.dto.*;
import tz.co.twende.countryconfig.entity.*;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CountryConfigMapper {

    /**
     * Maps CountryConfig entity to DTO. Note: the full CountryConfigDto includes nested lists
     * (vehicleTypes, cities, paymentMethods, requiredDocuments) which must be assembled manually in
     * the service layer since they come from separate repositories.
     */
    @Mapping(target = "vehicleTypes", ignore = true)
    @Mapping(target = "cities", ignore = true)
    @Mapping(target = "paymentMethods", ignore = true)
    @Mapping(target = "requiredDocuments", ignore = true)
    CountryConfigDto toDto(CountryConfig entity);

    VehicleTypeConfigDto toDto(VehicleTypeConfig entity);

    List<VehicleTypeConfigDto> toVehicleTypeDtoList(List<VehicleTypeConfig> entities);

    OperatingCityDto toDto(OperatingCity entity);

    List<OperatingCityDto> toCityDtoList(List<OperatingCity> entities);

    PaymentMethodConfigDto toDto(PaymentMethodConfig entity);

    List<PaymentMethodConfigDto> toPaymentMethodDtoList(List<PaymentMethodConfig> entities);

    RequiredDriverDocumentDto toDto(RequiredDriverDocument entity);

    List<RequiredDriverDocumentDto> toDocumentDtoList(List<RequiredDriverDocument> entities);

    @Mapping(target = "code", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateCountryConfigRequest request, @MappingTarget CountryConfig entity);
}
