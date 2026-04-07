package tz.co.twende.driver.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.driver.dto.request.DocumentVerifyRequest;
import tz.co.twende.driver.dto.request.UploadDocumentRequest;
import tz.co.twende.driver.dto.response.DriverDocumentDto;
import tz.co.twende.driver.entity.DriverDocument;
import tz.co.twende.driver.mapper.DriverMapper;
import tz.co.twende.driver.repository.DriverDocumentRepository;
import tz.co.twende.driver.repository.DriverProfileRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DriverDocumentRepository documentRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DriverMapper driverMapper;

    @Transactional
    public DriverDocumentDto uploadDocument(
            UUID driverId, String countryCode, UploadDocumentRequest request) {
        if (!driverProfileRepository.existsById(driverId)) {
            throw new ResourceNotFoundException("Driver not found with id: " + driverId);
        }

        if (documentRepository.existsByDriverIdAndDocumentType(
                driverId, request.getDocumentType())) {
            throw new ConflictException(
                    "Document of type "
                            + request.getDocumentType()
                            + " already exists for this driver");
        }

        DriverDocument document = new DriverDocument();
        document.setDriverId(driverId);
        document.setCountryCode(countryCode);
        document.setDocumentType(request.getDocumentType());
        document.setFileUrl(request.getFileUrl());
        document.setStatus("PENDING");

        DriverDocument saved = documentRepository.save(document);
        log.info("Uploaded document {} for driver {}", request.getDocumentType(), driverId);
        return driverMapper.toDocumentDto(saved);
    }

    public List<DriverDocumentDto> listDocuments(UUID driverId) {
        if (!driverProfileRepository.existsById(driverId)) {
            throw new ResourceNotFoundException("Driver not found with id: " + driverId);
        }
        return driverMapper.toDocumentDtoList(documentRepository.findByDriverId(driverId));
    }

    @Transactional
    public DriverDocumentDto verifyDocument(
            UUID documentId, UUID adminId, DocumentVerifyRequest request) {
        DriverDocument document =
                documentRepository
                        .findById(documentId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Document not found: " + documentId));

        if (!"PENDING".equals(document.getStatus())) {
            throw new BadRequestException("Document is already " + document.getStatus());
        }

        if (Boolean.TRUE.equals(request.getVerified())) {
            document.setStatus("VERIFIED");
            document.setVerifiedBy(adminId);
            document.setVerifiedAt(Instant.now());
            document.setRejectionReason(null);
        } else {
            document.setStatus("REJECTED");
            document.setRejectionReason(request.getRejectionReason());
        }

        DriverDocument saved = documentRepository.save(document);
        log.info("Document {} {} by admin {}", documentId, saved.getStatus(), adminId);
        return driverMapper.toDocumentDto(saved);
    }
}
