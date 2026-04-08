package tz.co.twende.user.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.common.response.PagedResponse;
import tz.co.twende.user.dto.DestinationSuggestionsDto;
import tz.co.twende.user.dto.RideHistoryResponse;
import tz.co.twende.user.dto.UpdateProfileRequest;
import tz.co.twende.user.dto.UserProfileDto;
import tz.co.twende.user.service.DestinationSuggestionService;
import tz.co.twende.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private UserService userService;
    @Mock private DestinationSuggestionService destinationSuggestionService;
    @InjectMocks private UserController userController;

    @Test
    void givenValidUserId_whenGetProfile_thenReturnsProfile() {
        UUID userId = UUID.randomUUID();
        UserProfileDto dto =
                UserProfileDto.builder().id(userId).fullName("Jane Doe").countryCode("TZ").build();
        when(userService.getProfile(userId)).thenReturn(dto);

        ResponseEntity<ApiResponse<UserProfileDto>> response = userController.getProfile(userId);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Jane Doe", response.getBody().getData().getFullName());
    }

    @Test
    void givenValidUpdate_whenUpdateProfile_thenReturnsUpdated() {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request =
                UpdateProfileRequest.builder().fullName("Jane Updated").build();
        UserProfileDto dto =
                UserProfileDto.builder()
                        .id(userId)
                        .fullName("Jane Updated")
                        .countryCode("TZ")
                        .build();
        when(userService.updateProfile(eq(userId), any())).thenReturn(dto);

        ResponseEntity<ApiResponse<UserProfileDto>> response =
                userController.updateProfile(userId, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Jane Updated", response.getBody().getData().getFullName());
    }

    @Test
    void givenAdminRole_whenGetUserById_thenReturnsProfile() {
        UUID userId = UUID.randomUUID();
        UserProfileDto dto =
                UserProfileDto.builder().id(userId).fullName("Jane Doe").countryCode("TZ").build();
        when(userService.getProfile(userId)).thenReturn(dto);

        ResponseEntity<ApiResponse<UserProfileDto>> response =
                userController.getUserById("ADMIN", userId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Jane Doe", response.getBody().getData().getFullName());
    }

    @Test
    void givenNonAdminRole_whenGetUserById_thenReturns403() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<ApiResponse<UserProfileDto>> response =
                userController.getUserById("RIDER", userId);

        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Access denied. Admin role required.", response.getBody().getMessage());
        verify(userService, never()).getProfile(any());
    }

    @Test
    void givenValidUserId_whenGetRideHistory_thenReturnsPagedResponse() {
        UUID userId = UUID.randomUUID();
        PagedResponse<RideHistoryResponse> pagedResponse =
                PagedResponse.<RideHistoryResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(20)
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build();
        when(userService.getRideHistory(userId, 0, 20)).thenReturn(pagedResponse);

        ResponseEntity<ApiResponse<PagedResponse<RideHistoryResponse>>> response =
                userController.getRideHistory(userId, 0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(0, response.getBody().getData().getTotalElements());
        verify(userService).getRideHistory(userId, 0, 20);
    }

    @Test
    void givenValidCoordinates_whenGetSuggestions_thenReturnsSuggestions() {
        UUID userId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");
        DestinationSuggestionsDto suggestionsDto =
                DestinationSuggestionsDto.builder().frequent(List.of()).recent(List.of()).build();
        when(destinationSuggestionService.getSuggestions(userId, lat, lng))
                .thenReturn(suggestionsDto);

        ResponseEntity<ApiResponse<DestinationSuggestionsDto>> response =
                userController.getSuggestions(userId, lat, lng);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getData().getFrequent());
        verify(destinationSuggestionService).getSuggestions(userId, lat, lng);
    }
}
