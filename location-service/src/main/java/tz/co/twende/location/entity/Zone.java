package tz.co.twende.location.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "zones")
@Getter
@Setter
@NoArgsConstructor
public class Zone extends BaseEntity {

    @Column(nullable = false)
    private UUID cityId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "GEOGRAPHY(POLYGON, 4326)", nullable = false)
    @ColumnTransformer(read = "ST_AsText(boundary)", write = "ST_GeogFromText(?)")
    private String boundary;

    @Column(nullable = false, length = 20)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '{}'")
    private String config;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
