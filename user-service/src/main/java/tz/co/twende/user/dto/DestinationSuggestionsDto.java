package tz.co.twende.user.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DestinationSuggestionsDto {
    private List<FrequentDestinationDto> frequent;
    private List<RecentRideDto> recent;
}
