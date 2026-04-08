package tz.co.twende.subscription.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.subscription.client.PaymentClient;
import tz.co.twende.subscription.dto.PaymentResponse;
import tz.co.twende.subscription.dto.SubscriptionDto;
import tz.co.twende.subscription.dto.SubscriptionPlanDto;
import tz.co.twende.subscription.entity.Subscription;
import tz.co.twende.subscription.entity.SubscriptionPlan;
import tz.co.twende.subscription.mapper.SubscriptionMapper;
import tz.co.twende.subscription.repository.SubscriptionPlanRepository;
import tz.co.twende.subscription.repository.SubscriptionRepository;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PaymentClient paymentClient;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private SubscriptionMapper mapper;

    @InjectMocks private SubscriptionService subscriptionService;

    @Test
    void givenCountryAndVehicleType_whenGetPlans_thenReturnFilteredPlans() {
        SubscriptionPlan plan = createPlan("TZ", "BAJAJ", "DAILY", BigDecimal.valueOf(2000));
        SubscriptionPlanDto dto = new SubscriptionPlanDto();
        dto.setVehicleType("BAJAJ");

        when(planRepository.findByCountryCodeAndVehicleTypeAndIsActiveTrue("TZ", "BAJAJ"))
                .thenReturn(List.of(plan));
        when(mapper.toPlanDtoList(List.of(plan))).thenReturn(List.of(dto));

        List<SubscriptionPlanDto> result = subscriptionService.getPlans("TZ", "BAJAJ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVehicleType()).isEqualTo("BAJAJ");
        verify(planRepository).findByCountryCodeAndVehicleTypeAndIsActiveTrue("TZ", "BAJAJ");
    }

    @Test
    void givenCountryOnly_whenGetPlans_thenReturnAllPlansForCountry() {
        SubscriptionPlan plan1 = createPlan("TZ", "BAJAJ", "DAILY", BigDecimal.valueOf(2000));
        SubscriptionPlan plan2 = createPlan("TZ", "BODA_BODA", "DAILY", BigDecimal.valueOf(1000));

        when(planRepository.findByCountryCodeAndIsActiveTrue("TZ"))
                .thenReturn(List.of(plan1, plan2));
        when(mapper.toPlanDtoList(anyList()))
                .thenReturn(List.of(new SubscriptionPlanDto(), new SubscriptionPlanDto()));

        List<SubscriptionPlanDto> result = subscriptionService.getPlans("TZ", null);

        assertThat(result).hasSize(2);
        verify(planRepository).findByCountryCodeAndIsActiveTrue("TZ");
    }

    @Test
    void givenNoActiveSubscription_whenPurchase_thenSubscriptionActivated() {
        UUID driverId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        SubscriptionPlan plan = createPlan("TZ", "BAJAJ", "DAILY", BigDecimal.valueOf(2000));
        plan.setId(planId);
        plan.setDurationHours(24);
        plan.setCurrencyCode("TZS");

        PaymentResponse paymentResponse = new PaymentResponse();
        paymentResponse.setTransactionId(UUID.randomUUID());
        paymentResponse.setStatus("COMPLETED");

        Subscription savedSub = new Subscription();
        savedSub.setId(UUID.randomUUID());
        savedSub.setDriverId(driverId);
        savedSub.setCountryCode("TZ");
        savedSub.setPlanId(planId);

        SubscriptionDto expectedDto = new SubscriptionDto();
        expectedDto.setStatus("ACTIVE");

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.existsByDriverIdAndStatusAndExpiresAtAfter(
                        eq(driverId), eq("ACTIVE"), any(Instant.class)))
                .thenReturn(false);
        when(subscriptionRepository.save(any(Subscription.class))).thenReturn(savedSub);
        when(paymentClient.initiateSubscriptionPayment(any(), any(), any(), any(), any(), any()))
                .thenReturn(paymentResponse);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(mapper.toDto(any(Subscription.class))).thenReturn(expectedDto);

        SubscriptionDto result =
                subscriptionService.purchase(driverId, "TZ", planId, "MOBILE_MONEY");

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(subscriptionRepository, times(2)).save(any(Subscription.class));
        verify(kafkaTemplate).send(eq("twende.subscriptions.activated"), anyString(), any());
    }

    @Test
    void givenActiveSubscription_whenPurchase_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        SubscriptionPlan plan = createPlan("TZ", "BAJAJ", "DAILY", BigDecimal.valueOf(2000));
        plan.setId(planId);

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.existsByDriverIdAndStatusAndExpiresAtAfter(
                        eq(driverId), eq("ACTIVE"), any(Instant.class)))
                .thenReturn(true);

        assertThatThrownBy(
                        () -> subscriptionService.purchase(driverId, "TZ", planId, "MOBILE_MONEY"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already has an active subscription");
    }

    @Test
    void givenNonExistentPlan_whenPurchase_thenThrowsNotFound() {
        UUID driverId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> subscriptionService.purchase(driverId, "TZ", planId, "MOBILE_MONEY"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Subscription plan not found");
    }

    @Test
    void givenInactivePlan_whenPurchase_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        SubscriptionPlan plan = createPlan("TZ", "BAJAJ", "DAILY", BigDecimal.valueOf(2000));
        plan.setId(planId);
        plan.setActive(false);

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(
                        () -> subscriptionService.purchase(driverId, "TZ", planId, "MOBILE_MONEY"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void givenWrongCountry_whenPurchase_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        SubscriptionPlan plan = createPlan("TZ", "BAJAJ", "DAILY", BigDecimal.valueOf(2000));
        plan.setId(planId);

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(
                        () -> subscriptionService.purchase(driverId, "KE", planId, "MOBILE_MONEY"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not available for country");
    }

    @Test
    void givenPaymentFails_whenPurchase_thenSubscriptionCancelled() {
        UUID driverId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        SubscriptionPlan plan = createPlan("TZ", "BAJAJ", "DAILY", BigDecimal.valueOf(2000));
        plan.setId(planId);
        plan.setDurationHours(24);
        plan.setCurrencyCode("TZS");

        Subscription savedSub = new Subscription();
        savedSub.setId(UUID.randomUUID());
        savedSub.setDriverId(driverId);
        savedSub.setCountryCode("TZ");

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.existsByDriverIdAndStatusAndExpiresAtAfter(
                        eq(driverId), eq("ACTIVE"), any(Instant.class)))
                .thenReturn(false);
        when(subscriptionRepository.save(any(Subscription.class))).thenReturn(savedSub);
        when(paymentClient.initiateSubscriptionPayment(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Payment gateway timeout"));

        assertThatThrownBy(
                        () -> subscriptionService.purchase(driverId, "TZ", planId, "MOBILE_MONEY"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Payment failed");

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void givenActiveSubscription_whenHasActiveSubscription_thenReturnsTrue() {
        UUID driverId = UUID.randomUUID();
        when(subscriptionRepository.existsByDriverIdAndStatusAndExpiresAtAfter(
                        eq(driverId), eq("ACTIVE"), any(Instant.class)))
                .thenReturn(true);

        boolean result = subscriptionService.hasActiveSubscription(driverId);

        assertThat(result).isTrue();
    }

    @Test
    void givenNoActiveSubscription_whenHasActiveSubscription_thenReturnsFalse() {
        UUID driverId = UUID.randomUUID();
        when(subscriptionRepository.existsByDriverIdAndStatusAndExpiresAtAfter(
                        eq(driverId), eq("ACTIVE"), any(Instant.class)))
                .thenReturn(false);

        boolean result = subscriptionService.hasActiveSubscription(driverId);

        assertThat(result).isFalse();
    }

    @Test
    void givenActiveSubscription_whenGetCurrentSubscription_thenReturnsDto() {
        UUID driverId = UUID.randomUUID();
        Subscription sub = new Subscription();
        sub.setDriverId(driverId);
        sub.setStatus("ACTIVE");

        SubscriptionDto dto = new SubscriptionDto();
        dto.setStatus("ACTIVE");

        when(subscriptionRepository
                        .findFirstByDriverIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                                eq(driverId), eq("ACTIVE"), any(Instant.class)))
                .thenReturn(Optional.of(sub));
        when(mapper.toDto(sub)).thenReturn(dto);

        SubscriptionDto result = subscriptionService.getCurrentSubscription(driverId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void givenNoActiveSubscription_whenGetCurrentSubscription_thenReturnsNull() {
        UUID driverId = UUID.randomUUID();
        when(subscriptionRepository
                        .findFirstByDriverIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                                eq(driverId), eq("ACTIVE"), any(Instant.class)))
                .thenReturn(Optional.empty());

        SubscriptionDto result = subscriptionService.getCurrentSubscription(driverId);

        assertThat(result).isNull();
    }

    @Test
    void givenExpiredSubscriptions_whenExpireSubscriptions_thenMarksExpiredAndPublishesEvents() {
        Subscription sub1 = new Subscription();
        sub1.setId(UUID.randomUUID());
        sub1.setDriverId(UUID.randomUUID());
        sub1.setCountryCode("TZ");
        sub1.setStatus("ACTIVE");
        sub1.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        Subscription sub2 = new Subscription();
        sub2.setId(UUID.randomUUID());
        sub2.setDriverId(UUID.randomUUID());
        sub2.setCountryCode("TZ");
        sub2.setStatus("ACTIVE");
        sub2.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));

        when(subscriptionRepository.findByStatusAndExpiresAtBefore(
                        eq("ACTIVE"), any(Instant.class)))
                .thenReturn(List.of(sub1, sub2));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        subscriptionService.expireSubscriptions();

        verify(subscriptionRepository, times(2)).save(any(Subscription.class));
        verify(kafkaTemplate, times(2))
                .send(eq("twende.subscriptions.expired"), anyString(), any());
        assertThat(sub1.getStatus()).isEqualTo("EXPIRED");
        assertThat(sub2.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void givenDriverHistory_whenGetHistory_thenReturnsPaginatedResults() {
        UUID driverId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 20);
        Subscription sub = new Subscription();
        sub.setDriverId(driverId);

        SubscriptionDto dto = new SubscriptionDto();
        dto.setDriverId(driverId);

        Page<Subscription> page = new PageImpl<>(List.of(sub));
        when(subscriptionRepository.findByDriverIdOrderByCreatedAtDesc(driverId, pageRequest))
                .thenReturn(page);
        when(mapper.toDto(sub)).thenReturn(dto);

        Page<SubscriptionDto> result = subscriptionService.getHistory(driverId, pageRequest);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getDriverId()).isEqualTo(driverId);
    }

    private SubscriptionPlan createPlan(
            String countryCode, String vehicleType, String planType, BigDecimal price) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setCountryCode(countryCode);
        plan.setVehicleType(vehicleType);
        plan.setPlanType(planType);
        plan.setPrice(price);
        plan.setCurrencyCode("TZS");
        plan.setDurationHours(24);
        plan.setDisplayName(vehicleType + " - " + planType);
        plan.setActive(true);
        return plan;
    }
}
