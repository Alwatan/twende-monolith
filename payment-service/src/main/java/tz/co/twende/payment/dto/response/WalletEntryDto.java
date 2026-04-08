package tz.co.twende.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletEntryDto {

    private UUID id;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private UUID referenceId;
    private String description;
    private Instant createdAt;
}
