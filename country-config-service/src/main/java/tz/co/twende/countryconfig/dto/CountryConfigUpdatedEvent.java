package tz.co.twende.countryconfig.dto;

import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryConfigUpdatedEvent implements Serializable {

    private String countryCode;

    @Builder.Default private Instant timestamp = Instant.now();

    public CountryConfigUpdatedEvent(String countryCode) {
        this.countryCode = countryCode;
        this.timestamp = Instant.now();
    }
}
