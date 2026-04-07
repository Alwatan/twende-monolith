package tz.co.twende.driver.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.common.enums.DocumentType;
import tz.co.twende.driver.entity.DriverDocument;

@Repository
public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {

    List<DriverDocument> findByDriverId(UUID driverId);

    Optional<DriverDocument> findByDriverIdAndDocumentType(UUID driverId, DocumentType documentType);

    boolean existsByDriverIdAndDocumentType(UUID driverId, DocumentType documentType);
}
