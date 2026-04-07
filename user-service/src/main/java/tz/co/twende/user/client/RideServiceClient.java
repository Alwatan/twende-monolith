package tz.co.twende.user.client;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.common.response.PagedResponse;
import tz.co.twende.user.dto.RideHistoryResponse;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideServiceClient {

    private final RestClient rideServiceRestClient;

    public PagedResponse<RideHistoryResponse> getRideHistory(UUID userId, int page, int size) {
        try {
            return rideServiceRestClient
                    .get()
                    .uri(
                            "/internal/rides/history?userId={userId}&page={page}&size={size}",
                            userId,
                            page,
                            size)
                    .header("X-User-Id", userId.toString())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to fetch ride history for user {}: {}", userId, e.getMessage());
            return PagedResponse.<RideHistoryResponse>builder()
                    .content(java.util.List.of())
                    .page(page)
                    .size(size)
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();
        }
    }
}
