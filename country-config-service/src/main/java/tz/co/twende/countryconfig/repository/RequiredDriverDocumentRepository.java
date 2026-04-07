package tz.co.twende.countryconfig.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.countryconfig.entity.RequiredDriverDocument;

@Repository
public interface RequiredDriverDocumentRepository
        extends JpaRepository<RequiredDriverDocument, UUID> {

    List<RequiredDriverDocument> findByCountryCode(String countryCode);
}
