package tz.co.twende.location.provider;

import java.util.List;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.PlaceResult;

public interface AutocompleteProvider {
    String getId();

    List<PlaceResult> search(String query, LatLng bias, String countryCode, int limit);
}
