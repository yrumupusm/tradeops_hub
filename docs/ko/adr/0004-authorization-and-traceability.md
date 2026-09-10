# ADR 0004: 서버 권한 제어와 요청 단위 추적

[한국어](0004-authorization-and-traceability.md) · [English](../../en/adr/0004-authorization-and-traceability.md)

> 과거 결정 기록입니다. 현재 BIS 구현은 [ADR 0005](0005-bis-sessions-and-provenance.md)와 [C4](../architecture/c4.md)를 참고하세요. 웹·API 분리 원칙은 유지하지만 당시 세부 구현을 현재 계약으로 해석하지 않습니다.

**상태:** 채택 · **기록일:** 2026-09-08

## 상황

화면에서 버튼을 숨겨도 API를 직접 호출할 수 있습니다. 데이터 변경에 대한 책임을 확인하려면 인증된 사용자와 요청을 저장 결과까지 연결해야 합니다.

## 대안

- 화면에서만 권한을 확인하면 API 직접 호출을 차단하지 못합니다.
- 서버 세션은 토큰 철회가 용이하지만 세션 저장소와 수명 관리가 필요합니다.
- 원본 전체를 로그에 기록하면 조사에 도움이 될 수 있지만 민감한 입력이 로그로 확산됩니다.

## 결정

Spring Security가 Bearer JWT를 검증하고 백엔드에서 역할을 확인합니다. 목록 갱신·거래 업로드·검토 기록은 `ADMIN`·`OPERATOR`, 관리자 상태 조회는 `ADMIN`만 허용합니다. `X-Correlation-Id`는 제한된 형식만 수용하고 실행 기록 및 응답에 연결합니다. 로그와 오류에는 원본 내용 대신 식별자와 제한된 오류 코드를 사용합니다.

검토 기록에는 담당자, 생성 시각, 대상 식별자, 점수, 처분, 요청 추적 ID를 남기는 정책을 채택합니다. 검토는 사람이 입력한 기록이며 자동 판단이 아닙니다.

## 결과와 적용 범위

웹 외의 API 클라이언트에도 동일한 접근 정책을 적용할 수 있습니다. 토큰은 만료까지 유효하며 현재 별도 갱신·철회 API는 없습니다. 로컬 계정 시더는 설정으로 활성화하며 외부 공개 환경의 계정 운영을 대신하지 않습니다.

검토 저장과 감사 저장은 `ScreeningReviewService`의 단일 트랜잭션과 `ScreeningReviewRepository`를 사용합니다. 조회자 쓰기를 거부하며 감사 저장 실패 시 검토 저장도 롤백합니다. `CorrelationIdFilter`는 보안 필터보다 먼저 실행하여 인증·권한 오류에도 요청 ID를 남깁니다. 목록 갱신과 가져오기는 실행 이력에 사용자를 남기지만 별도 `audit_events` 쓰기는 구현되어 있지 않습니다. 따라서 별도 감사 이벤트의 전체 변경 경로 적용은 후속 작업입니다.

## 근거

[SecurityConfig](../../../backend/src/main/java/io/tradeops/auth/SecurityConfig.java), [CorrelationIdFilter](../../../backend/src/main/java/io/tradeops/web/CorrelationIdFilter.java), 과거 ScreeningReviewController(Git 이력 참고), [후속 작업](../../../tasks/todo.md).
