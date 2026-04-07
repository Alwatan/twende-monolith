package tz.co.twende.countryconfig.dto;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequiredDriverDocumentDto implements Serializable {

    private UUID id;
    private String countryCode;
    private String documentType;
    private String displayName;
    private Boolean isMandatory;
}
