# ADR 0002: 원본 버전별 스냅샷과 체크섬 기반 중복 처리

**상태:** 채택 · **기록일:** 2026-09-08

## 상황

현재 목록만 덮어쓰면 이전 값과 변경 이유를 추적하기 어렵습니다. 같은 입력을 다시 실행하더라도 변경 기록이 중복되어서는 안 됩니다.

## 대안

- 현재 테이블 덮어쓰기는 단순하지만 이전 버전을 잃습니다.
- 변경 이벤트만 저장하면 공간을 절약할 수 있지만 현재 상태를 재구성하고 검증하는 절차가 필요합니다.
- 표시 이름으로 비교하면 이름이 바뀌었을 때 동일 항목을 삭제·추가로 오인할 수 있습니다.

## 결정

실행 기록과 전체 스냅샷을 분리합니다. `provider + externalId`로 항목을 식별하고 정규화한 업무 필드의 해시로 변경 여부를 비교합니다. `ADDED`, `CHANGED`, `REMOVED`는 변경 행으로 저장하고 `UNCHANGED`는 집계에 포함합니다.

`provider + sourceVersion + payloadChecksum`에 유일 제약을 둡니다. 동일 키를 다시 실행하면 새 실행 이력만 남기고 기존 스냅샷을 반환합니다. 원본 체크섬은 원본 문자열에 기반하므로 내용이 같은 XML과 CSV도 서로 다른 스냅샷이 될 수 있습니다.

현재 목록은 가장 큰 스냅샷 ID에 속한 행을 조회합니다. 스냅샷의 내용과 집계는 완료 후 수정하지 않는 애플리케이션 규칙을 적용합니다. 기존 스냅샷을 다시 실행해도 해당 스냅샷으로 현재 목록을 되돌리지는 않습니다.

## 결과

실행 결과와 필드 차이를 함께 추적할 수 있고 실패한 입력이 기존 목록을 덮어쓰지 않습니다. 전체 스냅샷을 보존하므로 보관 기간과 용량 정책이 필요합니다.

현재 유일 제약은 동일 키 중복을 막지만 동시 실행의 비교 순서를 직렬화하지 않습니다. 스냅샷 불변성도 DB 트리거로 강제하지 않습니다. 저장 실패 시 트랜잭션 전체가 롤백될 수 있어 모든 DB 장애가 실패 실행으로 남는 것은 아닙니다. 동시성, 실패 이력 보존과 DB 불변성 검증은 후속 작업입니다.

## 근거

[WatchlistUpdateService](../../backend/src/main/java/io/tradeops/watchlist/service/WatchlistUpdateService.java), [V2 스키마](../../backend/src/main/resources/db/migration/V2__create_watchlist_run_snapshot_tables.sql), [갱신 통합 테스트](../../backend/src/test/java/io/tradeops/watchlist/WatchlistRunIntegrationTest.java).
