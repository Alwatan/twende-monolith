package tz.co.twende.location.provider.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.PlaceResult;

@ExtendWith(MockitoExtension.class)
class GoogleAutocompleteProviderTest {

    @Mock private GoogleMapsClient googleMapsClient;

    @InjectMocks private GoogleAutocompleteProvider provider;

    @Test
    void givenProvider_whenGetId_thenReturnGoogle() {
        assertThat(provider.getId()).isEqualTo("google");
    }

    @Test
    void givenQueryWithBias_whenSearch_thenDelegateToClient() {
        LatLng bias = new LatLng(new BigDecimal("-6.792"), new BigDecimal("39.208"));
        List<PlaceResult> expected =
                List.of(PlaceResult.builder().placeId("p1").description("Place 1").build());

        when(googleMapsClient.autocomplete("test", -6.792, 39.208, "TZ")).thenReturn(expected);

        List<PlaceResult> result = provider.search("test", bias, "TZ", 5);

        assertThat(result).hasSize(1);
    }

    @Test
    void givenNullBias_whenSearch_thenUseZeroCoordinates() {
        List<PlaceResult> expected =
                List.of(PlaceResult.builder().placeId("p1").description("Place 1").build());

        when(googleMapsClient.autocomplete("test", 0, 0, "TZ")).thenReturn(expected);

        List<PlaceResult> result = provider.search("test", null, "TZ", 5);

        assertThat(result).hasSize(1);
    }

    @Test
    void givenMoreResultsThanLimit_whenSearch_thenTruncateResults() {
        List<PlaceResult> manyResults =
                List.of(
                        PlaceResult.builder().placeId("p1").build(),
                        PlaceResult.builder().placeId("p2").build(),
                        PlaceResult.builder().placeId("p3").build(),
                        PlaceResult.builder().placeId("p4").build(),
                        PlaceResult.builder().placeId("p5").build());

        when(googleMapsClient.autocomplete("test", 0, 0, "TZ")).thenReturn(manyResults);

        List<PlaceResult> result = provider.search("test", null, "TZ", 3);

        assertThat(result).hasSize(3);
    }

    @Test
    void givenFewerResultsThanLimit_whenSearch_thenReturnAll() {
        List<PlaceResult> fewResults = List.of(PlaceResult.builder().placeId("p1").build());

        when(googleMapsClient.autocomplete("test", 0, 0, "TZ")).thenReturn(fewResults);

        List<PlaceResult> result = provider.search("test", null, "TZ", 5);

        assertThat(result).hasSize(1);
    }
}
