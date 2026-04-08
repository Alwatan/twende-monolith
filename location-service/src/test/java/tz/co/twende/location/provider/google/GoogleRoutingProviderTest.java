package tz.co.twende.location.provider.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.Route;

@ExtendWith(MockitoExtension.class)
class GoogleRoutingProviderTest {

    @Mock private GoogleMapsClient googleMapsClient;

    @InjectMocks private GoogleRoutingProvider provider;

    @Test
    void givenProvider_whenGetId_thenReturnGoogle() {
        assertThat(provider.getId()).isEqualTo("google");
    }

    @Test
    void givenOriginAndDestination_whenGetRoute_thenDelegateToClient() {
        LatLng origin = new LatLng(new BigDecimal("-6.792"), new BigDecimal("39.208"));
        LatLng destination = new LatLng(new BigDecimal("-6.800"), new BigDecimal("39.250"));
        Route expected = Route.builder().distanceMetres(5000).durationSeconds(600).build();

        when(googleMapsClient.directions(-6.792, 39.208, -6.800, 39.250)).thenReturn(expected);

        Route result = provider.getRoute(origin, destination);

        assertThat(result.getDistanceMetres()).isEqualTo(5000);
        assertThat(result.getDurationSeconds()).isEqualTo(600);
    }

    @Test
    void givenValidRoute_whenGetEtaMinutes_thenCalculateFromDuration() {
        LatLng origin = new LatLng(new BigDecimal("-6.792"), new BigDecimal("39.208"));
        LatLng destination = new LatLng(new BigDecimal("-6.800"), new BigDecimal("39.250"));
        Route route = Route.builder().distanceMetres(5000).durationSeconds(660).build();

        when(googleMapsClient.directions(-6.792, 39.208, -6.800, 39.250)).thenReturn(route);

        int eta = provider.getEtaMinutes(origin, destination);

        assertThat(eta).isEqualTo(11); // ceil(660/60)
    }

    @Test
    void givenNullRoute_whenGetEtaMinutes_thenReturnMinusOne() {
        LatLng origin = new LatLng(new BigDecimal("-6.792"), new BigDecimal("39.208"));
        LatLng destination = new LatLng(new BigDecimal("-6.800"), new BigDecimal("39.250"));

        when(googleMapsClient.directions(-6.792, 39.208, -6.800, 39.250)).thenReturn(null);

        int eta = provider.getEtaMinutes(origin, destination);

        assertThat(eta).isEqualTo(-1);
    }
}
