package tz.co.twende.location.provider.osrm;

import org.springframework.stereotype.Component;
import tz.co.twende.location.config.OsrmProperties;

@Component
public class OsrmClient {

    @SuppressWarnings("unused")
    private final OsrmProperties properties;

    public OsrmClient(OsrmProperties properties) {
        this.properties = properties;
    }
}
