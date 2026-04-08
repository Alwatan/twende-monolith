package tz.co.twende.driver.dto.response;

import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverServiceCategoriesDto {

    private UUID driverId;
    private String revenueModel;
    private String qualityTier;
    private Set<String> serviceCategories;
}
