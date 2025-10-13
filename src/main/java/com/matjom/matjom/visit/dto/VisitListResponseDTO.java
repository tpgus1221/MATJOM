package com.matjom.matjom.visit.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitListResponseDTO {
    private List<VisitCardResponseDTO> visits;
}
