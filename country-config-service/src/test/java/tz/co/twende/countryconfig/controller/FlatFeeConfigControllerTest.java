package tz.co.twende.countryconfig.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.countryconfig.dto.FlatFeeConfigDto;
import tz.co.twende.countryconfig.dto.UpdateFlatFeeRequest;
import tz.co.twende.countryconfig.service.FlatFeeConfigService;

@ExtendWith(MockitoExtension.class)
class FlatFeeConfigControllerTest {

    @Mock private FlatFeeConfigService flatFeeConfigService;

    @InjectMocks private FlatFeeConfigController controller;

    private MockHttpServletRequest adminRequest;
    private MockHttpServletRequest nonAdminRequest;

    @BeforeEach
    void setUp() {
        adminRequest = new MockHttpServletRequest();
        adminRequest.addHeader("X-User-Role", "ADMIN");

        nonAdminRequest = new MockHttpServletRequest();
        nonAdminRequest.addHeader("X-User-Role", "RIDER");
    }

    @Test
    void givenCountryCode_whenGetFlatFeeConfigs_thenReturns200WithList() {
        FlatFeeConfigDto rideDto =
                FlatFeeConfigDto.builder()
                        .id(UUID.randomUUID())
                        .countryCode("TZ")
                        .serviceCategory("RIDE")
                        .percentage(new BigDecimal("15.00"))
                        .active(true)
                        .build();
        FlatFeeConfigDto charterDto =
                FlatFeeConfigDto.builder()
                        .id(UUID.randomUUID())
                        .countryCode("TZ")
                        .serviceCategory("CHARTER")
                        .percentage(new BigDecimal("12.00"))
                        .active(true)
                        .build();

        when(flatFeeConfigService.getFlatFeeConfigs("TZ")).thenReturn(List.of(rideDto, charterDto));

        ResponseEntity<ApiResponse<List<FlatFeeConfigDto>>> response =
                controller.getFlatFeeConfigs("TZ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(2);
    }

    @Test
    void givenCountryAndCategory_whenGetFlatFeeConfig_thenReturns200() {
        FlatFeeConfigDto dto =
                FlatFeeConfigDto.builder()
                        .countryCode("TZ")
                        .serviceCategory("RIDE")
                        .percentage(new BigDecimal("15.00"))
                        .build();
        when(flatFeeConfigService.getFlatFeeConfig("TZ", "RIDE")).thenReturn(dto);

        ResponseEntity<ApiResponse<FlatFeeConfigDto>> response =
                controller.getFlatFeeConfig("TZ", "RIDE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getPercentage())
                .isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void givenAdminRole_whenUpdateFlatFeeConfig_thenReturns200() {
        UpdateFlatFeeRequest request =
                UpdateFlatFeeRequest.builder().percentage(new BigDecimal("18.00")).build();
        FlatFeeConfigDto updatedDto =
                FlatFeeConfigDto.builder()
                        .countryCode("TZ")
                        .serviceCategory("RIDE")
                        .percentage(new BigDecimal("18.00"))
                        .build();
        when(flatFeeConfigService.updateFlatFeeConfig("TZ", "RIDE", request))
                .thenReturn(updatedDto);

        ResponseEntity<ApiResponse<FlatFeeConfigDto>> response =
                controller.updateFlatFeeConfig("TZ", "RIDE", request, adminRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getPercentage())
                .isEqualByComparingTo(new BigDecimal("18.00"));
    }

    @Test
    void givenNonAdminRole_whenUpdateFlatFeeConfig_thenThrowsUnauthorized() {
        UpdateFlatFeeRequest request =
                UpdateFlatFeeRequest.builder().percentage(new BigDecimal("18.00")).build();

        try {
            controller.updateFlatFeeConfig("TZ", "RIDE", request, nonAdminRequest);
            assertThat(true).as("Expected UnauthorizedException").isFalse();
        } catch (UnauthorizedException e) {
            assertThat(e.getMessage()).isEqualTo("Admin access required");
        }

        verify(flatFeeConfigService, never()).updateFlatFeeConfig(any(), any(), any());
    }

    @Test
    void givenLowercaseCountryCode_whenGetFlatFeeConfigs_thenConvertsToUppercase() {
        when(flatFeeConfigService.getFlatFeeConfigs("TZ")).thenReturn(List.of());

        controller.getFlatFeeConfigs("tz");

        verify(flatFeeConfigService).getFlatFeeConfigs("TZ");
    }
}
