package tz.co.twende.location.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.PlaceResult;
import tz.co.twende.location.provider.ProviderFactory;

@Service
@RequiredArgsConstructor
public class AutocompleteService {

    private final ProviderFactory providerFactory;

    public List<PlaceResult> search(
            String query, LatLng bias, String countryCode, UUID cityId, int limit) {
        var provider = providerFactory.autocompleteFor(cityId);
        return provider.search(query, bias, countryCode, limit);
    }
}
