package com.twende.common.response;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PagedResponseTest {

    @Test
    void givenSpringPage_whenFrom_thenCorrectlyMapsAllFields() {
        List<String> items = List.of("a", "b", "c");
        Page<String> springPage = new PageImpl<>(items, PageRequest.of(1, 10), 23);

        PagedResponse<String> response = PagedResponse.from(springPage);

        assertEquals(items, response.getContent());
        assertEquals(1, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(23, response.getTotalElements());
        assertEquals(3, response.getTotalPages()); // 23 elements / 10 per page = 3 pages
        assertFalse(response.isLast());
    }

    @Test
    void givenLastPage_whenFrom_thenLastIsTrue() {
        List<String> items = List.of("a");
        Page<String> springPage = new PageImpl<>(items, PageRequest.of(2, 10), 21);

        PagedResponse<String> response = PagedResponse.from(springPage);

        assertTrue(response.isLast());
        assertEquals(2, response.getPage());
    }

    @Test
    void givenSinglePageResult_whenFrom_thenLastIsTrueAndTotalPagesIs1() {
        List<String> items = List.of("x", "y");
        Page<String> springPage = new PageImpl<>(items, PageRequest.of(0, 10), 2);

        PagedResponse<String> response = PagedResponse.from(springPage);

        assertTrue(response.isLast());
        assertEquals(1, response.getTotalPages());
        assertEquals(0, response.getPage());
        assertEquals(2, response.getTotalElements());
    }

    @Test
    void givenEmptyPage_whenFrom_thenContentIsEmptyAndLastIsTrue() {
        Page<String> springPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        PagedResponse<String> response = PagedResponse.from(springPage);

        assertTrue(response.getContent().isEmpty());
        assertTrue(response.isLast());
        assertEquals(0, response.getTotalElements());
        assertEquals(0, response.getTotalPages());
    }
}
