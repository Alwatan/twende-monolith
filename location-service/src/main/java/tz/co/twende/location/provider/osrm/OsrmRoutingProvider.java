package tz.co.twende.location.provider.osrm;

import org.springframework.stereotype.Component;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.Route;
import tz.co.twende.location.provider.RoutingProvider;

@Component("osrm")
public class OsrmRoutingProvider implements RoutingProvider {

    @Override
    public String getId() {
        return "osrm";
    }

    @Override
    public Route getRoute(LatLng origin, LatLng destination) {
        throw new UnsupportedOperationException("OSRM provider not yet implemented");
    }

    @Override
    public int getEtaMinutes(LatLng origin, LatLng destination) {
        throw new UnsupportedOperationException("OSRM provider not yet implemented");
    }
}
