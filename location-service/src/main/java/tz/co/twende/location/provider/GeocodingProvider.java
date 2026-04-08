package tz.co.twende.location.provider;

import tz.co.twende.location.dto.GeocodingResult;
import tz.co.twende.location.dto.LatLng;

public interface GeocodingProvider {
    String getId();

    GeocodingResult geocode(String address, LatLng bias);

    GeocodingResult reverseGeocode(LatLng point);
}
