package tz.co.twende.location.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.location.dto.EtaRequest;
import tz.co.twende.location.dto.EtaResponse;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.Route;
import tz.co.twende.location.dto.RouteRequest;
import tz.co.twende.location.service.RoutingService;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingService routingService;

    @PostMapping("/route")
    public ResponseEntity<ApiResponse<Route>> getRoute(@Valid @RequestBody RouteRequest request) {
        LatLng origin = new LatLng(request.getOriginLat(), request.getOriginLng());
        LatLng destination = new LatLng(request.getDestinationLat(), request.getDestinationLng());
        Route route = routingService.getRoute(origin, destination, request.getCityId());
        return ResponseEntity.ok(ApiResponse.ok(route));
    }

    @PostMapping("/eta")
    public ResponseEntity<ApiResponse<EtaResponse>> getEta(@Valid @RequestBody EtaRequest request) {
        LatLng origin = new LatLng(request.getOriginLat(), request.getOriginLng());
        LatLng destination = new LatLng(request.getDestinationLat(), request.getDestinationLng());
        Route route = routingService.getRoute(origin, destination, request.getCityId());
        EtaResponse eta =
                EtaResponse.builder()
                        .etaMinutes(
                                route != null
                                        ? (int) Math.ceil(route.getDurationSeconds() / 60.0)
                                        : -1)
                        .distanceMetres(route != null ? route.getDistanceMetres() : 0)
                        .build();
        return ResponseEntity.ok(ApiResponse.ok(eta));
    }
}
