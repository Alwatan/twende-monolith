package tz.co.twende.countryconfig.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
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
import tz.co.twende.countryconfig.dto.*;
import tz.co.twende.countryconfig.service.CountryConfigService;

@ExtendWith(MockitoExtension.class)
class CountryConfigControllerTest {

    @Mock private CountryConfigService countryConfigService;

    @InjectMocks private CountryConfigController controller;

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
    void givenValidCountry_whenGetConfig_thenReturns200() {
        CountryConfigDto dto =
                CountryConfigDto.builder().code("TZ").name("Tanzania").status("ACTIVE").build();
        when(countryConfigService.getConfig("TZ")).thenReturn(dto);

        ResponseEntity<ApiResponse<CountryConfigDto>> response = controller.getConfig("TZ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getCode()).isEqualTo("TZ");
    }

    @Test
    void givenActiveCountries_whenGetActive_thenReturns200() {
        when(countryConfigService.getActiveCountries()).thenReturn(List.of("TZ"));

        ResponseEntity<ApiResponse<List<String>>> response = controller.getActiveCountries();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsExactly("TZ");
    }

    @Test
    void givenAdminRole_whenUpdateConfig_thenReturns200() {
        UpdateCountryConfigRequest request =
                UpdateCountryConfigRequest.builder().name("Updated Tanzania").build();

        ResponseEntity<ApiResponse<Void>> response =
                controller.updateConfig("TZ", request, adminRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(countryConfigService).updateConfig("TZ", request);
    }

    @Test
    void givenNonAdminRole_whenUpdateConfig_thenThrowsUnauthorized() {
        UpdateCountryConfigRequest request =
                UpdateCountryConfigRequest.builder().name("Updated").build();

        try {
            controller.updateConfig("TZ", request, nonAdminRequest);
            assertThat(true).as("Expected UnauthorizedException").isFalse();
        } catch (UnauthorizedException e) {
            assertThat(e.getMessage()).isEqualTo("Admin access required");
        }

        verify(countryConfigService, never()).updateConfig(anyString(), any());
    }

    @Test
    void givenVehicleTypes_whenGetVehicleTypes_thenReturns200() {
        VehicleTypeConfigDto dto =
                VehicleTypeConfigDto.builder().vehicleType("BAJAJ").displayName("Bajaj").build();
        when(countryConfigService.getVehicleTypes("TZ")).thenReturn(List.of(dto));

        ResponseEntity<ApiResponse<List<VehicleTypeConfigDto>>> response =
                controller.getVehicleTypes("TZ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).getVehicleType()).isEqualTo("BAJAJ");
    }

    @Test
    void givenCities_whenGetCities_thenReturns200() {
        OperatingCityDto dto =
                OperatingCityDto.builder().cityId("dar-es-salaam").name("Dar es Salaam").build();
        when(countryConfigService.getCities("TZ")).thenReturn(List.of(dto));

        ResponseEntity<ApiResponse<List<OperatingCityDto>>> response = controller.getCities("TZ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    void givenAdminRole_whenUpdateFeatures_thenReturns200() {
        Map<String, Object> features = Map.of("surgeEnabled", true);

        ResponseEntity<ApiResponse<Void>> response =
                controller.updateFeatures("TZ", features, adminRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(countryConfigService).updateFeatures("TZ", features);
    }

    @Test
    void givenNonAdminRole_whenUpdateFeatures_thenThrowsUnauthorized() {
        Map<String, Object> features = Map.of("surgeEnabled", true);

        try {
            controller.updateFeatures("TZ", features, nonAdminRequest);
            assertThat(true).as("Expected UnauthorizedException").isFalse();
        } catch (UnauthorizedException e) {
            assertThat(e.getMessage()).isEqualTo("Admin access required");
        }

        verify(countryConfigService, never()).updateFeatures(anyString(), any());
    }

    @Test
    void givenAdminRole_whenUpdateStatus_thenReturns200() {
        Map<String, String> body = Map.of("status", "INACTIVE");

        ResponseEntity<ApiResponse<Void>> response =
                controller.updateStatus("TZ", body, adminRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(countryConfigService).updateStatus("TZ", "INACTIVE");
    }

    @Test
    void givenAdminRole_whenUpdateVehicleType_thenReturns200() {
        UUID vtId = UUID.randomUUID();
        VehicleTypeConfigDto dto =
                VehicleTypeConfigDto.builder().displayName("Updated Bajaj").build();

        ResponseEntity<ApiResponse<Void>> response =
                controller.updateVehicleType("TZ", vtId, dto, adminRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(countryConfigService).updateVehicleType("TZ", vtId, dto);
    }

    @Test
    void givenNonAdminRole_whenUpdateVehicleType_thenThrowsUnauthorized() {
        UUID vtId = UUID.randomUUID();
        VehicleTypeConfigDto dto = VehicleTypeConfigDto.builder().displayName("Updated").build();

        try {
            controller.updateVehicleType("TZ", vtId, dto, nonAdminRequest);
            assertThat(true).as("Expected UnauthorizedException").isFalse();
        } catch (UnauthorizedException e) {
            assertThat(e.getMessage()).isEqualTo("Admin access required");
        }
    }

    @Test
    void givenAdminRole_whenCreateCity_thenReturns201() {
        CreateCityRequest request =
                CreateCityRequest.builder()
                        .cityId("mwanza")
                        .name("Mwanza")
                        .timezone("Africa/Dar_es_Salaam")
                        .centerLat(-2.5164)
                        .centerLng(32.9175)
                        .radiusKm(15)
                        .build();

        OperatingCityDto cityDto =
                OperatingCityDto.builder().cityId("mwanza").name("Mwanza").build();

        when(countryConfigService.createCity("TZ", request)).thenReturn(cityDto);

        ResponseEntity<ApiResponse<OperatingCityDto>> response =
                controller.createCity("TZ", request, adminRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getCityId()).isEqualTo("mwanza");
    }

    @Test
    void givenNonAdminRole_whenCreateCity_thenThrowsUnauthorized() {
        CreateCityRequest request =
                CreateCityRequest.builder()
                        .cityId("city")
                        .name("City")
                        .timezone("UTC")
                        .centerLat(0.0)
                        .centerLng(0.0)
                        .radiusKm(10)
                        .build();

        try {
            controller.createCity("TZ", request, nonAdminRequest);
            assertThat(true).as("Expected UnauthorizedException").isFalse();
        } catch (UnauthorizedException e) {
            assertThat(e.getMessage()).isEqualTo("Admin access required");
        }
    }

    @Test
    void givenAdminRole_whenGetAllConfigs_thenReturns200() {
        CountryConfigDto dto = CountryConfigDto.builder().code("TZ").name("Tanzania").build();
        when(countryConfigService.getAllConfigs()).thenReturn(List.of(dto));

        ResponseEntity<ApiResponse<List<CountryConfigDto>>> response =
                controller.getAllConfigs(adminRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    void givenNonAdminRole_whenGetAllConfigs_thenThrowsUnauthorized() {
        try {
            controller.getAllConfigs(nonAdminRequest);
            assertThat(true).as("Expected UnauthorizedException").isFalse();
        } catch (UnauthorizedException e) {
            assertThat(e.getMessage()).isEqualTo("Admin access required");
        }
    }

    @Test
    void givenLowercaseCountryCode_whenGetConfig_thenConvertsToUppercase() {
        CountryConfigDto dto = CountryConfigDto.builder().code("TZ").name("Tanzania").build();
        when(countryConfigService.getConfig("TZ")).thenReturn(dto);

        controller.getConfig("tz");

        verify(countryConfigService).getConfig("TZ");
    }
}
