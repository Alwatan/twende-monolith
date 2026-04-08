package tz.co.twende.subscription.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.enums.SubscriptionPlan;
import tz.co.twende.common.enums.SubscriptionStatus;
import tz.co.twende.common.event.subscription.SubscriptionActivatedEvent;
import tz.co.twende.common.event.subscription.SubscriptionExpiredEvent;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.subscription.client.PaymentClient;
import tz.co.twende.subscription.config.KafkaConfig;
import tz.co.twende.subscription.dto.PaymentResponse;
import tz.co.twende.subscription.dto.SubscriptionDto;
import tz.co.twende.subscription.dto.SubscriptionPlanDto;
import tz.co.twende.subscription.entity.Subscription;
import tz.co.twende.subscription.mapper.SubscriptionMapper;
import tz.co.twende.subscription.repository.SubscriptionPlanRepository;
import tz.co.twende.subscription.repository.SubscriptionRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentClient paymentClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SubscriptionMapper mapper;

    public List<SubscriptionPlanDto> getPlans(String countryCode, String vehicleType) {
        List<tz.co.twende.subscription.entity.SubscriptionPlan> plans;
        if (vehicleType != null && !vehicleType.isBlank()) {
            plans =
                    planRepository.findByCountryCodeAndVehicleTypeAndIsActiveTrue(
                            countryCode, vehicleType);
        } else {
            plans = planRepository.findByCountryCodeAndIsActiveTrue(countryCode);
        }
        return mapper.toPlanDtoList(plans);
    }

    @Transactional
    public SubscriptionDto purchase(
            UUID driverId, String countryCode, UUID planId, String paymentMethod) {
        tz.co.twende.subscription.entity.SubscriptionPlan plan =
                planRepository
                        .findById(planId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Subscription plan not found: " + planId));

        if (!plan.isActive()) {
            throw new BadRequestException("Subscription plan is not active");
        }

        if (!plan.getCountryCode().trim().equals(countryCode.trim())) {
            throw new BadRequestException("Plan is not available for country: " + countryCode);
        }

        boolean hasActive =
                subscriptionRepository.existsByDriverIdAndStatusAndExpiresAtAfter(
                        driverId, SubscriptionStatus.ACTIVE.name(), Instant.now());
        if (hasActive) {
            throw new BadRequestException("Driver already has an active subscription");
        }

        Subscription subscription = new Subscription();
        subscription.setDriverId(driverId);
        subscription.setCountryCode(countryCode);
        subscription.setPlanId(planId);
        subscription.setStatus(SubscriptionStatus.PENDING_PAYMENT.name());
        subscription.setPaymentMethod(paymentMethod);
        subscription.setAmountPaid(plan.getPrice());
        subscription = subscriptionRepository.save(subscription);

        try {
            PaymentResponse paymentResponse =
                    paymentClient.initiateSubscriptionPayment(
                            driverId,
                            subscription.getId(),
                            plan.getPrice(),
                            plan.getCurrencyCode(),
                            paymentMethod,
                            countryCode);

            Instant now = Instant.now();
            subscription.setStatus(SubscriptionStatus.ACTIVE.name());
            subscription.setStartedAt(now);
            subscription.setExpiresAt(now.plus(plan.getDurationHours(), ChronoUnit.HOURS));
            subscription.setPaymentRef(paymentResponse.getTransactionId());
            subscription = subscriptionRepository.save(subscription);

            publishActivatedEvent(subscription, plan);

            log.info(
                    "Subscription activated for driver {} with plan {}",
                    driverId,
                    plan.getDisplayName());
        } catch (Exception e) {
            log.error("Payment failed for driver {} subscription: {}", driverId, e.getMessage());
            subscription.setStatus(SubscriptionStatus.CANCELLED.name());
            subscriptionRepository.save(subscription);
            throw new BadRequestException("Payment failed: " + e.getMessage());
        }

        return mapper.toDto(subscription);
    }

    public boolean hasActiveSubscription(UUID driverId) {
        return subscriptionRepository.existsByDriverIdAndStatusAndExpiresAtAfter(
                driverId, SubscriptionStatus.ACTIVE.name(), Instant.now());
    }

    public SubscriptionDto getCurrentSubscription(UUID driverId) {
        return subscriptionRepository
                .findFirstByDriverIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                        driverId, SubscriptionStatus.ACTIVE.name(), Instant.now())
                .map(mapper::toDto)
                .orElse(null);
    }

    public Page<SubscriptionDto> getHistory(UUID driverId, Pageable pageable) {
        return subscriptionRepository
                .findByDriverIdOrderByCreatedAtDesc(driverId, pageable)
                .map(mapper::toDto);
    }

    @Transactional
    public void expireSubscriptions() {
        List<Subscription> expired =
                subscriptionRepository.findByStatusAndExpiresAtBefore(
                        SubscriptionStatus.ACTIVE.name(), Instant.now());
        for (Subscription sub : expired) {
            sub.setStatus(SubscriptionStatus.EXPIRED.name());
            subscriptionRepository.save(sub);
            publishExpiredEvent(sub);
            log.info("Subscription {} expired for driver {}", sub.getId(), sub.getDriverId());
        }
    }

    private void publishActivatedEvent(
            Subscription subscription, tz.co.twende.subscription.entity.SubscriptionPlan plan) {
        SubscriptionActivatedEvent event = new SubscriptionActivatedEvent();
        event.setSubscriptionId(subscription.getId());
        event.setDriverId(subscription.getDriverId());
        event.setPlan(SubscriptionPlan.valueOf(plan.getPlanType()));
        event.setExpiresAt(subscription.getExpiresAt());
        event.setCountryCode(subscription.getCountryCode());
        event.setEventType("SUBSCRIPTION_ACTIVATED");

        kafkaTemplate.send(
                KafkaConfig.TOPIC_SUBSCRIPTIONS_ACTIVATED,
                subscription.getCountryCode().trim() + ":" + subscription.getDriverId(),
                event);
    }

    private void publishExpiredEvent(Subscription subscription) {
        SubscriptionExpiredEvent event = new SubscriptionExpiredEvent();
        event.setSubscriptionId(subscription.getId());
        event.setDriverId(subscription.getDriverId());
        event.setCountryCode(subscription.getCountryCode());
        event.setEventType("SUBSCRIPTION_EXPIRED");

        kafkaTemplate.send(
                KafkaConfig.TOPIC_SUBSCRIPTIONS_EXPIRED,
                subscription.getCountryCode().trim() + ":" + subscription.getDriverId(),
                event);
    }
}
