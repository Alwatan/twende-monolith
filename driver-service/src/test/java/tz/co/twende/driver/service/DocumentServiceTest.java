package tz.co.twende.driver.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.DocumentType;
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

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DriverDocumentRepository documentRepository;
    @Mock private DriverProfileRepository driverProfileRepository;
    @Mock private DriverMapper driverMapper;

    @InjectMocks private DocumentService documentService;

    @Test
    void givenValidRequest_whenUploadDocument_thenDocumentCreated() {
        UUID driverId = UUID.randomUUID();
        UploadDocumentRequest request =
                UploadDocumentRequest.builder()
                        .documentType(DocumentType.NATIONAL_ID)
                        .fileUrl("http://minio/doc.pdf")
                        .build();
        DriverDocument saved = createDocument(driverId);
        DriverDocumentDto dto =
                DriverDocumentDto.builder()
                        .driverId(driverId)
                        .documentType(DocumentType.NATIONAL_ID)
                        .status("PENDING")
                        .build();

        when(driverProfileRepository.existsById(driverId)).thenReturn(true);
        when(documentRepository.existsByDriverIdAndDocumentType(driverId, DocumentType.NATIONAL_ID))
                .thenReturn(false);
        when(documentRepository.save(any())).thenReturn(saved);
        when(driverMapper.toDocumentDto(any())).thenReturn(dto);

        DriverDocumentDto result = documentService.uploadDocument(driverId, "TZ", request);
        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void givenDuplicateDocType_whenUploadDocument_thenThrowConflict() {
        UUID driverId = UUID.randomUUID();
        UploadDocumentRequest request =
                UploadDocumentRequest.builder()
                        .documentType(DocumentType.NATIONAL_ID)
                        .fileUrl("http://minio/doc.pdf")
                        .build();

        when(driverProfileRepository.existsById(driverId)).thenReturn(true);
        when(documentRepository.existsByDriverIdAndDocumentType(driverId, DocumentType.NATIONAL_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> documentService.uploadDocument(driverId, "TZ", request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void givenPendingDocument_whenVerifyApproved_thenStatusIsVerified() {
        UUID docId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        DriverDocument document = createDocument(UUID.randomUUID());
        document.setId(docId);
        DocumentVerifyRequest request = DocumentVerifyRequest.builder().verified(true).build();
        DriverDocumentDto dto = DriverDocumentDto.builder().status("VERIFIED").build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenReturn(document);
        when(driverMapper.toDocumentDto(any())).thenReturn(dto);

        DriverDocumentDto result = documentService.verifyDocument(docId, adminId, request);
        assertThat(result.getStatus()).isEqualTo("VERIFIED");
    }

    @Test
    void givenPendingDocument_whenVerifyRejected_thenStatusIsRejected() {
        UUID docId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        DriverDocument document = createDocument(UUID.randomUUID());
        document.setId(docId);
        DocumentVerifyRequest request =
                DocumentVerifyRequest.builder()
                        .verified(false)
                        .rejectionReason("Blurry image")
                        .build();
        DriverDocumentDto dto =
                DriverDocumentDto.builder()
                        .status("REJECTED")
                        .rejectionReason("Blurry image")
                        .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenReturn(document);
        when(driverMapper.toDocumentDto(any())).thenReturn(dto);

        DriverDocumentDto result = documentService.verifyDocument(docId, adminId, request);
        assertThat(result.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void givenAlreadyVerifiedDocument_whenVerify_thenThrowBadRequest() {
        UUID docId = UUID.randomUUID();
        DriverDocument document = createDocument(UUID.randomUUID());
        document.setId(docId);
        document.setStatus("VERIFIED");
        DocumentVerifyRequest request = DocumentVerifyRequest.builder().verified(true).build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> documentService.verifyDocument(docId, UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void givenNonExistingDocument_whenVerify_thenThrowNotFound() {
        UUID docId = UUID.randomUUID();
        DocumentVerifyRequest request = DocumentVerifyRequest.builder().verified(true).build();

        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.verifyDocument(docId, UUID.randomUUID(), request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void givenDriverWithDocuments_whenListDocuments_thenReturnAll() {
        UUID driverId = UUID.randomUUID();
        List<DriverDocument> docs = List.of(createDocument(driverId));
        List<DriverDocumentDto> dtos =
                List.of(DriverDocumentDto.builder().driverId(driverId).build());

        when(driverProfileRepository.existsById(driverId)).thenReturn(true);
        when(documentRepository.findByDriverId(driverId)).thenReturn(docs);
        when(driverMapper.toDocumentDtoList(docs)).thenReturn(dtos);

        List<DriverDocumentDto> result = documentService.listDocuments(driverId);
        assertThat(result).hasSize(1);
    }

    @Test
    void givenNonExistingDriver_whenListDocuments_thenThrowNotFound() {
        UUID driverId = UUID.randomUUID();
        when(driverProfileRepository.existsById(driverId)).thenReturn(false);

        assertThatThrownBy(() -> documentService.listDocuments(driverId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private DriverDocument createDocument(UUID driverId) {
        DriverDocument document = new DriverDocument();
        document.setId(UUID.randomUUID());
        document.setDriverId(driverId);
        document.setDocumentType(DocumentType.NATIONAL_ID);
        document.setFileUrl("http://minio/doc.pdf");
        document.setStatus("PENDING");
        document.setCountryCode("TZ");
        document.setCreatedAt(Instant.now());
        document.setUpdatedAt(Instant.now());
        return document;
    }
}
