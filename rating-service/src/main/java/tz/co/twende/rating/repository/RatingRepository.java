package tz.co.twende.rating.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tz.co.twende.rating.entity.Rating;

@Repository
public interface RatingRepository extends JpaRepository<Rating, UUID> {

    boolean existsByRideIdAndRaterRole(UUID rideId, String raterRole);

    @Query("SELECT AVG(CAST(r.score AS double)) FROM Rating r WHERE r.ratedUserId = :userId")
    Double findAverageScoreByRatedUserId(@Param("userId") UUID userId);

    long countByRatedUserId(UUID userId);

    @Query(
            "SELECT r.score AS score, COUNT(r) AS cnt FROM Rating r"
                    + " WHERE r.ratedUserId = :userId GROUP BY r.score ORDER BY r.score")
    List<ScoreCount> findScoreDistributionByRatedUserId(@Param("userId") UUID userId);

    interface ScoreCount {
        Short getScore();

        Long getCnt();
    }
}
