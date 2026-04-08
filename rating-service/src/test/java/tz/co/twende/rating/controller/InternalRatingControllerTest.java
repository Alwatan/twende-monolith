package tz.co.twende.rating.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tz.co.twende.rating.dto.DriverScoreDto;
import tz.co.twende.rating.service.RatingService;

@ExtendWith(MockitoExtension.class)
class InternalRatingControllerTest {

    @Mock private RatingService ratingService;

    @InjectMocks private InternalRatingController internalController;

    @Test
    void givenDriverId_whenGetScore_thenReturnPlainDto() {
        UUID driverId = UUID.randomUUID();
        DriverScoreDto expected =
                DriverScoreDto.builder().driverId(driverId).average(4.7).count(312).build();
        when(ratingService.getDriverScore(driverId)).thenReturn(expected);

        ResponseEntity<DriverScoreDto> response = internalController.getDriverScore(driverId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAverage()).isEqualTo(4.7);
        assertThat(response.getBody().getCount()).isEqualTo(312);
    }

    @Test
    void givenNoRatingsForDriver_whenGetScore_thenReturnZeroScore() {
        UUID driverId = UUID.randomUUID();
        DriverScoreDto expected =
                DriverScoreDto.builder().driverId(driverId).average(0.0).count(0).build();
        when(ratingService.getDriverScore(driverId)).thenReturn(expected);

        ResponseEntity<DriverScoreDto> response = internalController.getDriverScore(driverId);

        assertThat(response.getBody().getAverage()).isEqualTo(0.0);
        assertThat(response.getBody().getCount()).isZero();
    }
}
