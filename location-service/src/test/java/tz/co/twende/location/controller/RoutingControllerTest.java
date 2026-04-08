package tz.co.twende.location.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.location.dto.EtaRequest;
import tz.co.twende.location.dto.EtaResponse;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.Route;
import tz.co.twende.location.dto.RouteRequest;
import tz.co.twende.location.service.RoutingService;

@ExtendWith(MockitoExtension.class)
class RoutingControllerTest {

    @Mock private RoutingService routingService;

    @InjectMocks private RoutingController routingController;

    @Test
    void givenValidRouteRequest_whenGetRoute_thenReturnRoute() {
        UUID cityId = UUID.randomUUID();
        RouteRequest request = new RouteRequest();
        request.setOriginLat(new BigDecimal("-6.792"));
        request.setOriginLng(new BigDecimal("39.208"));
        request.setDestinationLat(new BigDecimal("-6.800"));
        request.setDestinationLng(new BigDecimal("39.250"));
        request.setCityId(cityId);

        Route expected = Route.builder().distanceMetres(5000).durationSeconds(600).build();
        when(routingService.getRoute(any(LatLng.class), any(LatLng.class), eq(cityId)))
                .thenReturn(expected);

        ResponseEntity<ApiResponse<Route>> response = routingController.getRoute(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getData().getDistanceMetres()).isEqualTo(5000);
        assertThat(response.getBody().getData().getDurationSeconds()).isEqualTo(600);
    }

    @Test
    void givenValidEtaRequest_whenGetEta_thenReturnEtaResponse() {
        UUID cityId = UUID.randomUUID();
        EtaRequest request = new EtaRequest();
        request.setOriginLat(new BigDecimal("-6.792"));
        request.setOriginLng(new BigDecimal("39.208"));
        request.setDestinationLat(new BigDecimal("-6.800"));
        request.setDestinationLng(new BigDecimal("39.250"));
        request.setCityId(cityId);

        Route route = Route.builder().distanceMetres(5000).durationSeconds(660).build();
        when(routingService.getRoute(any(LatLng.class), any(LatLng.class), eq(cityId)))
                .thenReturn(route);

        ResponseEntity<ApiResponse<EtaResponse>> response = routingController.getEta(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getData().getEtaMinutes()).isEqualTo(11);
        assertThat(response.getBody().getData().getDistanceMetres()).isEqualTo(5000);
    }

    @Test
    void givenNullRoute_whenGetEta_thenReturnMinusOneEta() {
        UUID cityId = UUID.randomUUID();
        EtaRequest request = new EtaRequest();
        request.setOriginLat(new BigDecimal("-6.792"));
        request.setOriginLng(new BigDecimal("39.208"));
        request.setDestinationLat(new BigDecimal("-6.800"));
        request.setDestinationLng(new BigDecimal("39.250"));
        request.setCityId(cityId);

        when(routingService.getRoute(any(LatLng.class), any(LatLng.class), eq(cityId)))
                .thenReturn(null);

        ResponseEntity<ApiResponse<EtaResponse>> response = routingController.getEta(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getData().getEtaMinutes()).isEqualTo(-1);
        assertThat(response.getBody().getData().getDistanceMetres()).isEqualTo(0);
    }
}
