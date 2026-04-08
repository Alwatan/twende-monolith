package tz.co.twende.location.provider;

import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.Route;

public interface RoutingProvider {
    String getId();

    Route getRoute(LatLng origin, LatLng destination);

    int getEtaMinutes(LatLng origin, LatLng destination);
}
