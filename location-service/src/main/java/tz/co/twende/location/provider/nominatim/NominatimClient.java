package tz.co.twende.location.provider.nominatim;

import org.springframework.stereotype.Component;
import tz.co.twende.location.config.NominatimProperties;

@Component
public class NominatimClient {

    @SuppressWarnings("unused")
    private final NominatimProperties properties;

    public NominatimClient(NominatimProperties properties) {
        this.properties = properties;
    }
}
