package tz.co.twende.location.provider.nominatim;

import org.springframework.stereotype.Component;
import tz.co.twende.location.dto.GeocodingResult;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.provider.GeocodingProvider;

@Component("nominatim")
public class NominatimGeocodingProvider implements GeocodingProvider {

    @Override
    public String getId() {
        return "nominatim";
    }

    @Override
    public GeocodingResult geocode(String address, LatLng bias) {
        throw new UnsupportedOperationException("Nominatim provider not yet implemented");
    }

    @Override
    public GeocodingResult reverseGeocode(LatLng point) {
        throw new UnsupportedOperationException("Nominatim provider not yet implemented");
    }
}
