package tz.co.twende.location.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import tz.co.twende.location.dto.GeocodingResult;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.PlaceResult;
import tz.co.twende.location.service.AutocompleteService;
import tz.co.twende.location.service.GeocodingService;

@ExtendWith(MockitoExtension.class)
class GeocodingControllerTest {

    @Mock private GeocodingService geocodingService;
    @Mock private AutocompleteService autocompleteService;

    @InjectMocks private GeocodingController geocodingController;

    @Test
    void givenValidAddress_whenGeocode_thenReturnResult() {
        UUID cityId = UUID.randomUUID();
        GeocodingResult expected =
                GeocodingResult.builder()
                        .latitude(new BigDecimal("-6.7924"))
                        .longitude(new BigDecimal("39.2083"))
                        .formattedAddress("Dar es Salaam, Tanzania")
                        .build();
        when(geocodingService.geocode("Dar es Salaam", null, cityId)).thenReturn(expected);

        ResponseEntity<ApiResponse<GeocodingResult>> response =
                geocodingController.geocode("Dar es Salaam", cityId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getFormattedAddress())
                .isEqualTo("Dar es Salaam, Tanzania");
    }

    @Test
    void givenValidCoordinates_whenReverseGeocode_thenReturnResult() {
        UUID cityId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");
        GeocodingResult expected =
                GeocodingResult.builder()
                        .latitude(lat)
                        .longitude(lng)
                        .formattedAddress("Some address")
                        .build();
        when(geocodingService.reverseGeocode(any(LatLng.class), eq(cityId))).thenReturn(expected);

        ResponseEntity<ApiResponse<GeocodingResult>> response =
                geocodingController.reverseGeocode(lat, lng, cityId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getData().getFormattedAddress()).isEqualTo("Some address");
    }

    @Test
    void givenValidQuery_whenAutocomplete_thenReturnResults() {
        UUID cityId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("-6.7924");
        BigDecimal lng = new BigDecimal("39.2083");
        List<PlaceResult> expected =
                List.of(PlaceResult.builder().placeId("place1").description("Place 1").build());
        when(autocompleteService.search(eq("test"), any(LatLng.class), eq("TZ"), eq(cityId), eq(5)))
                .thenReturn(expected);

        ResponseEntity<ApiResponse<List<PlaceResult>>> response =
                geocodingController.autocomplete("test", lat, lng, cityId, "TZ", 5);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    void givenNoBias_whenAutocomplete_thenPassNullBias() {
        UUID cityId = UUID.randomUUID();
        List<PlaceResult> expected = List.of();
        when(autocompleteService.search(eq("test"), isNull(), eq("TZ"), eq(cityId), eq(5)))
                .thenReturn(expected);

        ResponseEntity<ApiResponse<List<PlaceResult>>> response =
                geocodingController.autocomplete("test", null, null, cityId, "TZ", 5);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(autocompleteService).search("test", null, "TZ", cityId, 5);
    }
}
