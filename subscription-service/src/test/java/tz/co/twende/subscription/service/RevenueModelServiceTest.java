package tz.co.twende.subscription.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.subscription.dto.RevenueModelDto;
import tz.co.twende.subscription.entity.DriverRevenueModel;
import tz.co.twende.subscription.repository.DriverRevenueModelRepository;

@ExtendWith(MockitoExtension.class)
class RevenueModelServiceTest {

    @Mock private DriverRevenueModelRepository revenueModelRepository;
    @Mock private SubscriptionService subscriptionService;

    @InjectMocks private RevenueModelService revenueModelService;

    @Test
    void givenNewDriver_whenRegisterFlatFee_thenModelCreated() {
        UUID driverId = UUID.randomUUID();
        when(revenueModelRepository.existsByDriverId(driverId)).thenReturn(false);
        when(revenueModelRepository.save(any(DriverRevenueModel.class)))
                .thenAnswer(i -> i.getArgument(0));

        RevenueModelDto result = revenueModelService.registerFlatFee(driverId, "TZ", "RIDE");

        assertThat(result.getDriverId()).isEqualTo(driverId);
        assertThat(result.getRevenueModel()).isEqualTo("FLAT_FEE");
        assertThat(result.getServiceCategory()).isEqualTo("RIDE");

        ArgumentCaptor<DriverRevenueModel> captor =
                ArgumentCaptor.forClass(DriverRevenueModel.class);
        verify(revenueModelRepository).save(captor.capture());
        assertThat(captor.getValue().getDriverId()).isEqualTo(driverId);
        assertThat(captor.getValue().getCountryCode()).isEqualTo("TZ");
    }

    @Test
    void givenExistingModel_whenRegisterFlatFee_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();
        when(revenueModelRepository.existsByDriverId(driverId)).thenReturn(true);

        assertThatThrownBy(() -> revenueModelService.registerFlatFee(driverId, "TZ", "RIDE"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already has a revenue model");
    }

    @Test
    void givenInvalidCategory_whenRegisterFlatFee_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(() -> revenueModelService.registerFlatFee(driverId, "TZ", "INVALID"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid service category");
    }

    @Test
    void givenActiveSubscription_whenHasActiveRevenueModel_thenReturnsTrue() {
        UUID driverId = UUID.randomUUID();
        when(subscriptionService.hasActiveSubscription(driverId)).thenReturn(true);

        boolean result = revenueModelService.hasActiveRevenueModel(driverId);

        assertThat(result).isTrue();
    }

    @Test
    void givenFlatFeeRegistration_whenHasActiveRevenueModel_thenReturnsTrue() {
        UUID driverId = UUID.randomUUID();
        when(subscriptionService.hasActiveSubscription(driverId)).thenReturn(false);
        when(revenueModelRepository.existsByDriverId(driverId)).thenReturn(true);

        boolean result = revenueModelService.hasActiveRevenueModel(driverId);

        assertThat(result).isTrue();
    }

    @Test
    void givenNoModelAndNoSubscription_whenHasActiveRevenueModel_thenReturnsFalse() {
        UUID driverId = UUID.randomUUID();
        when(subscriptionService.hasActiveSubscription(driverId)).thenReturn(false);
        when(revenueModelRepository.existsByDriverId(driverId)).thenReturn(false);

        boolean result = revenueModelService.hasActiveRevenueModel(driverId);

        assertThat(result).isFalse();
    }

    @Test
    void givenFlatFeeDriver_whenGetRevenueModel_thenReturnsFlatFeeDto() {
        UUID driverId = UUID.randomUUID();
        DriverRevenueModel model = createModel(driverId, "FLAT_FEE", "CARGO");

        when(subscriptionService.hasActiveSubscription(driverId)).thenReturn(false);
        when(revenueModelRepository.findByDriverId(driverId)).thenReturn(Optional.of(model));

        RevenueModelDto result = revenueModelService.getRevenueModel(driverId);

        assertThat(result).isNotNull();
        assertThat(result.getRevenueModel()).isEqualTo("FLAT_FEE");
        assertThat(result.getServiceCategory()).isEqualTo("CARGO");
        assertThat(result.isHasActiveSubscription()).isFalse();
    }

    @Test
    void givenSubscriptionOnlyDriver_whenGetRevenueModel_thenReturnsSubscriptionDto() {
        UUID driverId = UUID.randomUUID();

        when(subscriptionService.hasActiveSubscription(driverId)).thenReturn(true);
        when(revenueModelRepository.findByDriverId(driverId)).thenReturn(Optional.empty());

        RevenueModelDto result = revenueModelService.getRevenueModel(driverId);

        assertThat(result).isNotNull();
        assertThat(result.getRevenueModel()).isEqualTo("SUBSCRIPTION");
        assertThat(result.isHasActiveSubscription()).isTrue();
    }

    @Test
    void givenNoModel_whenGetRevenueModel_thenReturnsNull() {
        UUID driverId = UUID.randomUUID();

        when(subscriptionService.hasActiveSubscription(driverId)).thenReturn(false);
        when(revenueModelRepository.findByDriverId(driverId)).thenReturn(Optional.empty());

        RevenueModelDto result = revenueModelService.getRevenueModel(driverId);

        assertThat(result).isNull();
    }

    @Test
    void givenCharterCategory_whenSwitchToSubscription_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                revenueModelService.switchRevenueModel(
                                        driverId, "TZ", "SUBSCRIPTION", "CHARTER"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must use FLAT_FEE");
    }

    @Test
    void givenCargoCategory_whenSwitchToSubscription_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                revenueModelService.switchRevenueModel(
                                        driverId, "TZ", "SUBSCRIPTION", "CARGO"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must use FLAT_FEE");
    }

    @Test
    void givenRideDriver_whenSwitchToFlatFee_thenModelUpdated() {
        UUID driverId = UUID.randomUUID();
        when(revenueModelRepository.findByDriverId(driverId)).thenReturn(Optional.empty());
        when(revenueModelRepository.save(any(DriverRevenueModel.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(subscriptionService.hasActiveSubscription(driverId)).thenReturn(false);

        RevenueModelDto result =
                revenueModelService.switchRevenueModel(driverId, "TZ", "FLAT_FEE", "RIDE");

        assertThat(result.getRevenueModel()).isEqualTo("FLAT_FEE");
        assertThat(result.getServiceCategory()).isEqualTo("RIDE");
        verify(revenueModelRepository).save(any(DriverRevenueModel.class));
    }

    @Test
    void givenRideDriver_whenSwitchToSubscription_thenFlatFeeRemoved() {
        UUID driverId = UUID.randomUUID();
        DriverRevenueModel existingModel = createModel(driverId, "FLAT_FEE", "RIDE");
        when(revenueModelRepository.findByDriverId(driverId))
                .thenReturn(Optional.of(existingModel));
        when(subscriptionService.hasActiveSubscription(driverId)).thenReturn(true);

        RevenueModelDto result =
                revenueModelService.switchRevenueModel(driverId, "TZ", "SUBSCRIPTION", "RIDE");

        assertThat(result.getRevenueModel()).isEqualTo("SUBSCRIPTION");
        verify(revenueModelRepository).delete(existingModel);
    }

    @Test
    void givenInvalidRevenueModel_whenSwitch_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                revenueModelService.switchRevenueModel(
                                        driverId, "TZ", "INVALID", "RIDE"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid revenue model");
    }

    private DriverRevenueModel createModel(UUID driverId, String revenueModel, String category) {
        DriverRevenueModel model = new DriverRevenueModel();
        model.setId(UUID.randomUUID());
        model.setDriverId(driverId);
        model.setCountryCode("TZ");
        model.setRevenueModel(revenueModel);
        model.setServiceCategory(category);
        model.setRegisteredAt(Instant.now());
        return model;
    }
}
