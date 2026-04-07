package tz.co.twende.driver.dto.response;

import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverDetailDto {
    private DriverProfileDto profile;
    private List<DriverVehicleDto> vehicles;
    private List<DriverDocumentDto> documents;
}
