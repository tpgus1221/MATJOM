package com.matjom.matjom.visit.entity;

public enum VisitState {
    ACTIVE, // 세션이 현재 진행 중 (사용자가 방문을 시작하고 아직 도착·종료 판정이 안 된 상태)
    ARRIVED, // 사용자가 장소(Place)의 지오펜스 조건을 충족해 '도착'으로 확정된 상태
    EXPIRED, // 세션이 시간 초과(TIMEOUT, 예: 30분)로 만료된 상태
    CANCELLED // 사용자가 방문을 수동 취소한 상태 (혹은 도중에 종료)
}
