# 운영 안내

[한국어](../ko/runbook.md) | [English](../en/runbook.md)

[README](../../README.md)는 실행 순서와 전체 구조를 소개합니다. 이 문서는 기존 README의 환경 설정·서버 관리·데이터 작업 설명을 모은 운영 참고 문서입니다.

## 환경 설정

Spring Boot는 프로젝트 루트의 `.env`를 설정 파일로 읽습니다. 새 환경에서는 [.env.example](../../.env.example)의 변수 이름을 사용하고, 기존 `.env`와 데이터를 보존합니다.

| 설정 | 용도 |
| --- | --- |
| `SERVER_PORT` | 애플리케이션 포트; 기본 8080 |
| `SPRING_DATASOURCE_*` | DB URL, 드라이버, 사용자와 비밀번호 |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | DB 스키마 처리; 기존 데이터에 `create-drop` 사용 금지 |
| `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Compose의 PostgreSQL 설정 |
| `LLM_PROVIDER`, `EMBEDDING_PROVIDER` | `mock` 또는 `openrouter` |
| `OPENROUTER_API_KEY` | 공통 키; `LLM_API_KEY`, `EMBEDDING_API_KEY`로 분리 가능 |
| `LLM_MODEL`, `EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS` | 모델과 임베딩 차원 |
| `VECTOR_PROVIDER`, `VECTOR_COLLECTION` | `inmemory` 또는 `qdrant`, 사용할 컬렉션 |
| `QDRANT_BASE_URL`, `QDRANT_PORT`, `QDRANT_API_KEY` | Qdrant 연결 설정 |
| `RERANKER_PROVIDER`, `RERANKER_MODEL`, `RERANKER_API_KEY`, `RERANKER_BASE_URL` | `mock` 또는 `cohere` 재정렬 설정 |
| `HTTP_TIMEOUT_SECONDS` | 외부 HTTP 연결·읽기 제한; 기본적으로 `LLM_TIMEOUT_SECONDS` 참조 |
| `EMBEDDING_MAX_CHARS`, `EMBEDDING_BATCH_SIZE`, `EMBEDDING_BATCH_DELAY_MILLIS` | 임베딩 본문 길이·배치 크기·호출 간격 |
| `EMBEDDING_RATE_LIMIT_RETRY_DELAY_MILLIS`, `EMBEDDING_RATE_LIMIT_MAX_RETRIES` | 호출 제한 시 재시도 간격·횟수 |
| `LAW_SOURCE_DIR`, `LAW_SOURCE_REPO_URL`, `LAW_SOURCE_BRANCH` | 원문 경로, 저장소, 브랜치 |
| `LAW_SOURCE_INCLUDE_DIRS`, `LAW_SOURCE_INCLUDE_FILES` | 수집할 법령 폴더와 파일 범위 |
| `REFERENCE_DATA_ENABLED` | 초기 검증용 예제 조문 적재 여부 |
| `ADMIN_REINDEX_ENABLED` | 관리자 재색인 허용; 기본 false |

실데이터 환경에서는 PostgreSQL 연결, 영속 볼륨, Qdrant 컬렉션을 먼저 확인합니다. `.env.example`은 PostgreSQL 설정과 mock 제공자가 섞인 시작 예시이며, 복사만으로 실모델 검색 환경이 완성되지 않습니다. 애플리케이션 기본값의 H2·메모리 저장소는 영속 실행과 구분합니다.

임베딩 모델과 차원은 기존 컬렉션과 맞아야 합니다. 모델 변경은 단순 서버 재시작과 다른 데이터 작업입니다. `EMBEDDING_MAX_CHARS`보다 긴 조문은 임베딩 요청에서 앞부분으로 제한됩니다. 배치 실패 시 단건 재시도를 수행하며, 최종 실패는 부분 실패 상태에 기록됩니다.

## 실행·중지·재시작

프로젝트 루트에서 실행합니다. Maven이 PATH에 없다면 `server.ps1`에 `-MavenPath <mvn.cmd 경로>`를 전달합니다.

```powershell
powershell -File scripts/preflight.ps1
powershell -File scripts/infra.ps1 up
powershell -File scripts/server.ps1 start
powershell -File scripts/server.ps1 status
```

외부 제공자 필수 설정을 엄격하게 확인하려면 `preflight.ps1 -Strict`를 사용합니다. 키의 존재 여부만 검사하고 값을 출력하지 않습니다. 이미 생성된 컨테이너를 다시 시작할 때는 `docker compose start postgres qdrant`도 사용할 수 있습니다.

```powershell
powershell -File scripts/server.ps1 stop
powershell -File scripts/server.ps1 restart
```

`restart`는 PID 파일과 설정 포트에서 찾은 프로세스를 대상으로 종료한 뒤 실행합니다. 다른 프로젝트가 같은 포트를 쓰지 않는지 먼저 확인합니다. 단순 점검은 재시작 대신 `status`와 상태 API를 사용합니다. 실행 로그는 `target/bootrun.out.log`, `target/bootrun.err.log`에 있습니다.

DB와 벡터 저장소만 점검하려면 `scripts/infra.ps1 status`를 사용합니다. `infra.ps1 down`은 컨테이너를 내리는 명령이므로 애플리케이션 종료와 구분합니다. 데이터 보존을 위해 볼륨 삭제 옵션을 추가하지 않습니다.

## 법령 수집과 색인

첫 환경에서 원문 Git 저장소를 동기화하거나 기존 Markdown 경로를 수집합니다. 작업 경로와 수집 범위를 확인한 후 관리 화면 또는 [관리 API](api-contract.md)를 사용합니다.

- `POST /api/admin/sync-source`: 원문 Git 동기화. `ingestAfterSync=true`이면 수집·색인까지 수행합니다.
- `POST /api/admin/ingest-local`: 지정한 로컬 Markdown에서 새 스냅샷을 수집·색인합니다.
- `POST /api/admin/reindex`: 저장된 조문을 재임베딩합니다. `ADMIN_REINDEX_ENABLED=true` 설정 후 사용할 수 있습니다.
- `GET /api/admin/ingestion-runs`: 성공·부분 실패·실패와 실행 중 상태를 확인합니다.

동기화는 이전 커밋과 원격 이력을 확인하고 fast-forward만 허용합니다. 이력 충돌이나 로컬 변경을 강제로 덮어쓰지 않습니다. 수집된 조문은 이전 버전과 연결하며 본문 해시는 BOM·줄바꿈·줄 끝 공백 등의 차이를 정규화합니다. 지원하는 법령 종류와 Markdown 형식은 API 계약을 참고합니다.

Qdrant 사용 시 서버 시작은 기존 컬렉션의 포인트 수를 복원합니다. 서버를 켜거나 화면을 수정하기 위해 재색인을 실행할 필요는 없습니다. 재색인·수집은 외부 임베딩 호출과 비용을 발생시킬 수 있습니다. `/api/admin/reindex`는 동기 응답이고, `/api/v1/admin/reindex`는 백그라운드 예약 후 HTTP 202를 반환합니다.

## 상태와 오류 확인

| 경로 | 용도 |
| --- | --- |
| `/healthz` | 서버 생존 확인 |
| `/api/health`, `/api/v1/health` | DB와 색인 준비 상태 |
| `/api/admin/status` | 전체 데이터 건수·색인 상태·최근 실패·동기화 상태 |
| `/api/admin/search-logs` | 요청 ID·상태·안전한 질문 미리보기 |
| `/api/admin/agent-traces?requestId={requestId}` | 특정 요청의 처리 단계 추적 |
| `/swagger-ui/index.html` | API 형식 확인과 수동 호출 |

Health API는 HTTP 200이어도 본문이 `degraded`일 수 있습니다. 색인의 `stale`은 DB 조문 수와 벡터 수가 다르다는 뜻입니다. 누락과 초과 벡터를 구분해야 하며, `unindexedArticlesCount=0`만으로 정상이라고 판단하지 않습니다. 진단 없이 자동 재색인을 실행하지 않습니다.

`POST /api/admin/provider-smoke-test`는 설정된 LLM·임베딩·재정렬 제공자를 실제 호출합니다. 일부 제공자가 실패해도 HTTP 200일 수 있으므로 각 결과의 `failed` 여부와 안전한 오류 코드를 확인합니다. 답변 API도 본문의 `status`와 `errorMessage`를 함께 확인해야 합니다.

로그 상관관계는 응답 `diagnostics.requestId`, SearchLog, AgentTrace를 기준으로 확인합니다. `ask_complete` 로그는 해시·길이·소요 시간·검색 지표를, `http_request` 로그는 메서드·경로·HTTP 상태·소요 시간을 기록합니다. 질문 전문, 쿼리 문자열과 요청 본문은 운영 로그에 기록하지 않습니다.

## 검증과 결과 파일

- 서버 없는 점검: `scripts/verify-local.ps1`
- 실서버 점검: `scripts/verify-runtime.ps1`
- 제공자 연결 포함: `scripts/verify-runtime.ps1 -IncludeProviderSmoke`
- 고정 질문 시나리오: `scripts/run-scenarios.ps1`
- 준비 상태부터 결과 검증까지: `scripts/verify-final.ps1`

Qdrant·Cohere 필수 조건과 단계별 실행은 [실행 준비 점검](runtime-readiness.md), 질문·인용·로그의 검증 기준은 [검증 안내](evaluation-harness.md), 시나리오 실행법은 [시나리오 안내](scenario-test-guide.md)에 정리돼 있습니다. `-IncludeReindexContract`는 실제 재색인을 유발할 수 있으므로 일반 점검과 구분합니다.

결과는 `target/readiness-summary.json`, `target/runtime-evidence/`, `target/scenario-responses/`, `target/evidence-report.md`에 기록됩니다. 개별 단계 실행 후 `scripts/export-evidence-report.ps1`로 보고서를 모을 수 있습니다. 검증 결과와 수집 데이터는 Git에 올리지 않습니다.

### 실제 DB와 테스트 격리

현재 일부 통합 테스트는 데이터소스 설정을 자체 고정하지 않아 로컬 `.env`의 실제 DB에 연결할 수 있습니다. 따라서 실제 DB 설정이 있는 작업 디렉터리에서 기본 `mvn test`나 `verify-local.ps1`을 그대로 실행하지 않습니다. “서버 비의존”은 “데이터베이스와 자동 격리”를 뜻하지 않습니다. 테스트 기본 설정의 자동 격리는 후속 개선 사항입니다.

전체 Maven 검증은 아래처럼 임시 H2와 mock 제공자를 명시해 실행합니다. Maven이 PATH에 없으면 `mvn.cmd`를 실제 실행 파일 경로로 바꿉니다. 이 명령의 `create-drop`은 명시한 임시 H2에만 적용되므로 데이터소스 지정 옵션을 빼고 실행하지 않습니다. 이미 설정된 프로세스 환경변수도 사전에 점검합니다.

```powershell
& mvn.cmd test `
  '-Dspring.config.import=optional:classpath:/no-local-env.properties' `
  '-Dspring.datasource.url=jdbc:h2:mem:docverification;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1' `
  '-Dspring.datasource.driver-class-name=org.h2.Driver' `
  '-Dspring.datasource.username=sa' `
  '-Dspring.datasource.password=' `
  '-Dspring.jpa.hibernate.ddl-auto=create-drop' `
  '-Dapp.llm.provider=mock' `
  '-Dapp.embedding.provider=mock' `
  '-Dapp.embedding.dimensions=8' `
  '-Dapp.vector.provider=inmemory' `
  '-Dapp.reranker.provider=mock' `
  '-Dapp.embedding.batch-delay-millis=0' `
  '-Dapp.reference-data.enabled=true'
```

실행 로그에서 `jdbc:h2:mem:` 연결과 mock 제공자를 확인합니다. 실제 PostgreSQL이나 외부 제공자가 선택되면 검증을 중단하고 설정을 확인합니다. 실제 데이터 삭제나 재색인으로 테스트 결과를 맞추지 않습니다.

## TradeOps Hub 연결

이 서비스 디렉터리는 RAG API와 점검 화면을 담당합니다. 모노레포의 frontend/backend는 사용자 화면과 인증을 담당합니다. Hub PostgreSQL 기본 포트는 5433, 법령은 5432이며 실제 설정을 우선합니다. DB·볼륨은 분리해 유지합니다. [모노레포 안내](../../../../docs/ko/monorepo.md)를 참고하세요. 루트 `scripts/verify-monorepo.ps1`은 격리된 테스트 설정을 자동 적용합니다. 과거 저장소 분리 인계 문서는 이전 기록입니다.
