# 운영 안내

[한국어](runbook.md) · [English](../en/runbook.md)

## 설정과 시작

Java 17, Maven, Node.js 22 이상, Docker가 필요합니다.

1. `.env.example`을 `.env`로 복사하고 DB 비밀번호와 운영 책임자 초기 비밀번호를 설정합니다.
2. `docker compose up -d`로 PostgreSQL을 실행합니다.
3. `powershell -File scripts/start-api-local.ps1 -MavenPath <mvn.cmd 경로>`로 API를 실행합니다.
4. 다른 터미널의 `frontend`에서 `npm ci`, `npm run dev`를 실행합니다. API 기본 주소는 `http://127.0.0.1:8081`입니다. 다르면 웹 프로세스의 `API_PROXY_TARGET`을 지정합니다.
5. `http://localhost:3000`에 접속해 설정한 운영 책임자 계정으로 로그인합니다. **우려거래자 수집 관리**에서 첫 수집을 실행합니다.

법령 서비스는 별도 터미널에서 `services/law-search`로 이동해 실행합니다. 해당 디렉터리의 `.env.example`을 참고해 서비스 전용 `.env`를 준비하고 `powershell -File scripts/server.ps1 start -MavenPath <mvn.cmd 경로>`를 실행합니다. PostgreSQL·Qdrant·실제 모델 설정은 [법령 운영 안내](../../services/law-search/docs/ko/runbook.md)를 따릅니다. Hub API의 `LAW_RAG_BASE_URL`은 법령 서버 주소(로컬 기본 `http://127.0.0.1:8080`)로 설정합니다.

기존 DB·벡터 컬렉션이 있다면 그대로 연결하며 디렉터리 통합 때문에 재수집·재임베딩하지 않습니다. 법령 Compose 프로젝트 이름도 기존 이름으로 고정했습니다. Hub DB 기본 포트는 5433, 법령 DB는 5432이며 실제 환경 설정을 우선합니다.

운영 책임자 계정은 최초 생성 시에만 초기 비밀번호를 사용합니다. 기존 DB 계정 비밀번호를 환경 변수로 덮어쓰지 않습니다. 운영 환경에서는 HTTPS와 `SESSION_COOKIE_SECURE=true`를 설정합니다. 원본 보관 경로를 영속 저장소로 연결하고 PostgreSQL과 함께 백업합니다.

로컬 API 실행 스크립트만 루트 `.env`를 읽는다. 프런트엔드의 `API_PROXY_TARGET`과 배포 환경 변수는 해당 프로세스에 별도로 전달한다. 환경 변수 목록은 [.env.example](../../.env.example)에 있다.

첫 운영 책임자는 `OWNER_USERNAME`, `OWNER_INITIAL_PASSWORD`로 만든다. 이미 존재하면 비밀번호를 덮어쓰지 않는다. 역할 편집은 없다. 기존 설치에서 JWT는 더 이상 사용하지 않으며 다시 로그인해야 한다. Flyway V1–V4는 수정하지 않고 V5–V8을 추가 적용한다. PostgreSQL 계정은 pg_trgm 확장을 생성할 권한이 필요하다.

기본 원본 경로는 API 프로세스 기준 상대 경로다. 운영에서는 `BIS_STORAGE_PATH`를 절대 경로의 영속 볼륨으로 지정한다. 웹 공개 주소는 HTTPS로 제공하고 `SESSION_COOKIE_SECURE=true`를 설정한다. API·DB는 필요한 네트워크에서만 접근할 수 있게 둔다.

## 첫 수집과 확인

로그인 후 **우려거래자 수집 관리 → 전체 수집**을 실행한다. DPL/EL의 상태가 각각 완료인지 보고 행 수와 최근 성공 시각을 확인한다. **우려거래자 검색**에서 이름·출처·국가 조건을 적용하고 상세 원문과 원본 파일을 확인한다. CSV는 검색 화면에 표시한 버전 전체 결과를 내려받는다.

## 정기 실행

기본값은 매주 월요일 09:00, Asia/Seoul이다. 다음 예약은 DB에 남는다. 첫 설치에서는 다음 월요일을 예약하므로 즉시 필요한 데이터는 수동 수집한다. 서버 중단으로 놓친 예약은 재기동 후 한 번 수행한다. 수집 도중 중단된 실행은 PROCESS_INTERRUPTED로 종료하며 기존 현재 목록을 유지한다.

스케줄러는 단일 API 인스턴스만 지원한다. 동일 DB로 API 인스턴스를 여러 개 실행하지 않는다. 예약 비활성화는 `BIS_SCHEDULE_ENABLED=false`다.

## 오류 대응

| 상태 / 오류 | 확인 및 대응 |
| --- | --- |
| DOWNLOAD_LINK_MISSING / AMBIGUOUS | 공식 안내 페이지의 문단·링크를 확인하고 discovery 규칙을 수정·테스트한다. |
| SOURCE_URL_NOT_ALLOWED | 공식 주소인지 직접 확인한 후 필요한 환경 설정만 변경한다. 임의 호스트를 넓게 허용하지 않는다. |
| SCHEMA_CHANGED / CSV_INVALID | 원본 열과 형식을 확인하고 parser 계약·가상 회귀 테스트를 함께 갱신한다. |
| ROW_VALIDATION_FAILED | 실행 상세의 오류 행 번호와 원본 파일을 대조한다. 오류를 숨기고 반영하지 않는다. |
| ROW_COUNT_DROP / HELD | 직전 버전과 추가·제외를 확인한다. 운영 책임자가 올바른 감소로 확인하면 반영한다. |
| SOURCE_RETRY_LATER / TEMPORARILY_UNAVAILABLE | 잠시 후 수동 재시도한다. 실패 중에는 직전 자료를 계속 조회한다. |
| COLLECTION_STORAGE_FAILED | 파일 권한·디스크 공간·DB 상태를 확인한다. 원본/DB를 함께 백업한다. |
| CSRF_INVALID | 로그인 화면에서 다시 인증한다. 만료된 요청을 자동 재실행하지 않는다. |

검증된 공식 다운로드 주소 변경은 자동 반영하고 출처 경고·실행의 이전/신규 주소에 남긴다. 안내 페이지 자체가 공식 도메인 밖으로 이동하면 운영자가 확인 후 설정을 바꿔야 한다.

## 계정과 감사

운영 책임자가 임시 비밀번호로 계정을 만든다. 사용자는 첫 로그인 후 변경한다. 비활성화·초기화·비밀번호 변경은 해당 계정의 모든 세션을 폐기한다. 운영 책임자 자신은 내 계정에서 비밀번호를 변경한다. 감사 이력은 작업·행위자·기간으로 필터링한다. 검색어는 개인 검색 이력에만 남으며 본인이 삭제할 때까지 유지한다.

## 백업과 검증

DB와 원본 보관 디렉터리를 함께 백업한다. 복원 검증은 별도 DB/디렉터리에서 수행한다. 원본 자동 삭제 기능은 없다. `storage/`, `artifacts/`, `.env`, 빌드 결과는 Git에 올리지 않는다. 공개 BIS 자료도 저장소 fixture로 사용하지 않는다.

[검증 안내](evaluation-harness.md)의 서버 없는 검사, 실제 PostgreSQL 검사, 런타임/브라우저 검사를 구분한다. 원본 이름이나 세션 정보 없이 상태·건수·안전한 오류 코드만 검증 결과에 남긴다.


법령 검색은 별도 RAG 서비스와 연동한다. 질문·인용·조문 이력/비교와 권한·데이터 보존은 [법령 검색 통합](law-integration.md)을 참고한다.

## 통합 저장소 검증

루트에서 `powershell -File scripts/verify-monorepo.ps1 -MavenPath <mvn.cmd 경로>`를 실행한다. Hub 검사와 법령 JavaScript 문법·전체 Maven 테스트를 실행하며, 법령 테스트는 임시 H2·mock 설정으로 실제 DB와 모델을 사용하지 않는다. `scripts/verify-local.ps1`은 Hub 서버 없이 단위 테스트·검증 도구 테스트·프런트엔드 타입 검사를 실행한다. 실제 PostgreSQL·런타임 검사는 [검증 안내](evaluation-harness.md)를 따른다.
