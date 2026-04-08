package tz.co.twende.location.provider.google;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.PlaceResult;
import tz.co.twende.location.provider.AutocompleteProvider;

@Component("googleAutocomplete")
@RequiredArgsConstructor
public class GoogleAutocompleteProvider implements AutocompleteProvider {

    private final GoogleMapsClient googleMapsClient;

    @Override
    public String getId() {
        return "google";
    }

    @Override
    public List<PlaceResult> search(String query, LatLng bias, String countryCode, int limit) {
        List<PlaceResult> results =
                googleMapsClient.autocomplete(
                        query,
                        bias != null ? bias.getLatitude().doubleValue() : 0,
                        bias != null ? bias.getLongitude().doubleValue() : 0,
                        countryCode);
        if (results.size() > limit) {
            return results.subList(0, limit);
        }
        return results;
    }
}
