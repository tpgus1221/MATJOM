package com.matjom.matjom.visit.dto;

import com.matjom.matjom.visit.entity.ClientMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VisitPositionRequest {

    @NotNull(message = "위도(lat)는 필수입니다.")
    @DecimalMin(value = "-90.0", message = "위도(lat)는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "위도(lat)는 90 이하이어야 합니다.")
    private BigDecimal latitude;

    @NotNull(message = "경도(lng)는 필수입니다.")
    @DecimalMin(value = "-180.0", message = "경도(lng)는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "경도(lng)는 180 이하이어야 합니다.")
    private BigDecimal longitude;

    @DecimalMin(value = "0.0", message = "정확도(accuracyMeters)는 0 이상이어야 합니다.")
    private BigDecimal accuracyMeters;

    private ClientMode mode;

    @NotNull(message = "recordedAt은 필수입니다.")
    private OffsetDateTime recordedAt;

    public ClientMode modeOrDefault() {
        if (mode == null) {
            return ClientMode.NAVIGATION;
        }
        return mode;
    }
}
