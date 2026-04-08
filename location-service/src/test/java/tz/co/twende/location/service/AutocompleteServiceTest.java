package tz.co.twende.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.PlaceResult;
import tz.co.twende.location.provider.AutocompleteProvider;
import tz.co.twende.location.provider.ProviderFactory;

@ExtendWith(MockitoExtension.class)
class AutocompleteServiceTest {

    @Mock private ProviderFactory providerFactory;
    @Mock private AutocompleteProvider autocompleteProvider;

    @InjectMocks private AutocompleteService autocompleteService;

    @Test
    void givenValidQuery_whenSearch_thenDelegateToProvider() {
        UUID cityId = UUID.randomUUID();
        LatLng bias = new LatLng(new BigDecimal("-6.792"), new BigDecimal("39.208"));
        List<PlaceResult> expected =
                List.of(PlaceResult.builder().placeId("p1").description("Place 1").build());

        when(providerFactory.autocompleteFor(cityId)).thenReturn(autocompleteProvider);
        when(autocompleteProvider.search("test", bias, "TZ", 5)).thenReturn(expected);

        List<PlaceResult> result = autocompleteService.search("test", bias, "TZ", cityId, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlaceId()).isEqualTo("p1");
    }

    @Test
    void givenNullBias_whenSearch_thenDelegateWithNull() {
        UUID cityId = UUID.randomUUID();
        when(providerFactory.autocompleteFor(cityId)).thenReturn(autocompleteProvider);
        when(autocompleteProvider.search("test", null, "TZ", 5)).thenReturn(List.of());

        List<PlaceResult> result = autocompleteService.search("test", null, "TZ", cityId, 5);

        assertThat(result).isEmpty();
    }
}
