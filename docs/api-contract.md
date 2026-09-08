# API 계약

기준일: 2026-09-08. 현재 컨트롤러·서비스·보안 설정을 기준으로 작성했습니다. 기능별 구현 한계는 아래에 명시합니다.

## 공통 규칙

- 기본 경로: `/api/v1`. JSON 필드는 camelCase입니다.
- 인증: `Authorization: Bearer <token>`. 로그인과 상태 조회는 인증 없이 접근합니다.
- 응답 추적: `X-Correlation-Id`. 보안 검사 전에 ID를 부여하므로 401·403 응답에도 헤더와 오류 본문의 ID가 일치합니다. 요청 값은 8–64자의 영숫자·하이픈만 수용하며 나머지는 새 UUID로 대체합니다.
- 잘못된 로그인은 `401 AUTHENTICATION_FAILED`, 인증 누락은 `401 AUTHENTICATION_REQUIRED`, 역할 부족은 `403 ACCESS_DENIED`입니다.
- 알 수 없는 JSON 필드, 잘못된 enum과 본문 검증 실패는 `400 INVALID_REQUEST`입니다. 잘못된 페이지 범위·타입도 같은 형식으로 응답합니다. 데이터 접근 실패는 `503 PERSISTENCE_UNAVAILABLE`, 지원하지 않는 HTTP 메서드·미디어 타입과 누락된 multipart 항목은 기존 4xx 상태를 유지하며 안전한 `INVALID_REQUEST` 본문으로 응답합니다. 그 밖의 컨트롤러 예외는 `500 INTERNAL_ERROR`이며 원인 문자열과 스택을 응답에 포함하지 않습니다.
- 갱신·가져오기 업무 실패는 HTTP 200 본문의 `status=FAILED`일 수 있습니다. HTTP 코드만으로 성공을 판단하지 않습니다.

## 엔드포인트

| 메서드·경로 | 현재 접근 권한 | 입력 / 동작 |
| --- | --- | --- |
| GET `/health` | 공개 | API 상태 |
| POST `/auth/login` | 공개 | `username`, `password` |
| GET `/auth/me` | 인증 사용자 | 토큰 사용자·역할 |
| GET `/admin/status` | ADMIN | 관리자 권한 확인 |
| POST `/watchlist/runs` | ADMIN, OPERATOR | `fixtureVersion`, `format` |
| GET `/watchlist/runs` | 인증 사용자 | `page`, `size` |
| GET `/watchlist/entities` | 인증 사용자 | `provider`, `search`, `country`, `status`, `page`, `size` |
| GET `/watchlist/changes` | 인증 사용자 | `type`, `search`, `page`, `size` |
| POST `/imports` | ADMIN, OPERATOR | multipart `file` |
| GET `/transactions` | 인증 사용자 | `search`, `country` |
| GET `/transactions/monthly-summary` | 인증 사용자 | 월별 건수·금액 |
| POST `/screening-reviews` | ADMIN, OPERATOR | 거래·목록 식별자, 점수, 처분 |

`/actuator/health`도 공개 상태 엔드포인트이며 `/api/v1` 경로 바깥에 있습니다. 상세 구성 정보는 숨깁니다.

## 상태와 로그인

`GET /health`는 `status="ok"`, `service="tradeops-api"`, `checkedAt`, `correlationId`를 반환합니다. 이 응답은 DB 가용성을 검사하지 않습니다.

로그인은 사용자명 최대 120자, 비밀번호 8–160자를 받습니다. 성공 응답은 `accessToken`, `tokenType="Bearer"`, `expiresInSeconds`, `user: {username, role}`입니다. 토큰 기본 유효 시간은 60분이며 설정할 수 있습니다. `GET /auth/me`는 `{username, role}`을 반환합니다.

## 목록 갱신

```json
{"fixtureVersion":"2026-01-A","format":"XML"}
```

버전은 3–80자의 영숫자·하이픈이며 형식은 `XML` 또는 `CSV`입니다. 지원 리소스는 `2026-01-A`, `2026-02-B`입니다.

응답 필드: `runId`, `snapshotId`(실패 시 null), `status`, `idempotent`, `totalRows`, `addedCount`, `changedCount`, `removedCount`, `unchangedCount`, `safeErrorCode`, `correlationId`.

동일 공급자·버전·원본 체크섬은 새 스냅샷과 변경을 만들지 않습니다. 실행 이력은 새로 생성합니다. 원본 형식이 달라 체크섬이 달라지면 별도 스냅샷일 수 있습니다. 존재하지 않는 버전은 `FAILED / FIXTURE_NOT_FOUND`를 반환하며 현재 목록은 바뀌지 않습니다.

## 목록 조회

페이지 기본값은 `page=0`, `size=20`이며 `page>=0`, `1<=size<=100`입니다. 응답은 `{items, page, size, totalElements, totalPages}`이고 정렬은 ID 내림차순으로 고정됩니다. 외부 `sort` 인자는 없습니다.

| 조회 | items의 필드 |
| --- | --- |
| runs | `runId, requestedVersion, format, status, totalRows, addedCount, changedCount, removedCount, unchangedCount, safeErrorCode, correlationId` |
| entities | `externalId, entityName, aliases, countryCode, listingReason, status, sourceVersion` |
| changes | `type, externalId, entityName, differencesJson, sourceVersion` |

entities·changes는 최신 스냅샷만 조회합니다. 미지원 공급자 또는 스냅샷이 없으면 빈 페이지입니다. `differencesJson`은 객체가 아닌 JSON 문자열이며 변경 필드별 `previousValue`·`currentValue`를 담습니다. 추가·삭제 항목의 차이 값은 `"{}"`입니다.

## 거래 가져오기와 조회

가져오기는 UTF-8의 단순 쉼표 구분 CSV이며 헤더 순서는 다음과 같습니다. 인용부호로 감싼 쉼표 값은 지원하지 않습니다.

```text
transactionId,transactionDate,counterpartyName,countryCode,amountUsd,currency,dataOrigin
```

각 필드 규칙은 [데이터 사전](../data/fixtures/transaction-data-dictionary.md)을 참조합니다. 결과는 `runId, status, acceptedCount, rejectedCount, duplicateCount, safeErrorCode, correlationId`입니다. 일부 행 거절·중복은 `COMPLETED_WITH_ERRORS`이며 행 번호와 오류 코드가 DB에 저장됩니다. 같은 파일은 새 실행 없이 `FAILED / DUPLICATE_FILE`과 null `runId`를 반환합니다. 가져오기 이력과 거절 행의 조회 API는 아직 없습니다.

거래 검색은 `transactionId, transactionDate, counterpartyName, countryCode, amountUsd, currency`의 배열을 반환합니다. 날짜·ID 내림차순 최대 100건이며 페이지 메타데이터는 없습니다. 월별 집계는 `month, transactionCount, totalAmountUsd`의 배열입니다. 연도·월별 `EXTRACT` 집계로 PostgreSQL과 H2에서 동일한 `YYYY-MM` 응답을 제공합니다.

## 검토 기록

```json
{
  "transactionId":"TX-101",
  "watchlistExternalId":"FIC-001",
  "matchScore":0.75,
  "disposition":"NEEDS_FOLLOW_UP"
}
```

식별자는 비어 있지 않아야 하고 점수는 0–1입니다. 처분 값은 `CONFIRMED_MATCH`, `CLEARED`, `NEEDS_FOLLOW_UP`입니다. 응답은 `{status:"RECORDED", correlationId}`입니다. 사용자와 생성 시각은 서버에서 저장하며 자동 매칭 계산은 없습니다.

조회자는 403, 인증 없는 요청은 401입니다. `disposition`의 null·빈 값·잘못된 값과 범위를 벗어난 점수는 `400 INVALID_REQUEST`입니다. 거래 ID는 최대 100자, 목록 ID는 최대 160자입니다. 검토와 감사 이벤트를 하나의 서비스 트랜잭션으로 저장하며, 감사 이벤트의 `entityId`는 생성된 검토 ID를 가리킵니다. 대상 식별자의 실제 존재 여부 검증은 아직 없습니다.

## 미구현 계약

원본 설정, 스케줄 실행, 가져오기 이력·거절 행 조회, 검토 목록 조회, 감사 로그 조회 API는 아직 없습니다. [작업 목록](../tasks/todo.md)과 [arc42](arc42.md)에서 후속 범위를 관리합니다.
