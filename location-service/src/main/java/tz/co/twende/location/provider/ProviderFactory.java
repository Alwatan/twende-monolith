package tz.co.twende.location.provider;

import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tz.co.twende.location.client.CountryConfigClient;
import tz.co.twende.location.dto.OperatingCityDto;

@Component
@Slf4j
public class ProviderFactory {

    private final Map<String, GeocodingProvider> geocodingProviders;
    private final Map<String, RoutingProvider> routingProviders;
    private final Map<String, AutocompleteProvider> autocompleteProviders;
    private final CountryConfigClient countryConfigClient;

    public ProviderFactory(
            Map<String, GeocodingProvider> geocodingProviders,
            Map<String, RoutingProvider> routingProviders,
            Map<String, AutocompleteProvider> autocompleteProviders,
            CountryConfigClient countryConfigClient) {
        this.geocodingProviders = geocodingProviders;
        this.routingProviders = routingProviders;
        this.autocompleteProviders = autocompleteProviders;
        this.countryConfigClient = countryConfigClient;
    }

    public GeocodingProvider geocodingFor(UUID cityId) {
        OperatingCityDto city = countryConfigClient.getCity(cityId);
        String providerKey = city.getGeocodingProvider().toLowerCase();
        GeocodingProvider provider = geocodingProviders.get(providerKey);
        if (provider == null) {
            log.warn(
                    "Geocoding provider '{}' not found for city {}, falling back to google",
                    providerKey,
                    cityId);
            provider = geocodingProviders.get("google");
        }
        return provider;
    }

    public RoutingProvider routingFor(UUID cityId) {
        OperatingCityDto city = countryConfigClient.getCity(cityId);
        String providerKey = city.getRoutingProvider().toLowerCase();
        // Map "google" to the "googleRouting" bean name
        if ("google".equals(providerKey)) {
            providerKey = "googleRouting";
        }
        RoutingProvider provider = routingProviders.get(providerKey);
        if (provider == null) {
            log.warn(
                    "Routing provider '{}' not found for city {}, falling back to google",
                    providerKey,
                    cityId);
            provider = routingProviders.get("googleRouting");
        }
        return provider;
    }

    public AutocompleteProvider autocompleteFor(UUID cityId) {
        OperatingCityDto city = countryConfigClient.getCity(cityId);
        String providerKey = city.getAutocompleteProvider().toLowerCase();
        // Map "google" to the "googleAutocomplete" bean name
        if ("google".equals(providerKey)) {
            providerKey = "googleAutocomplete";
        }
        AutocompleteProvider provider = autocompleteProviders.get(providerKey);
        if (provider == null) {
            log.warn(
                    "Autocomplete provider '{}' not found for city {}, falling back to" + " google",
                    providerKey,
                    cityId);
            provider = autocompleteProviders.get("googleAutocomplete");
        }
        return provider;
    }
}
