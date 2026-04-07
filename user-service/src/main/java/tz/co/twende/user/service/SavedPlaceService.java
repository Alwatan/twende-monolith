package tz.co.twende.user.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.user.dto.CreateSavedPlaceRequest;
import tz.co.twende.user.dto.SavedPlaceDto;
import tz.co.twende.user.entity.SavedPlace;
import tz.co.twende.user.mapper.UserMapper;
import tz.co.twende.user.repository.SavedPlaceRepository;

@Service
@RequiredArgsConstructor
public class SavedPlaceService {

    private final SavedPlaceRepository savedPlaceRepository;
    private final UserMapper userMapper;

    public List<SavedPlaceDto> getPlaces(UUID userId) {
        List<SavedPlace> places = savedPlaceRepository.findByUserId(userId);
        return userMapper.toSavedPlaceDtoList(places);
    }

    public SavedPlaceDto createPlace(
            UUID userId, String countryCode, CreateSavedPlaceRequest request) {
        SavedPlace place = new SavedPlace();
        place.setUserId(userId);
        place.setCountryCode(countryCode);
        place.setLabel(request.getLabel());
        place.setAddress(request.getAddress());
        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());

        SavedPlace saved = savedPlaceRepository.save(place);
        return userMapper.toDto(saved);
    }

    @Transactional
    public void deletePlace(UUID placeId, UUID userId) {
        int deleted = savedPlaceRepository.deleteByIdAndUserId(placeId, userId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Saved place not found with id: " + placeId);
        }
    }
}
