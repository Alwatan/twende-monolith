package tz.co.twende.location.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.location.client.CountryConfigClient;
import tz.co.twende.location.dto.OperatingCityDto;

@ExtendWith(MockitoExtension.class)
class ProviderFactoryTest {

    @Mock private GeocodingProvider googleGeocoding;
    @Mock private GeocodingProvider nominatimGeocoding;
    @Mock private RoutingProvider googleRouting;
    @Mock private RoutingProvider osrmRouting;
    @Mock private AutocompleteProvider googleAutocomplete;
    @Mock private CountryConfigClient countryConfigClient;

    private ProviderFactory providerFactory;

    @BeforeEach
    void setUp() {
        providerFactory =
                new ProviderFactory(
                        Map.of("google", googleGeocoding, "nominatim", nominatimGeocoding),
                        Map.of("googleRouting", googleRouting, "osrm", osrmRouting),
                        Map.of("googleAutocomplete", googleAutocomplete),
                        countryConfigClient);
    }

    @Test
    void givenCityWithGoogleProvider_whenGeocodingFor_thenReturnGoogleProvider() {
        UUID cityId = UUID.randomUUID();
        OperatingCityDto city = new OperatingCityDto();
        city.setGeocodingProvider("GOOGLE");
        when(countryConfigClient.getCity(cityId)).thenReturn(city);

        GeocodingProvider result = providerFactory.geocodingFor(cityId);
        assertThat(result).isEqualTo(googleGeocoding);
    }

    @Test
    void givenCityWithNominatimProvider_whenGeocodingFor_thenReturnNominatimProvider() {
        UUID cityId = UUID.randomUUID();
        OperatingCityDto city = new OperatingCityDto();
        city.setGeocodingProvider("NOMINATIM");
        when(countryConfigClient.getCity(cityId)).thenReturn(city);

        GeocodingProvider result = providerFactory.geocodingFor(cityId);
        assertThat(result).isEqualTo(nominatimGeocoding);
    }

    @Test
    void givenCityWithGoogleRouting_whenRoutingFor_thenReturnGoogleRoutingProvider() {
        UUID cityId = UUID.randomUUID();
        OperatingCityDto city = new OperatingCityDto();
        city.setRoutingProvider("GOOGLE");
        when(countryConfigClient.getCity(cityId)).thenReturn(city);

        RoutingProvider result = providerFactory.routingFor(cityId);
        assertThat(result).isEqualTo(googleRouting);
    }

    @Test
    void givenCityWithOsrmRouting_whenRoutingFor_thenReturnOsrmProvider() {
        UUID cityId = UUID.randomUUID();
        OperatingCityDto city = new OperatingCityDto();
        city.setRoutingProvider("OSRM");
        when(countryConfigClient.getCity(cityId)).thenReturn(city);

        RoutingProvider result = providerFactory.routingFor(cityId);
        assertThat(result).isEqualTo(osrmRouting);
    }

    @Test
    void givenCityWithUnknownProvider_whenGeocodingFor_thenFallbackToGoogle() {
        UUID cityId = UUID.randomUUID();
        OperatingCityDto city = new OperatingCityDto();
        city.setGeocodingProvider("UNKNOWN");
        when(countryConfigClient.getCity(cityId)).thenReturn(city);

        GeocodingProvider result = providerFactory.geocodingFor(cityId);
        assertThat(result).isEqualTo(googleGeocoding);
    }

    @Test
    void givenCityWithUnknownRouting_whenRoutingFor_thenFallbackToGoogleRouting() {
        UUID cityId = UUID.randomUUID();
        OperatingCityDto city = new OperatingCityDto();
        city.setRoutingProvider("UNKNOWN_ROUTING");
        when(countryConfigClient.getCity(cityId)).thenReturn(city);

        RoutingProvider result = providerFactory.routingFor(cityId);
        assertThat(result).isEqualTo(googleRouting);
    }

    @Test
    void givenCityWithGoogleAutocomplete_whenAutocompleteFor_thenReturnGoogleAutocomplete() {
        UUID cityId = UUID.randomUUID();
        OperatingCityDto city = new OperatingCityDto();
        city.setAutocompleteProvider("GOOGLE");
        when(countryConfigClient.getCity(cityId)).thenReturn(city);

        AutocompleteProvider result = providerFactory.autocompleteFor(cityId);
        assertThat(result).isEqualTo(googleAutocomplete);
    }

    @Test
    void givenCityWithUnknownAutocomplete_whenAutocompleteFor_thenFallbackToGoogleAutocomplete() {
        UUID cityId = UUID.randomUUID();
        OperatingCityDto city = new OperatingCityDto();
        city.setAutocompleteProvider("UNKNOWN_AC");
        when(countryConfigClient.getCity(cityId)).thenReturn(city);

        AutocompleteProvider result = providerFactory.autocompleteFor(cityId);
        assertThat(result).isEqualTo(googleAutocomplete);
    }
}
