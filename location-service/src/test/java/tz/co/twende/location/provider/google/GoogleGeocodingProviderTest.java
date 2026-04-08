package tz.co.twende.location.provider.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.location.dto.GeocodingResult;
import tz.co.twende.location.dto.LatLng;

@ExtendWith(MockitoExtension.class)
class GoogleGeocodingProviderTest {

    @Mock private GoogleMapsClient googleMapsClient;

    @InjectMocks private GoogleGeocodingProvider provider;

    @Test
    void givenProvider_whenGetId_thenReturnGoogle() {
        assertThat(provider.getId()).isEqualTo("google");
    }

    @Test
    void givenAddress_whenGeocode_thenDelegateToClient() {
        GeocodingResult expected =
                GeocodingResult.builder()
                        .latitude(new BigDecimal("-6.792"))
                        .longitude(new BigDecimal("39.208"))
                        .formattedAddress("Dar es Salaam")
                        .build();
        when(googleMapsClient.geocode("Dar es Salaam")).thenReturn(expected);

        GeocodingResult result = provider.geocode("Dar es Salaam", null);

        assertThat(result.getFormattedAddress()).isEqualTo("Dar es Salaam");
    }

    @Test
    void givenLatLng_whenReverseGeocode_thenDelegateToClient() {
        LatLng point = new LatLng(new BigDecimal("-6.792"), new BigDecimal("39.208"));
        GeocodingResult expected =
                GeocodingResult.builder().formattedAddress("Some address").build();
        when(googleMapsClient.reverseGeocode(-6.792, 39.208)).thenReturn(expected);

        GeocodingResult result = provider.reverseGeocode(point);

        assertThat(result.getFormattedAddress()).isEqualTo("Some address");
    }
}
