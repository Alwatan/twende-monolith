package tz.co.twende.user.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.user.dto.CreateSavedPlaceRequest;
import tz.co.twende.user.dto.SavedPlaceDto;
import tz.co.twende.user.service.SavedPlaceService;

@ExtendWith(MockitoExtension.class)
class SavedPlaceControllerTest {

    @Mock private SavedPlaceService savedPlaceService;
    @InjectMocks private SavedPlaceController savedPlaceController;

    @Test
    void givenUserId_whenListPlaces_thenReturnsPlaces() {
        UUID userId = UUID.randomUUID();
        SavedPlaceDto place1 =
                SavedPlaceDto.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .label("Home")
                        .address("Mikocheni, Dar es Salaam")
                        .latitude(-6.79)
                        .longitude(39.21)
                        .build();
        SavedPlaceDto place2 =
                SavedPlaceDto.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .label("Work")
                        .address("CBD, Dar es Salaam")
                        .latitude(-6.81)
                        .longitude(39.27)
                        .build();
        when(savedPlaceService.getPlaces(userId)).thenReturn(List.of(place1, place2));

        ResponseEntity<ApiResponse<List<SavedPlaceDto>>> response =
                savedPlaceController.getPlaces(userId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(2, response.getBody().getData().size());
        assertEquals("Home", response.getBody().getData().get(0).getLabel());
        assertEquals("Work", response.getBody().getData().get(1).getLabel());
        verify(savedPlaceService).getPlaces(userId);
    }

    @Test
    void givenValidRequest_whenCreatePlace_thenReturns201() {
        UUID userId = UUID.randomUUID();
        String countryCode = "TZ";
        CreateSavedPlaceRequest request =
                CreateSavedPlaceRequest.builder()
                        .label("Home")
                        .address("Mikocheni, Dar es Salaam")
                        .latitude(-6.79)
                        .longitude(39.21)
                        .build();
        SavedPlaceDto created =
                SavedPlaceDto.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .label("Home")
                        .address("Mikocheni, Dar es Salaam")
                        .latitude(-6.79)
                        .longitude(39.21)
                        .build();
        when(savedPlaceService.createPlace(eq(userId), eq(countryCode), any())).thenReturn(created);

        ResponseEntity<ApiResponse<SavedPlaceDto>> response =
                savedPlaceController.createPlace(userId, countryCode, request);

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Home", response.getBody().getData().getLabel());
        assertEquals(userId, response.getBody().getData().getUserId());
        verify(savedPlaceService).createPlace(eq(userId), eq(countryCode), any());
    }

    @Test
    void givenPlaceId_whenDeletePlace_thenReturns200() {
        UUID userId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        doNothing().when(savedPlaceService).deletePlace(placeId, userId);

        ResponseEntity<ApiResponse<Void>> response =
                savedPlaceController.deletePlace(userId, placeId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Saved place deleted successfully", response.getBody().getMessage());
        verify(savedPlaceService).deletePlace(placeId, userId);
    }
}
