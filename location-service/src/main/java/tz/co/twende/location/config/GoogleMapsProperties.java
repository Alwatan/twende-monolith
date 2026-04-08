package tz.co.twende.location.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "twende.maps.google")
@Getter
@Setter
public class GoogleMapsProperties {
    private String apiKey;
    private boolean enabled = true;
}
