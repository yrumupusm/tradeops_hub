# TradeOps Hub 법령 검색 연동 인계

작성일: 2026-09-10. 이 문서는 통합 구현을 위한 인계이며, 통합이 완료됐다는 보고가 아니다.

## 합의된 방향과 작업 범위

- Git 저장소는 분리한다. TradeOps Hub에서 하나의 서비스로 제공하고 RAG 서버는 API로 연결한다.
- Hub의 ‘우려거래자 조회/수집’ 메뉴 그룹 바로 아래에 독립적인 ‘법령 검색’ 메뉴를 둔다. 하위 수집 기능으로 넣지 않는다.
- UI/UX는 TradeOps Hub를 따른다. 기존 공통 메뉴, 색상, 글꼴, 간격, 입력창, 버튼, 오류 표시를 재사용한다.
- RAG 페이지를 iframe으로 삽입하거나 독립 화면의 CSS를 그대로 복사하지 않는다. 응답 데이터를 Hub 화면으로 표현한다.
- 첫 통합은 로그인 사용자의 질문·검색 결과·인용 조문·조문 이력/비교에 집중한다. RAG 관리 화면 통합은 별도 후속 작업이다.
- 연결만을 위해 법령을 재수집하거나 재임베딩하지 않는다. 기존 PostgreSQL과 Qdrant 데이터를 사용한다.

## 저장소와 담당 경계

| 저장소 | 역할 | 권장 작업 브랜치 |
| --- | --- | --- |
| `C:\dev\tradeops_hub` | 통합 주도, 메뉴·화면·세션 인증·권한·API 중계·운영 연결 | `feat/law-search` |
| `C:\dev\law-research-assistant-spring` | 질문 분석·검색·근거 검증·답변·법령 수집·색인 | RAG 변경이 필요할 때 `feat/tradeops-integration` |

구현 전에 각 저장소의 `AGENTS.md`, `PROJECT_GUIDE.md`와 작업 중 변경사항을 확인한다. 다른 AI의 작업을 덮어쓰지 않는다. 기능 브랜치에서 검증하고 각 저장소별로 PR/커밋을 관리한다.

이 문서 작성 시 확인한 코드 기준:

- RAG: `47d0d94afdf69c3beff9210664c42eb45ad78f17`
- Hub: `7445fbf2951a4a6736d1a998fa52ffb6a18c5910`

이는 조사 기준점이다. 실제 착수 시 브랜치와 미커밋 변경을 다시 확인하고, 완료 후 함께 검증한 두 커밋과 실행 설정을 Hub의 배포/검증 문서에 기록한다.

## 권장 호출 구조

```text
브라우저: TradeOps Hub 법령 검색 화면
  → Hub 동일 출처 /api/v1/law-search/...
  → Hub Spring Boot: 세션·CSRF·권한 확인, RAG 클라이언트 호출
  → RAG /api/ask 또는 허용된 조문 조회 API
  → 기존 RAG PostgreSQL · Qdrant · 설정된 모델 provider
```

Hub의 `/api/v1/law-search/...`와 화면 경로 `/law-search`는 제안이며 아직 존재하지 않는다. Hub의 라우팅 규칙에 맞춰 확정한다.

현재 확인한 Hub 연결 지점:

- `frontend/src/components/console.tsx`: 메뉴 구성 및 화면 분기
- `frontend/src/app/[...path]/page.tsx`: 허용 화면 경로 확인
- `frontend/src/lib/api.ts`: `/api/v1` 요청, `X-CSRF-TOKEN`, 공통 오류 처리
- `frontend/next.config.ts`: `/api/v1/:path*`를 `API_PROXY_TARGET`으로 중계
- `backend/src/main/java/io/tradeops/auth/SecurityConfig.java`: 로그인, CSRF, 임시 비밀번호 변경 의무

Hub 서버에 RAG 클라이언트와 전용 엔드포인트를 추가한다. 기존 인증 경로를 거치도록 하며 Next.js에서 RAG로 바로 rewrite하여 Hub 인증을 우회하지 않는다. RAG의 실제 base URL은 Hub 서버 환경변수(예: 새로 정의할 `LAW_RAG_BASE_URL`)로 관리하고 브라우저에 노출하는 설정으로 만들지 않는다.

현재 RAG는 Hub 세션 인증/사용자 권한을 검증하지 않는다. 관리자 API도 Hub 권한으로 보호되는 상태가 아니다. 통합 배포에서는 RAG 및 데이터 저장소를 내부 네트워크 또는 필요한 로컬 인터페이스로 제한하고, Hub에 허용한 검색·조문 조회 경로만 명시적으로 중계한다. 임의 URL/경로를 받는 범용 프록시를 만들지 않는다. Hub 쿠키·CSRF 값·사용자 입력의 임의 헤더를 RAG에 그대로 전달할 필요는 없다.

## 사용할 RAG API

Hub에는 `/api/v1`이 있어도, RAG 호출은 아래 **`/api` camelCase 계약**으로 통일하는 것을 권장한다. RAG `/api/v1`은 별도의 snake_case·소문자 enum 호환 응답이므로 두 형식을 혼용하지 않는다.

| RAG API | 사용처 |
| --- | --- |
| `POST /api/ask` | 질문과 답변, 인용 조문 반환 |
| `GET /api/articles/{id}/history` | 인용 조문의 이력 보기 |
| `GET /api/articles/{id}/diff?compareWith={previousArticleId}` | 이전 조문 비교 |
| `GET /api/articles/{id}` | 필요할 때 개별 조문 조회 |
| `GET /api/laws?q={keyword}&page=1&size=20` | 필요할 때 법령 목록 조회; 페이지 처리가 필요함 |
| `GET /healthz` | 서버 생존 확인 |
| `GET /api/health` | DB·색인 상태 확인; HTTP 코드와 응답 본문을 함께 확인 |

관리용 `/api/admin/*` 전체를 사용자 화면으로 중계하지 않는다. API 명세는 [api-contract.md](api-contract.md), 실제 필드는 `src/main/java/com/example/lawassistant/dto/`를 확인한다. 기존 API 문서의 JSON 예시는 일부 필드가 생략돼 있다.

### 질문 요청

```json
{
  "question": "탱크를 수출하고 싶은데 관련된 법령에는 어떤 게 있어?",
  "asOf": null,
  "researchAreas": ["DEFENSE_MATERIALS"]
}
```

- `question`: 필수, 공백만 입력 불가, 최대 4000자.
- `asOf`: 선택, `YYYY-MM-DD` 또는 `null`. `null`은 현재 유효 조문 기준.
- `researchAreas`: 선택, 미선택은 `[]`. `STRATEGIC_GOODS`와 `DEFENSE_MATERIALS`를 복수 선택할 수 있다.
- 선택 분야는 검색 우선순위·답변 맥락이다. 법적 해당 여부 판정이나 특정 법령만 허용하는 필터로 표현하지 않는다.
- 요청 본문에 `userId`, `requestId` 등 새 필드를 덧붙이지 않는다. 미지원 필드는 엄격 검증으로 HTTP 400이다.
- 잘못된 입력은 HTTP 400과 `{"error":"invalid request"}`. Hub 공통 오류 형식으로 매핑한다.

### 응답과 화면 매핑

| 응답 필드 | Hub 표시/처리 |
| --- | --- |
| `reasoning` | 검색 결과의 답변 본문. Hub에서 법적 설명을 추가 생성하지 않음 |
| `followUpQuestions` | 추가 확인 질문 |
| `citedArticles` | 인용 조문 목록 |
| `effectiveBasis.asOf` | 사용자가 선택한 기준일 또는 현재 유효 조문 |
| `disclaimer` | 서버가 제공한 확인 안내; 분야에 따라 달라질 수 있음 |
| `status`, `errorMessage` | 결과 상태/오류 분기; 내부 실패 코드를 그대로 문구로 출력하지 않음 |
| `diagnostics.requestId` | 운영 추적용 RAG 요청 ID |
| `effectiveBasis` 전체 | 근거 추적 데이터로 유지; 원문 경로·스냅샷은 일반 화면에서 숨김 |
| `interpretation`, `candidateLaws`, `confidence`, 나머지 `diagnostics` | 일반 결과 화면에 표시하지 않는 내부 점검 데이터 |

`effectiveBasis`는 `snapshotVersion`, `indexedAt`, `sourcePath`, `sourceVersion`, `asOf`를 가진다. 일부 출처 필드는 `null`일 수 있다. 원본 응답/서버 측 추적에는 보존하되, 브라우저 응답을 축소한다면 출처와 요청 상관관계를 서버에서 잃지 않도록 설계한다.

`citedArticles` 항목은 다음 필드를 가진다:

```text
articleId, lawId, lawTitle, articleNumber, articleTitle,
content, reason, contentHash, effectiveFrom, effectiveTo,
amendmentKind, previousArticleId, historicalEntries
```

인용 카드에는 처음에 법령명·조문 번호·제목과 ‘전체 보기’만 표시한다. 펼치면 본문·유효기간·이력/비교 기능을 표시하고 다시 접을 수 있어야 한다. `reason`의 검색 점수 등은 표시하지 않는다. `previousArticleId`가 없으면 이전 조문 비교를 비활성화한다. `historicalEntries`에 포함된 이전 회차도 개정 비교의 근거이므로 버리지 않는다.

이력 응답은 `{lawId, lawTitle, articleNumber, entries}`이며, 비교 응답은 `contentA`, `contentB`, `contentHashEqual` 등을 포함한다. 비교 결과의 본문은 텍스트로 안전하게 렌더링한다. `content`의 `**` 표기는 기존 화면처럼 표시 단계에서 정리할 수 있지만 법령 문구나 조문 번호 자체를 임의 변경하지 않는다. HTML 문자열로 직접 주입하지 않는다.

### 상태와 실패 처리

| `status` | 처리 |
| --- | --- |
| `OK` | 답변 및 인용 표시. 적어도 한 개의 인용이 있어야 함 |
| `LOW_CONFIDENCE` | 근거가 약하다는 안내와 제공된 답변·인용 표시 |
| `INSUFFICIENT_INFO` | 정보 부족과 추가 질문 안내 |
| `FAILED` | 안전한 실패 안내와 재시도 수단 |

HTTP 200이어도 본문이 `FAILED`일 수 있다. 반대로 모든 비정상 상태를 통신 오류로 바꾸지 않는다. `LOW_CONFIDENCE`에서 `diagnostics.retrievalStats.retrieved > 0` 또는 `hydrated > 0`이면 인용이 최소 한 개 필요하다. 계약을 위반한 응답은 정상 답변으로 표시하지 않는다.

현재 실패 코드는 `no_snapshot`, `query_analysis_failed`, `retrieval_failed`, `evidence_validation_failed`, `answer_generation_failed`, `missing_citation`, `response_quality_failed`다. Hub는 RAG 연결 실패·시간 초과·잘못된 응답도 별도로 처리하고 provider 예외 원문을 사용자에게 노출하지 않는다.

`/api/ask`는 현재 동기식 단일 JSON 응답이다. SSE나 진행 단계 조회 API는 없다. 로딩 표시를 제공하되 실제 단계/진행률을 추측해 표시하지 않는다. 중복 클릭을 막고 늦게 도착한 이전 요청이 최신 결과를 덮어쓰지 않게 한다.

Provider별 timeout은 질문 전체 제한 시간이 아니다. `HTTP_TIMEOUT_SECONDS`, `LLM_TIMEOUT_SECONDS`, 여러 검색/모델 호출 및 재시도 시간을 확인해 RAG 클라이언트·Hub·Next 프록시·브라우저의 대기 시간을 함께 설계하고 검증한다. 짧은 기본값으로 검색을 끊거나 무제한 기다리게 하지 않는다. 질문 POST는 비용과 로그가 발생하므로 자동 재전송하지 않는다. 브라우저 요청 취소만으로 RAG 내부 처리가 중단된다고 가정하지 않는다.

## 화면 세부 기준

- 제목: ‘법령 검색’. 검색 버튼: ‘검색’.
- 질문 입력, 검색 가능 범위 안내, 분야 선택(선택 사항), 기준일을 제공한다.
- 분야 라벨: ‘전략물자’, ‘방산물자·국방과학기술’. 미선택/하나/둘 모두 지원한다.
- 검색 범위는 무역안보·방산·기술보호 관련 8개 핵심 법령과 수집된 하위 법령이다. 대한민국 전체 법령 검색이나 실시간 공식 법령 동기화로 소개하지 않는다.
- 일반 화면에서 검색 점수, 질문 해석, 검색 과정, 후보 법령 카드, 색인 상태, 원문 서버 경로를 노출하지 않는다.
- 긴 답변·조문은 Hub 레이아웃 안에서 읽을 수 있게 배치하고 접기/스크롤, 모바일, 키보드 조작을 확인한다.
- 법령 검색 질문을 우려거래자 검색에 자동 전달하거나 두 결과를 합쳐 수출 가능 여부를 판정하는 기능은 이번 범위에 없다.
- 현재 RAG 정적 화면은 독립 개발·점검용으로 유지한다. Hub 사용자용 UI의 기준은 Hub 저장소에 기록한다.

## 인증과 요청 추적

Hub의 세션·CSRF·계정 비활성화·비밀번호 변경 의무 검사를 그대로 적용한다. 일반 사용자가 이용할 질문/조문 조회와 운영자의 관리 기능을 구분한다. 화면 숨김만으로 권한을 처리하지 않는다.

RAG는 `AskOrchestratorService`에서 UUID 요청 ID를 자체 생성한다. Hub의 `correlationId`와 자동으로 같아지지 않는다. Hub 서버에서 사용자 식별자, Hub correlation ID, 응답의 `diagnostics.requestId` 간 매핑을 남긴다. RAG의 응답·SearchLog·AgentTrace는 같은 RAG 요청 ID를 유지해야 한다.

Hub에 검색 이력을 추가할 경우 사용자별 조회 권한을 별도로 설계한다. 기존 RAG 관리자 로그는 사용자별 이력이 아니므로 일반 사용자에게 그대로 공개하지 않는다. 질문 전문을 운영 로그/URL 쿼리/분석 이벤트에 기록하지 않는다. 기존 RAG 화면의 URL 공유 동작을 무심코 복사하지 않는다. hash·길이·마스킹된 짧은 preview 규칙을 참고한다.

## 실행과 데이터 보존

개발 기본 주소: Hub 웹 `http://localhost:3000`, Hub API `http://127.0.0.1:8081`, RAG `http://localhost:8080`, Qdrant `http://localhost:6333`. 실제 환경 설정을 우선한다. 컨테이너 안의 `localhost`는 해당 컨테이너이므로 배포 시 서비스 DNS/네트워크를 따로 설정한다.

RAG 저장소에서 기존 컨테이너가 있다면:

```powershell
docker compose ps -a
docker compose start postgres qdrant
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\server.ps1 start -MavenPath C:\dev\apache-maven-3.9.9\bin\mvn.cmd
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\server.ps1 status
```

신규 환경은 README에 따라 `.env`와 provider/DB를 준비한다. 빈 기본 설정으로 기동하면 H2·mock·reference data를 쓸 수 있으므로 기존 실데이터가 연결된 것으로 오인하지 않는다. `.env`의 비밀값을 인계 문서나 Git에 복사하지 않는다.

두 Compose 모두 기본 PostgreSQL 호스트 포트가 `5432`다. 실제 실행 중 포트를 먼저 확인하고, 필요 시 한쪽 `POSTGRES_PORT`와 해당 JDBC URL을 함께 조정한다. DB·볼륨은 분리해서 유지한다. 기본값 `ddl-auto=create-drop`으로 기존 DB를 시작하지 않도록 배포 설정을 확인한다. `docker compose down -v`나 Qdrant 컬렉션 삭제로 충돌을 해결하지 않는다.

한 번의 실행 명령으로 두 서비스를 운영하는 스크립트/Compose는 후속 구현 대상이며 아직 완성된 통합 실행 구성이 없다. 기존 데이터 경로·볼륨·임베딩 모델·차원·컬렉션을 보존하고, 허브만 재시작할 때 RAG 데이터 작업을 유발하지 않게 한다.

## 알려진 상태와 해석 주의점

- 앞선 관리 화면에서 DB 조문 4,112건 / 벡터 4,431건, `stale`이 관찰됐다. 이는 과거 관찰값이며 현재 상태를 다시 조회해야 한다. 이번 문서 작성에서 재색인·정리는 실행하지 않았다.
- `stale`은 건수 불일치 신호다. 원인이 남는 벡터인지 누락인지 확인해야 하며 자동으로 재색인하지 않는다. `unindexedArticlesCount=0`도 초과 벡터가 없다는 뜻은 아니다.
- Health API는 HTTP 200 안에 `status=degraded`를 반환할 수 있다. 생존 확인과 검색 준비 상태를 구분한다. 일시적인 RAG 장애가 우려거래자 검색까지 막지 않게 한다.
- `docs/law-scope.md`의 21개 seed 조문 설명은 초기 검증용 데이터 설명이다. 현재 실행 DB의 실제 적재 건수와 혼동하지 않는다.
- 기준일 유효성, 인용 정확도, 부칙 분리 등 RAG의 품질 문제를 Hub 화면에서 임의로 보정하지 않는다. 재현 요청과 RAG request ID를 남겨 RAG 담당에게 전달한다.

## 완료 검증 및 인계 결과

1. 로그인·로그아웃·권한·CSRF·비밀번호 변경 의무가 법령 검색에도 적용되는지 확인한다.
2. 4000자 초과, 빈 질문, 잘못된 날짜/분야를 거부하고 미선택/단일/복수 분야를 전송하는지 확인한다.
3. ‘탱크 수출 관련 법령’ + 방산 선택, 전략물자 질문 + 전략 선택, 일반 수출 질문 + 미선택을 고정 입력으로 검증한다. 외부 모델의 문장 일치보다 API 계약·근거 보존을 우선 검증한다.
4. 정상·근거 부족·정보 부족·실패·HTTP 오류·시간 초과·RAG 중단을 통제된 응답으로 확인한다. 실패를 빈 검색 결과로 바꾸지 않는다.
5. 답변의 인용 본문, 전체 보기/접기, 이력, 이전 조문 비교, nullable 필드와 `historicalEntries` 표시를 확인한다.
6. 긴 질문/긴 답변, 작은 화면, 키보드, 중복 클릭, 빠른 재검색, 화면 이동 후 지연 응답을 확인한다.
7. Hub correlation ID와 RAG request ID 연결, 다른 사용자 로그 비공개, 질문 전문 비기록을 확인한다.
8. 기존 우려거래자 조회/수집·인증 회귀 검증과 Hub의 저장소 지정 검증 절차를 실행한다. RAG를 수정했다면 해당 테스트와 `mvn test`도 실행한다.
9. 실제 provider 호출은 과금/지연 가능성을 고려해 필요한 소수의 종단 확인에 사용한다. 테스트 때문에 운영 데이터를 재수집·재색인하지 않는다.
10. Hub 문서에 확정 경로·환경변수·실행법·장애 처리·두 저장소 검증 커밋을 기록한다. API 계약 변경이 필요하면 RAG 담당과 명시적으로 맞춘다.

## Hub 담당 AI에게 전달할 요청

> 이 문서를 기준으로 별도 브랜치에서 법령 검색을 통합해줘. Git 저장소는 분리하고, ‘우려거래자 조회/수집’ 그룹 아래에 독립적인 ‘법령 검색’ 메뉴를 만들어줘. UI/UX는 TradeOps Hub의 기존 구성요소와 스타일을 따라야 해. 허브 로그인과 권한을 거쳐 RAG API를 호출하고, 기존 법령 DB·임베딩을 그대로 사용해줘. RAG 관리 기능 통합은 후속으로 두고 사용자 검색·답변·인용·이력/비교부터 구현해줘. API 변경이 필요하면 근거와 필요한 계약을 정리해줘. 기존 작업을 보존하면서 기능과 오류·권한 처리를 검증하고, 실행 방법과 함께 검증한 두 저장소 버전을 남겨줘.
