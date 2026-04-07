package tz.co.twende.user.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import tz.co.twende.user.dto.SavedPlaceDto;
import tz.co.twende.user.dto.UserProfileDto;
import tz.co.twende.user.entity.SavedPlace;
import tz.co.twende.user.entity.UserProfile;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserProfileDto toDto(UserProfile entity);

    SavedPlaceDto toDto(SavedPlace entity);

    List<SavedPlaceDto> toSavedPlaceDtoList(List<SavedPlace> entities);
}
