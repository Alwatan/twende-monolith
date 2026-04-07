package tz.co.twende.user.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.user.entity.SavedPlace;

@Repository
public interface SavedPlaceRepository extends JpaRepository<SavedPlace, UUID> {

    List<SavedPlace> findByUserId(UUID userId);

    int deleteByIdAndUserId(UUID id, UUID userId);
}
