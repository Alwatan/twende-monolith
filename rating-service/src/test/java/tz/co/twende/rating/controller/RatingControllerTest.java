package tz.co.twende.rating.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.rating.dto.*;
import tz.co.twende.rating.service.RatingService;

@ExtendWith(MockitoExtension.class)
class RatingControllerTest {

    @Mock private RatingService ratingService;

    @InjectMocks private RatingController ratingController;

    @Test
    void givenValidRequest_whenSubmitRating_thenReturn201() {
        UUID userId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();
        SubmitRatingRequest request = new SubmitRatingRequest(rideId, (short) 5, "Great!");

        RatingDto expected =
                RatingDto.builder()
                        .id(UUID.randomUUID())
                        .rideId(rideId)
                        .raterUserId(userId)
                        .ratedUserId(UUID.randomUUID())
                        .raterRole("RIDER")
                        .score((short) 5)
                        .comment("Great!")
                        .createdAt(Instant.now())
                        .build();
        when(ratingService.submitRating(userId, "RIDER", request)).thenReturn(expected);

        ResponseEntity<ApiResponse<RatingDto>> response =
                ratingController.submitRating(userId, "RIDER", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getScore()).isEqualTo((short) 5);
    }

    @Test
    void givenDriverId_whenGetDriverRating_thenReturn200() {
        UUID driverId = UUID.randomUUID();
        Map<Short, Long> dist = new LinkedHashMap<>();
        dist.put((short) 5, 10L);
        RatingSummaryDto summary =
                RatingSummaryDto.builder()
                        .userId(driverId)
                        .averageScore(4.8)
                        .totalRatings(10)
                        .distribution(dist)
                        .build();
        when(ratingService.getDriverRatingSummary(driverId)).thenReturn(summary);

        ResponseEntity<ApiResponse<RatingSummaryDto>> response =
                ratingController.getDriverRating(driverId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getAverageScore()).isEqualTo(4.8);
    }

    @Test
    void givenDriverRole_whenGetMyRating_thenReturnDriverSummary() {
        UUID userId = UUID.randomUUID();
        RatingSummaryDto summary =
                RatingSummaryDto.builder()
                        .userId(userId)
                        .averageScore(4.5)
                        .totalRatings(20)
                        .build();
        when(ratingService.getDriverRatingSummary(userId)).thenReturn(summary);

        ResponseEntity<ApiResponse<RatingSummaryDto>> response =
                ratingController.getMyRating(userId, "DRIVER");

        assertThat(response.getBody().getData().getAverageScore()).isEqualTo(4.5);
        verify(ratingService).getDriverRatingSummary(userId);
        verify(ratingService, never()).getRiderRatingSummary(any());
    }

    @Test
    void givenRiderRole_whenGetMyRating_thenReturnRiderSummary() {
        UUID userId = UUID.randomUUID();
        RatingSummaryDto summary =
                RatingSummaryDto.builder().userId(userId).averageScore(4.9).totalRatings(5).build();
        when(ratingService.getRiderRatingSummary(userId)).thenReturn(summary);

        ResponseEntity<ApiResponse<RatingSummaryDto>> response =
                ratingController.getMyRating(userId, "RIDER");

        assertThat(response.getBody().getData().getAverageScore()).isEqualTo(4.9);
        verify(ratingService).getRiderRatingSummary(userId);
    }
}
