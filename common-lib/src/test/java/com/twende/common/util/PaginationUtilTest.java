package com.twende.common.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PaginationUtilTest {

    @Test
    void givenNullSortByAndNullDirection_whenBuildPageRequest_thenUsesDefaults() {
        PageRequest result = PaginationUtil.buildPageRequest(0, 20, null, null);

        assertEquals(0, result.getPageNumber());
        assertEquals(20, result.getPageSize());
        Sort.Order order = result.getSort().getOrderFor("createdAt");
        assertNotNull(order);
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    void givenBlankSortBy_whenBuildPageRequest_thenDefaultsToCreatedAt() {
        PageRequest result = PaginationUtil.buildPageRequest(0, 20, "  ", null);

        Sort.Order order = result.getSort().getOrderFor("createdAt");
        assertNotNull(order);
    }

    @Test
    void givenSizeExceeds100_whenBuildPageRequest_thenClampedTo100() {
        PageRequest result = PaginationUtil.buildPageRequest(0, 200, "name", Sort.Direction.ASC);
        assertEquals(100, result.getPageSize());
    }

    @Test
    void givenSizeLessThan1_whenBuildPageRequest_thenClampedTo1() {
        PageRequest result = PaginationUtil.buildPageRequest(0, 0, "name", Sort.Direction.ASC);
        assertEquals(1, result.getPageSize());
    }

    @Test
    void givenNegativeSize_whenBuildPageRequest_thenClampedTo1() {
        PageRequest result = PaginationUtil.buildPageRequest(0, -5, "name", Sort.Direction.ASC);
        assertEquals(1, result.getPageSize());
    }

    @Test
    void givenNegativePage_whenBuildPageRequest_thenClampedTo0() {
        PageRequest result = PaginationUtil.buildPageRequest(-3, 10, "name", Sort.Direction.ASC);
        assertEquals(0, result.getPageNumber());
    }

    @Test
    void givenCustomSortFieldAndDirection_whenBuildPageRequest_thenRespected() {
        PageRequest result =
                PaginationUtil.buildPageRequest(2, 50, "updatedAt", Sort.Direction.ASC);

        assertEquals(2, result.getPageNumber());
        assertEquals(50, result.getPageSize());
        Sort.Order order = result.getSort().getOrderFor("updatedAt");
        assertNotNull(order);
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    void givenValidInputs_whenBuildPageRequest_thenReturnsCorrectPageRequest() {
        PageRequest result = PaginationUtil.buildPageRequest(5, 25, "name", Sort.Direction.DESC);

        assertEquals(5, result.getPageNumber());
        assertEquals(25, result.getPageSize());
        Sort.Order order = result.getSort().getOrderFor("name");
        assertNotNull(order);
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    void givenSizeExactly100_whenBuildPageRequest_thenNotClamped() {
        PageRequest result = PaginationUtil.buildPageRequest(0, 100, "name", Sort.Direction.ASC);
        assertEquals(100, result.getPageSize());
    }

    @Test
    void givenSizeExactly1_whenBuildPageRequest_thenNotClamped() {
        PageRequest result = PaginationUtil.buildPageRequest(0, 1, "name", Sort.Direction.ASC);
        assertEquals(1, result.getPageSize());
    }
}
