package tz.co.twende.subscription.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.enums.RevenueModel;
import tz.co.twende.common.enums.ServiceCategory;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.subscription.dto.RevenueModelDto;
import tz.co.twende.subscription.entity.DriverRevenueModel;
import tz.co.twende.subscription.repository.DriverRevenueModelRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class RevenueModelService {

    private final DriverRevenueModelRepository revenueModelRepository;
    private final SubscriptionService subscriptionService;

    @Transactional
    public RevenueModelDto registerFlatFee(
            UUID driverId, String countryCode, String serviceCategory) {
        ServiceCategory category;
        try {
            category = ServiceCategory.valueOf(serviceCategory);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid service category: " + serviceCategory);
        }

        if (revenueModelRepository.existsByDriverId(driverId)) {
            throw new BadRequestException("Driver already has a revenue model registered");
        }

        DriverRevenueModel model = new DriverRevenueModel();
        model.setDriverId(driverId);
        model.setCountryCode(countryCode);
        model.setRevenueModel(RevenueModel.FLAT_FEE.name());
        model.setServiceCategory(category.name());
        model.setRegisteredAt(Instant.now());
        revenueModelRepository.save(model);

        log.info("Registered flat fee for driver {} in category {}", driverId, serviceCategory);

        return toDto(model, false);
    }

    public boolean hasActiveRevenueModel(UUID driverId) {
        boolean hasSubscription = subscriptionService.hasActiveSubscription(driverId);
        if (hasSubscription) {
            return true;
        }
        return revenueModelRepository.existsByDriverId(driverId);
    }

    public RevenueModelDto getRevenueModel(UUID driverId) {
        boolean hasSubscription = subscriptionService.hasActiveSubscription(driverId);

        return revenueModelRepository
                .findByDriverId(driverId)
                .map(model -> toDto(model, hasSubscription))
                .orElseGet(
                        () -> {
                            if (hasSubscription) {
                                return RevenueModelDto.builder()
                                        .driverId(driverId)
                                        .revenueModel(RevenueModel.SUBSCRIPTION.name())
                                        .serviceCategory(ServiceCategory.RIDE.name())
                                        .hasActiveSubscription(true)
                                        .build();
                            }
                            return null;
                        });
    }

    @Transactional
    public RevenueModelDto switchRevenueModel(
            UUID driverId, String countryCode, String newModel, String serviceCategory) {
        RevenueModel targetModel;
        try {
            targetModel = RevenueModel.valueOf(newModel);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid revenue model: " + newModel);
        }

        ServiceCategory category;
        try {
            category = ServiceCategory.valueOf(serviceCategory);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid service category: " + serviceCategory);
        }

        // Charter and Cargo must be FLAT_FEE
        if ((category == ServiceCategory.CHARTER || category == ServiceCategory.CARGO)
                && targetModel == RevenueModel.SUBSCRIPTION) {
            throw new BadRequestException(
                    category.name() + " drivers must use FLAT_FEE revenue model");
        }

        if (targetModel == RevenueModel.SUBSCRIPTION) {
            // Switch to subscription: remove flat fee registration if exists
            revenueModelRepository
                    .findByDriverId(driverId)
                    .ifPresent(revenueModelRepository::delete);

            boolean hasSubscription = subscriptionService.hasActiveSubscription(driverId);
            return RevenueModelDto.builder()
                    .driverId(driverId)
                    .revenueModel(RevenueModel.SUBSCRIPTION.name())
                    .serviceCategory(category.name())
                    .hasActiveSubscription(hasSubscription)
                    .build();
        } else {
            // Switch to flat fee
            DriverRevenueModel model =
                    revenueModelRepository
                            .findByDriverId(driverId)
                            .orElseGet(
                                    () -> {
                                        DriverRevenueModel newRm = new DriverRevenueModel();
                                        newRm.setDriverId(driverId);
                                        newRm.setCountryCode(countryCode);
                                        newRm.setRegisteredAt(Instant.now());
                                        return newRm;
                                    });

            model.setRevenueModel(RevenueModel.FLAT_FEE.name());
            model.setServiceCategory(category.name());
            revenueModelRepository.save(model);

            log.info("Switched driver {} to FLAT_FEE for {}", driverId, category.name());
            return toDto(model, subscriptionService.hasActiveSubscription(driverId));
        }
    }

    private RevenueModelDto toDto(DriverRevenueModel model, boolean hasActiveSubscription) {
        return RevenueModelDto.builder()
                .driverId(model.getDriverId())
                .revenueModel(model.getRevenueModel())
                .serviceCategory(model.getServiceCategory())
                .hasActiveSubscription(hasActiveSubscription)
                .registeredAt(model.getRegisteredAt())
                .build();
    }
}
