package com.ktb4.team16.mulo.place.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.place.dto.PopularTracksResponseDto;
import com.ktb4.team16.mulo.place.message.PlaceMessage;
import com.ktb4.team16.mulo.place.service.PlaceService;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PopularTracksControllerTest {

    @Mock
    private PlaceService placeService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PlaceController(placeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsPopularTracksWithMessageAndRank() throws Exception {
        when(placeService.getPopularTracks(eq(List.of(10L, 20L))))
                .thenReturn(new PopularTracksResponseDto(
                        3L,
                        List.of(new PopularTracksResponseDto.Music(
                                1, 7L, "title", "artist", 3L))));

        mvc.perform(post("/api/places/popular-tracks/search")
                        .contentType(APPLICATION_JSON)
                        .content("{\"placeIds\":[10,20]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value(PlaceMessage.POPULAR_TRACKS_RETRIEVED.message()))
                .andExpect(jsonPath("$.data.recordCount").value(3))
                .andExpect(jsonPath("$.data.music[0].rank").value(1))
                .andExpect(jsonPath("$.data.music[0].musicTrackId").value(7));

        verify(placeService).getPopularTracks(List.of(10L, 20L));
    }

    @Test
    void rejectsMissingOrEmptyPlaceIds() throws Exception {
        mvc.perform(post("/api/places/popular-tracks/search")
                        .contentType(APPLICATION_JSON)
                        .content("{\"placeIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PLACE_IDS_REQUIRED"));
    }

    @Test
    void acceptsExactlyOneHundredPlaceIdsAndPassesAllToService() throws Exception {
        List<Long> placeIds = IntStream.rangeClosed(1, 100)
                .mapToObj(Long::valueOf)
                .toList();
        when(placeService.getPopularTracks(placeIds))
                .thenReturn(new PopularTracksResponseDto(0L, List.of()));

        mvc.perform(post("/api/places/popular-tracks/search")
                        .contentType(APPLICATION_JSON)
                        .content(requestBodyWithPlaceIds(100)))
                .andExpect(status().isOk());

        verify(placeService).getPopularTracks(placeIds);
    }

    @Test
    void rejectsOneHundredAndOnePlaceIdsBeforeCallingService() throws Exception {
        mvc.perform(post("/api/places/popular-tracks/search")
                        .contentType(APPLICATION_JSON)
                        .content(requestBodyWithPlaceIds(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("placeIds"))
                .andExpect(jsonPath("$.errors[0].code").value("PLACE_IDS_COUNT_EXCEEDED"));

        verify(placeService, never()).getPopularTracks(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void rejectsNullZeroAndNegativePlaceIdsBeforeCallingService() throws Exception {
        for (String requestBody : List.of(
                "{\"placeIds\":[null]}",
                "{\"placeIds\":[0]}",
                "{\"placeIds\":[-1]}")) {
            mvc.perform(post("/api/places/popular-tracks/search")
                            .contentType(APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                    .andExpect(jsonPath("$.errors[0].field").value("placeIds[0]"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_PLACE_ID"));
        }

        verify(placeService, never()).getPopularTracks(org.mockito.ArgumentMatchers.anyList());
    }

    private String requestBodyWithPlaceIds(int count) {
        String ids = IntStream.rangeClosed(1, count)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
        return "{\"placeIds\":[" + ids + "]}";
    }
}
