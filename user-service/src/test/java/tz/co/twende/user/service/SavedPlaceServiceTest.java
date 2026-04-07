package tz.co.twende.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.user.dto.CreateSavedPlaceRequest;
import tz.co.twende.user.dto.SavedPlaceDto;
import tz.co.twende.user.entity.SavedPlace;
import tz.co.twende.user.mapper.UserMapper;
import tz.co.twende.user.repository.SavedPlaceRepository;

@ExtendWith(MockitoExtension.class)
class SavedPlaceServiceTest {

    @Mock private SavedPlaceRepository savedPlaceRepository;
    @Mock private UserMapper userMapper;

    @InjectMocks private SavedPlaceService savedPlaceService;

    @Test
    void givenUserWithPlaces_whenGetPlaces_thenReturnList() {
        UUID userId = UUID.randomUUID();
        SavedPlace place = createPlace(userId, "Home", -6.79, 39.21);
        SavedPlaceDto dto =
                SavedPlaceDto.builder().id(place.getId()).userId(userId).label("Home").build();

        when(savedPlaceRepository.findByUserId(userId)).thenReturn(List.of(place));
        when(userMapper.toSavedPlaceDtoList(any())).thenReturn(List.of(dto));

        List<SavedPlaceDto> result = savedPlaceService.getPlaces(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo("Home");
    }

    @Test
    void givenValidRequest_whenCreatePlace_thenSaveAndReturnDto() {
        UUID userId = UUID.randomUUID();
        CreateSavedPlaceRequest request =
                CreateSavedPlaceRequest.builder()
                        .label("Work")
                        .address("CBD, Dar es Salaam")
                        .latitude(-6.81)
                        .longitude(39.27)
                        .build();
        SavedPlaceDto dto = SavedPlaceDto.builder().userId(userId).label("Work").build();

        when(savedPlaceRepository.save(any(SavedPlace.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(SavedPlace.class))).thenReturn(dto);

        SavedPlaceDto result = savedPlaceService.createPlace(userId, "TZ", request);

        assertThat(result.getLabel()).isEqualTo("Work");

        ArgumentCaptor<SavedPlace> captor = ArgumentCaptor.forClass(SavedPlace.class);
        verify(savedPlaceRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getCountryCode()).isEqualTo("TZ");
        assertThat(captor.getValue().getAddress()).isEqualTo("CBD, Dar es Salaam");
    }

    @Test
    void givenExistingPlace_whenDeleteByOwner_thenDeleteSuccessfully() {
        UUID placeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(savedPlaceRepository.deleteByIdAndUserId(placeId, userId)).thenReturn(1);

        assertThatCode(() -> savedPlaceService.deletePlace(placeId, userId))
                .doesNotThrowAnyException();
        verify(savedPlaceRepository).deleteByIdAndUserId(placeId, userId);
    }

    @Test
    void givenNonExistingPlace_whenDelete_thenThrowNotFound() {
        UUID placeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(savedPlaceRepository.deleteByIdAndUserId(placeId, userId)).thenReturn(0);

        assertThatThrownBy(() -> savedPlaceService.deletePlace(placeId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(placeId.toString());
    }

    @Test
    void givenPlaceOwnedByOtherUser_whenDeleteByWrongUser_thenThrowNotFound() {
        UUID placeId = UUID.randomUUID();
        UUID wrongUserId = UUID.randomUUID();

        when(savedPlaceRepository.deleteByIdAndUserId(placeId, wrongUserId)).thenReturn(0);

        assertThatThrownBy(() -> savedPlaceService.deletePlace(placeId, wrongUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private SavedPlace createPlace(UUID userId, String label, double lat, double lng) {
        SavedPlace place = new SavedPlace();
        place.setId(UUID.randomUUID());
        place.setUserId(userId);
        place.setCountryCode("TZ");
        place.setLabel(label);
        place.setAddress("Test Address");
        place.setLatitude(lat);
        place.setLongitude(lng);
        place.setCreatedAt(Instant.now());
        place.setUpdatedAt(Instant.now());
        return place;
    }
}
