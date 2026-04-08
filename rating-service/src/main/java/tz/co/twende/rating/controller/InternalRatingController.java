package tz.co.twende.rating.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tz.co.twende.rating.dto.DriverScoreDto;
import tz.co.twende.rating.service.RatingService;

@RestController
@RequestMapping("/internal/ratings")
@RequiredArgsConstructor
public class InternalRatingController {

    private final RatingService ratingService;

    @GetMapping("/driver/{driverId}/score")
    public ResponseEntity<DriverScoreDto> getDriverScore(@PathVariable UUID driverId) {
        return ResponseEntity.ok(ratingService.getDriverScore(driverId));
    }
}
