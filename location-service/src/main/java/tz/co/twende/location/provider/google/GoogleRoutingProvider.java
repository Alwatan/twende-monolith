package tz.co.twende.location.provider.google;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.Route;
import tz.co.twende.location.provider.RoutingProvider;

@Component("googleRouting")
@RequiredArgsConstructor
public class GoogleRoutingProvider implements RoutingProvider {

    private final GoogleMapsClient googleMapsClient;

    @Override
    public String getId() {
        return "google";
    }

    @Override
    public Route getRoute(LatLng origin, LatLng destination) {
        return googleMapsClient.directions(
                origin.getLatitude().doubleValue(),
                origin.getLongitude().doubleValue(),
                destination.getLatitude().doubleValue(),
                destination.getLongitude().doubleValue());
    }

    @Override
    public int getEtaMinutes(LatLng origin, LatLng destination) {
        Route route = getRoute(origin, destination);
        if (route == null) {
            return -1;
        }
        return (int) Math.ceil(route.getDurationSeconds() / 60.0);
    }
}
