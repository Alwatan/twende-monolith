package tz.co.twende.driver.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import tz.co.twende.common.enums.DocumentType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadDocumentRequest {

    @NotNull
    private DocumentType documentType;

    @NotBlank
    private String fileUrl;
}
