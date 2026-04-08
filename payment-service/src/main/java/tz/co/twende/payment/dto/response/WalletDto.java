package tz.co.twende.payment.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletDto {

    private UUID driverId;
    private BigDecimal balance;
    private String currency;
    private List<WalletEntryDto> recentEntries;
}
