package tz.co.twende.notification.mapper;

import org.mapstruct.Mapper;
import tz.co.twende.notification.dto.response.FcmTokenDto;
import tz.co.twende.notification.entity.FcmToken;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    FcmTokenDto toFcmTokenDto(FcmToken entity);
}
