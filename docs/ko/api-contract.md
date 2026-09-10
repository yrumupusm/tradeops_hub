# API 계약

[한국어](api-contract.md) · [English](../en/api-contract.md)

기본 경로는 `/api/v1`이다. 브라우저는 Next.js 동일 출처 프록시를 사용한다. JSON DTO는 camelCase, DB 기반 행 객체는 아래 명시한 snake_case 필드를 사용한다. 알 수 없는 요청 필드는 400으로 거부한다. 오류에는 제한된 `code`, 안전한 `message`, `correlationId`와 선택적인 `timestamp`를 포함한다. 모든 API 응답에 `X-Correlation-Id`를 보낸다.

## 인증

1. GET `/auth/csrf` → `{token, headerName}`와 세션 쿠키.
2. POST `/auth/login`: `{username,password}`와 `X-CSRF-TOKEN` → `{id,username,owner,mustChangePassword}`. 세션 ID를 교체한다.
3. 후속 요청에는 HttpOnly `SESSION` 쿠키를 보낸다. 로그인·로그아웃·검색·내보내기를 포함한 모든 POST/PATCH/DELETE 요청은 CSRF가 필요하다.
4. GET `/auth/me`는 현재 사용자를 반환한다. POST `/auth/logout`은 세션을 무효화한다.

유휴 세션 만료 기본값은 30분이다. 사용자명/IP에 대해 10분 안에 5회 인증 실패하면 429로 제한한다. 인증 없음은 401, CSRF 누락·만료는 403 `CSRF_INVALID`, 권한 부족은 403 `ACCESS_DENIED`다. 강제 변경 계정은 me/csrf/logout/password만 접근한다. 비밀번호 등 인증 비밀정보는 응답에 포함하지 않는다.

## 계정과 감사

| 메서드 / 경로 | 입력 / 결과 | 권한 |
| --- | --- | --- |
| GET /users | id, username, enabled, must_change_password, created_at 배열 | 운영 책임자 |
| POST /users | {username,password}; 임시 비밀번호, 본문 없는 200 | 운영 책임자 |
| PATCH /users/{id} | {enabled:boolean}; 필수 필드, 본문 없는 200 | 운영 책임자 |
| POST /users/{id}/reset-password | {password}; 모든 세션 폐기, 변경 강제 | 운영 책임자 |
| POST /account/password | {currentPassword,newPassword}; 모든 세션 폐기 | 본인 |
| GET /audit-events | actor,event,from,to,page,size; 페이지별 이벤트 | 운영 책임자 |

운영 책임자는 설정으로 지정하며 비활성화·초기화 API에서 보호한다. 비밀번호는 최소 8자, 최대 UTF-8 72바이트이고 새 비밀번호는 현재 값과 달라야 한다. 사용자명은 3–120자의 ASCII 영문·숫자 또는 `@._+-`다. 감사 날짜는 ISO 시각이며 from은 포함, to는 제외한다. 감사 행에는 event_type, actor_username, entity_type, entity_id, correlation_id, occurred_at과 JSON 문자열 detail이 있다. 원본 검색어·인증 비밀정보는 제외한다.

## 수집

| 메서드 / 경로 | 계약 |
| --- | --- |
| GET /watchlist/sources | {items,settings}; 출처의 code/current_snapshot_id/data_updated_at/active_run_id/URL/last_checked/last_success/next_scheduled/alert_code; settings의 guideUrl, scheduleEnabled, cron, zone |
| POST /watchlist/runs | {source:"DPL"\|"EL"\|"ALL"} → 202 {status:"RUNNING",runIds:{DPL?:id,EL?:id}} |
| GET /watchlist/runs | page,size → {items,totalElements,page,size} |
| GET /watchlist/runs/{id} | {run,issues}; 서버 file_path 제외 |
| GET /watchlist/runs/{id}/changes | page,size → 추가·제외 행 요약 배열 |
| POST /watchlist/runs/{id}/approve | 운영 책임자만, 본문 없음; 보류 실행을 현재 버전으로 반영 |
| GET /watchlist/files | page,size → 파일이 있는 실행의 페이지 결과 |
| GET /watchlist/files/{runId}/download | 원본 첨부 파일, 접근 감사 기록 |

`data_updated_at`은 현재 스냅샷 생성 시각이며 최초 반영 전에는 null이다. 화면에는 최근 데이터 변경일로 표시한다. 동일 파일 재확인 시 유지하고 파서 갱신 재처리를 포함해 새 스냅샷이 생기면 바뀐다. 제공 기관의 Last-Modified가 아니라 로컬 데이터 버전 시각이다.

상태는 RUNNING, COMPLETED, FAILED, HELD이고 단계는 DISCOVERING, DOWNLOADING, VALIDATING, SAVING, FINISHED다. 실행 필드는 source_code, trigger_type, actor, correlation_id, 시각, 안내·다운로드·최종·이전 URL, 페이지·파일 해시, file_size, 행 집계, snapshot_id, previous_snapshot_id, parser_version, idempotent와 안전한 error_code를 포함한다. 검증 문제는 row_number/code/severity다. 파싱 실패 시에도 원본 파일로 행을 확인할 수 있다.

고정 10,000행 제한은 없다. HTTP 다운로드 최대 32 MiB, 요청 제한 30초, 연결 제한 10초, 리다이렉트 최대 4회, 시도 최대 3회다. 긴 Retry-After는 무기한 대기 대신 안전한 실패로 처리한다. 실패·모호한 다운로드는 현재 버전을 유지한다. 30% 초과 감소는 HELD다. 더 새 현재 버전으로 대체되지 않은 보류 버전만 승인할 수 있다.

## 검색·상세·CSV

GET `/search-history/recent`는 본인 이력에서 비어 있지 않은 검색어를 앞뒤 공백 제거·대소문자 무시로 묶어 최신순 최대 5개의 `{id,q}`를 반환한다. DELETE `/search-history/recent/{id}`는 해당 검색어의 본인 이력을 조건·버전이 다른 기록까지 삭제한다. 다른 사람의 ID는 404다. 삭제는 CSRF와 감사 기록이 필요하며 검색어는 감사에 넣지 않는다. 검색창 아래에는 조용한 텍스트와 개별 삭제 버튼으로 표시한다. 클릭하면 현재 화면 조건과 최신 출처 스냅샷으로 재검색한다. 기존 이력 재실행은 당시 고정 버전을 유지한다.

POST `/watchlist/search`, POST `/watchlist/exports` 입력:

```json
{"q":"FICTIONAL BEACON","mode":"HYBRID","source":"","country":"","sort":"score","page":0,"size":20,"snapshots":{"DPL":1,"EL":2},"saveHistory":true}
```

모든 필드는 선택 사항이다. 기본값은 빈 검색어·출처·국가, HYBRID, score 정렬, page 0, size 20, 이력 저장 안 함이다. mode는 BASIC/SIMILAR/HYBRID, sort는 score/name/country다. 검색어는 최대 250자, 국가는 빈 값 또는 두 글자 코드다. 일반 페이지 범위는 0–100000, size는 1–100이며 출처는 빈 값/DPL/EL이다.

snapshots를 생략하면 현재 버전을 확정한다. 명시적인 빈 객체는 빈 결과로 고정한다. ID는 지정 출처에 속해야 한다. 응답은 items,totalElements,page,size,snapshots,elapsedMs,variants다. 항목에는 id,snapshot_id,source_code,row_number,name,country,address,matched_name,priority,score,duplicate_count가 있다. priority는 0 이름 일치, 1 별칭 일치, 2 부분 일치, 3 유사 후보(trigram 기준 0.42)다. 정규화 이름·공백 제거 표기·정렬한 단어를 비교한다. 같은 원본 행만 묶고 다른 행은 별도로 유지한다. CSV는 page/size를 무시하고 조건·버전을 유지해 **일치하는 전체 행**을 내보낸다. UTF-8 BOM, 필드 인용과 수식 방지를 적용한다.

CSV는 한글 컬럼명을 사용한다. 출처 다음에 원본 이름·다른 이름·국가·주소·도시·주/지역·우편번호·발효일·연방 관보·표준 명령을 두고, DPL/EL별 항목(만료·변경일·변경 설명, 목록·기업 정보, 해제·면제·만료일, 허가 요건, 선박·주소·대체 정보, 원문 링크)을 둔다. 날짜 문자열과 국가 표기를 포함해 저장된 원본 값을 사용하며 없는 필드는 빈칸이다. DPL 만료일과 EL 해제·면제·만료일은 구분한다. 일치 방식·유사도·중복 행 수·내부 스냅샷 및 행 번호는 제외한다. 모든 값에 수식 방지를 적용하고 출처 필터와 무관하게 같은 컬럼 구성을 사용한다. 원본 다운로드는 변경하지 않는다.

GET `/watchlist/records/{id}`는 저장 행, raw_json, aliases_json, dates_json, validation과 출처·실행·스냅샷 연결을 반환한다. GET `/watchlist/countries`는 현재 데이터의 국가 코드·건수를 반환한다. 상세는 불변 버전에 속하며 화면에서 검색 결과 복귀 링크를 유지한다.

## 개인 이력

GET `/search-history?page=0&size=20`은 본인 기록의 query_json, total_results, elapsed_ms, created_at 등을 반환한다. DELETE `/search-history/{id}`는 본인 항목만 삭제하며 타인 ID는 404다. DELETE `/search-history`는 본인 이력을 모두 삭제한다. 만료는 없다. 운영 책임자도 소유권 제한을 우회하지 못한다. 명시적 검색은 이력을 저장하고 요청 상관 ID와 함께 사용자·건수·버전 ID를 감사에 기록하되 검색어는 제외한다. 페이지 이동·내보내기는 새 이력을 만들지 않는다.

## 제거한 경로와 상태 확인

거래·가져오기·심사·역할 관리 API는 없고 과거 테이블은 유지한다. GET `/health`는 공개이며 status/service/checkedAt/correlationId를 포함한다. GET `/actuator/health`는 민감한 세부 정보를 공개하지 않는다.

검증 정책 bis-csv-2는 구조·필수 이름 실패만 행 문제로 만든다. 선택적 국가·날짜 변환은 경고를 만들지 않으며 원문을 보존한다. 과거 완료 집계는 바꾸지 않는다. 스냅샷 재사용은 파일 해시와 파서 버전 모두 같아야 하므로 파서 갱신 후 다음 수집은 동일 바이트도 다시 처리한다.


법령 검색은 별도 RAG 서비스와 연동한다. 질문·인용·조문 이력/비교와 권한·데이터 보존은 [법령 검색 통합](law-integration.md)을 참고한다.


운영 책임자 전용 법령 관리 API의 허용 조회·실행 목록, 검증, CSRF, 응답 필드와 감사 계약은 [법령 통합](law-integration.md#운영-책임자-관리-화면)을 따른다.
