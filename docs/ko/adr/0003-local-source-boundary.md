# ADR 0003: 재현 가능한 로컬 원본과 수집 경계

[한국어](0003-local-source-boundary.md) · [English](../../en/adr/0003-local-source-boundary.md)

> 과거 결정 기록입니다. 현재 BIS 구현은 [ADR 0005](0005-bis-sessions-and-provenance.md)와 [C4](../architecture/c4.md)를 참고하세요. 웹·API 분리 원칙은 유지하지만 당시 세부 구현을 현재 계약으로 해석하지 않습니다.

**상태:** 채택 · **기록일:** 2026-09-08

## 상황

추가·변경·삭제와 실패 처리를 반복 검증하려면 원본의 내용과 버전을 고정할 수 있어야 합니다. 외부 다운로드에 의존하면 데이터 변경이나 통신 장애가 기능 검증과 결합됩니다.

## 대안

- 외부 목록을 직접 수집하면 실제 갱신 흐름을 연결할 수 있지만 접근 조건, 형식 변경, 호출 제한을 관리해야 합니다.
- 각 테스트에서 임의 데이터를 만들면 빠르지만 화면·API·비교 테스트가 같은 원본을 사용하기 어렵습니다.

## 결정

시드가 고정된 가상 공급자의 두 버전을 XML·CSV 리소스로 관리합니다. `dataOrigin=FICTIONAL`을 검증하고 `FictionalWatchlistSource`가 요청 버전을 읽어 파서에 전달합니다. 식별자와 필드 규칙은 데이터 사전에 정의합니다.

실제 외부 어댑터는 현재 구현 범위에서 제외합니다. 추가할 경우 원본 접근 조건과 형식을 확인하고 설정으로 활성화하며, 다운로드 실패를 안전한 실패 실행으로 처리해야 합니다.

## 결과

네트워크 없이 동일한 변경 결과를 재현할 수 있습니다. 다만 현재 갱신은 저장소의 리소스를 다시 읽는 동작이며 실시간 외부 데이터 갱신이 아닙니다.

현재 서비스는 구체 클래스 `FictionalWatchlistSource`에 직접 의존합니다. 공통 소스 인터페이스와 스케줄러는 아직 구현되어 있지 않으며 새 공급자 도입 시 분리해야 합니다.

## 근거

[원본 로더](../../../backend/src/main/java/io/tradeops/watchlist/service/FictionalWatchlistSource.java), [생성기](../../../backend/src/main/java/io/tradeops/watchlist/fixture/FictionalWatchlistFixtureGenerator.java), [데이터 사전](../../../data/fixtures/watchlist-data-dictionary.md).
