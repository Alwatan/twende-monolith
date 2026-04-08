package tz.co.twende.location.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "twende.maps.osrm")
@Getter
@Setter
public class OsrmProperties {
    private String baseUrl = "http://localhost:5000";
    private boolean enabled = false;
}
