package tz.co.twende.rating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "ratings")
@Getter
@Setter
@NoArgsConstructor
public class Rating extends BaseEntity {

    @Column(nullable = false)
    private UUID rideId;

    @Column(nullable = false)
    private UUID ratedUserId;

    @Column(nullable = false)
    private UUID raterUserId;

    @Column(nullable = false, length = 10)
    private String raterRole;

    @Column(nullable = false)
    private Short score;

    private String comment;
}
