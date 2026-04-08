package tz.co.twende.location.provider.google;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tz.co.twende.location.dto.GeocodingResult;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.provider.GeocodingProvider;

@Component("google")
@RequiredArgsConstructor
public class GoogleGeocodingProvider implements GeocodingProvider {

    private final GoogleMapsClient googleMapsClient;

    @Override
    public String getId() {
        return "google";
    }

    @Override
    public GeocodingResult geocode(String address, LatLng bias) {
        return googleMapsClient.geocode(address);
    }

    @Override
    public GeocodingResult reverseGeocode(LatLng point) {
        return googleMapsClient.reverseGeocode(
                point.getLatitude().doubleValue(), point.getLongitude().doubleValue());
    }
}
