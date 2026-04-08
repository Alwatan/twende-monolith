package tz.co.twende.location.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "twende.maps.nominatim")
@Getter
@Setter
public class NominatimProperties {
    private String baseUrl = "http://localhost:8088";
    private boolean enabled = false;
}
