package com.matjom.matjom.place.dto;

import com.matjom.matjom.place.entity.Place;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceInfoDTO {
    private Long placeId;
    private String name;
    private String address;
    private List<String> categories;
    private JsonNode workingHours;
    private JsonNode breakTime;
    private String phoneNumber;

    public static PlaceInfoDTO from(Place place, String address) {
        return PlaceInfoDTO.builder()
                .placeId(place.getId())
                .name(place.getName())
                .address(address)
                .categories(place.getCategory())
                .workingHours(place.getWorkingHours())
                .breakTime(place.getBreakTime())
                .phoneNumber(place.getPhoneNumber())
                .build();
    }
}
