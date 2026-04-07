package tz.co.twende.countryconfig.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(
        name = "required_driver_documents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"country_code", "document_type"}))
@Getter
@Setter
@NoArgsConstructor
public class RequiredDriverDocument extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String documentType;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private Boolean isMandatory;
}
