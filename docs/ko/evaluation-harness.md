# 검증 체계

[한국어](evaluation-harness.md) · [English](../en/evaluation-harness.md)

## 검증 단계

- `scripts/verify-local.ps1 -MavenPath <mvn.cmd>`: 서버 없는 단위·파서 테스트, 검증 도구 테스트와 TypeScript 검사. PostgreSQL 전용 테스트는 명시적으로 건너뛴다.
- `BisPostgresIntegrationTest`: 실제 PostgreSQL 마이그레이션, 원자적 반영, 감소량 승인, 실패 보존, 링크 탐색·중복 처리, 출처 동시성·재시작, 버전 고정·개인 검색과 성능.
- `scripts/verify-runtime.ps1`: 실행 중인 API·웹의 영속 세션·CSRF, 비밀번호 변경 강제, 운영 책임자 권한, 이력 격리, CSV 계약, 감사 정보 제한과 한국어 HTML. 새로운 격리 DB와 `VERIFY_OWNER_USERNAME`/`VERIFY_LOCAL_PASSWORD` 환경 변수의 계정 정보가 필요하다.
- `scripts/verify-final.ps1`: 로컬 검사, 빌드, 임시 PostgreSQL, DB 테스트, 격리 API·웹, 런타임 시나리오, 증거와 저장 결과 검증. 자신이 만든 자원만 정리하며 `-KeepRuntime`은 성공한 경우에만 자원을 유지한다.
- 브라우저 인수 검사: 로그인 → 검색·필터·페이지·상세·뒤로·CSV, 수집 → 실행 상세·파일·버전, 계정·강제 변경, 오류와 데스크톱·모바일 탐색. HTTP HTML 검사만으로 대체할 수 없다.

## PostgreSQL 테스트

격리 검증 사용자가 소유한 **tradeops_fixtures라는 이름의 폐기 가능한 DB**에서만 실행한다. 테스트는 BIS 테이블을 비우기 전 JDBC DB 이름을 확인한다. `TRADEOPS_TEST_PG=true`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`를 설정하고 backend에서 `mvn -Dtest=BisPostgresIntegrationTest test`를 실행한다. 실제 애플리케이션 DB를 지정하지 않는다.

테스트 데이터는 고정 순서로 생성한다(시드 식별자 `BIS-FIXTURE-20260909`). 가상 Northline Beacon과 번호가 붙은 Fictional Meridian 레코드를 사용한다. 생성 파일·증거는 Git에서 제외한 artifacts에 저장한다. BIS HTTP 클라이언트만 모의 처리하고 파싱·링크 탐색·저장·PostgreSQL은 실제로 실행한다. 외부 네트워크를 사용하지 않는다. 10,000행 성능 검사는 준비 실행 후 5개 스레드로 100회 요청하고 p95 <1000 ms, CSV <10000 ms를 확인하며 집계 시간만 남긴다.

## 검증 증거

`harness/scenarios.json`은 HTTP 시나리오 ID와 검증 항목을 고정한다. 증거에는 상태·검사명, 허용된 요청 경로·상태·상관 ID, 소스·시나리오 해시와 시각만 포함한다. 쿠키·계정 정보·요청 및 응답 본문·원본 검색어는 제외한다. 검증기는 누락·실패·건너뛴 검사, 오래된 결과, 알 수 없는 필드와 달라진 소스 해시를 거부한다. 증거 경로는 Git에서 제외한 artifacts 내부여야 한다.

실제 BIS 연결은 별도 실환경 인수 검사다. 공식 안내 링크로 DPL·EL을 수집해 완료 상태, 원본 다운로드와 검색 가능한 스냅샷을 확인한다. 공개 자료의 행 수는 변하므로 고정 테스트 기준으로 삼지 않는다. 실데이터와 이를 담은 브라우저 캡처는 Git에서 제외한다.

## 현재 완료 상태

이 리비전에서 실제 수행한 검사와 브라우저 확인은 [작업 목록](../../tasks/todo.md)을 참고한다. 과거 거래·JWT 테스트의 성공을 BIS 구현의 검증 결과로 재사용하지 않는다.
