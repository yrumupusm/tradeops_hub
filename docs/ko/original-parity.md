# 원본 프로젝트 대응표

[한국어](../ko/original-parity.md) | [English](../en/original-parity.md)

이 문서는 원본 `law_research_assistant`의 지침을 Spring Boot 구현과 연결합니다.
Spring 버전은 포트폴리오 규모의 재구현이며 FastAPI·Next.js 코드를 줄 단위로 옮긴 프로젝트가 아닙니다. 원본 제품의 불변 조건을 유지하면서 Spring Boot, JPA, Thymeleaf를 사용하지 않는 정적 UI, 제공자 인터페이스로 기술 구성을 교체했습니다.

회사 기밀 코드, 내부 데이터, 계정 정보, 비공개 URL은 포함하지 않습니다.

## 참고한 원본 지침

아래 경로는 이 저장소가 아니라 원본 프로젝트의 경로입니다.

- 원본 `CLAUDE.md`
- 원본 `docs/p0-scope.md`
- 원본 `docs/architecture.md`
- 원본 `docs/constraints.md`

## P0 범위 대응

| 원본 요구사항 | Spring 구현 | 검증 근거 |
| --- | --- | --- |
| 현행 법령 검색 | `asOf`가 없으면 `LawQueryService`, `ArticleRepository`, `RetrievalAgent`가 현행 조문으로 제한 | [상세 아키텍처](architecture.md), `ArticleRepositoryAsOfTest` |
| 법률·시행령·시행규칙 색인 | `MarkdownLawParser`가 지원 법령 종류를 매핑하고 범위 밖 종류는 건너뜀 | `MarkdownLawParserTest`, `LocalLawIngestionServiceTest` |
| 자연어 질문 입력 | `POST /api/ask`, `POST /api/v1/ask` | `AskController`, `V1AskController` |
| 질문 구조화 | `QueryAnalyzerAgent`가 행위, 대상, 후보 도메인, 생성 검색어, 질문 유형을 추출 | `QueryAnalyzerAgentTest` |
| 문서·조문 혼합 검색 | `RetrievalAgent`가 키워드/JPA 결과와 `VectorSearchClient` 결과를 합쳐 조문 근거 순위를 계산 | `RetrievalAgentRerankerTest` |
| 후보 법령 | `AskResponse.candidateLaws`, `/api/v1`의 `candidate_laws` | `AskResponseInvariantTest`, `V1ResponseMapper` |
| 인용 조문 | `AskResponse.citedArticles`, `/api/v1`의 `cited_articles` | `AskResponseInvariantTest`, `EvaluationHarnessTest` |
| 근거 설명 | `AnswerWriterAgent`는 원문을 보강한 검색 근거만으로 설명 생성 | `AnswerWriterAgentTest` |
| 추가 확인 질문 | `QueryAnalyzerAgent`와 `AnswerWriterAgent`가 불확실성에 따른 추가 질문 유지 | `QueryAnalyzerAgentTest`, `AnswerWriterAgentTest` |
| 기준 데이터 | `EffectiveBasisDto`에 `snapshotVersion`, `indexedAt`, `sourcePath`, 선택적 `asOf` 포함 | [API 계약](api-contract.md) |
| 최소 관리자 API/UI | `/api/admin/*`, `/api/v1/admin/*`, `static/admin.html`, `static/admin.js` | `AdminControllerIntegrationTest`, `StaticResourceTextTest` |

## 아키텍처 대응

| 원본 계층 | Spring 구현 |
| --- | --- |
| 수집 계층 | `SourceSyncService`, `LocalLawIngestionService` |
| 파싱·정규화 계층 | `MarkdownLawParser`, `ParsedLawFile`, `ParsedLawArticle` |
| 색인 계층 | `VectorIndexService`, `EmbeddingClient`, `VectorSearchClient` |
| 검색 계층 | `RetrievalAgent`, `ArticleRepository`, `RerankerClient` |
| LLM 실행 조율 계층 | `AskOrchestratorService`, `QueryAnalyzerAgent`, `AnswerWriterAgent`, `CriticAgent` |
| API 계층 | `AskController`, `V1AskController`, `LawController`, `V1LawController`, `AdminController`, `V1AdminController` |
| 웹 UI 계층 | `src/main/resources/static/index.html`, `app.js`, `styles.css` |
| 관리자 계층 | `src/main/resources/static/admin.html`, `admin.js`, `/api/admin/*` |

실행 파이프라인:

```text
AskController
-> AskOrchestratorService
-> QueryAnalyzerAgent
-> RetrievalAgent
-> EvidenceValidatorAgent
-> AnswerWriterAgent
-> CriticAgent
-> SearchLogAgent
```

## 핵심 불변 조건

- `status=OK`에는 인용이 필요하며, 정상 답변에 인용 조문이 최소 하나 있어야 합니다.
- 검색 또는 원문 보강 근거가 있는 `LOW_CONFIDENCE`도 인용 조문을 유지합니다.
- LLM은 모델 지식만으로 답하지 않습니다. 답변 생성에는 검색된 맥락을 제공합니다.
- 최종 법률 판단은 허용하지 않습니다. 조사 보조에 맞는 표현을 사용합니다.
- 모든 답변의 기준 데이터에는 `snapshotVersion`, `indexedAt`, `sourcePath`를 포함하며, 기준일 요청 시 `asOf`도 포함합니다.
- 제공자 세부 구현은 `ChatModelClient`, `EmbeddingClient`, `VectorSearchClient`, `RerankerClient` 뒤에 둡니다.
- 제공자 URL, 모델명, 차원, API 키는 설정 또는 환경변수에서 가져옵니다.
- 답변, 검색 로그, 에이전트 추적은 같은 `requestId`를 공유합니다.

## 원본 API 호환성

Spring 구현은 두 가지 API 형식을 제공합니다.

- `/api/...`: Spring 정적 UI에서 사용하는 camelCase DTO
- `/api/v1/...`: 원본 호환 snake_case DTO

유지하는 주요 원본 형식 응답 필드:

- `candidate_laws`
- `cited_articles`
- `follow_up_questions`
- `effective_basis`
- `retrieval_stats`
- `request_id`
- `error_message`

`POST /api/v1/ask`는 엄격한 요청 검증과 기준일의 `as_of` 별칭을 지원합니다.

## 검색·제공자 대응

원본 아키텍처가 요구하는 혼합 검색:

- 제목의 정확·유사 일치
- 전문 또는 키워드 검색
- 밀집 벡터 검색
- 재정렬

Spring 구현:

- `RetrievalAgent`의 JPA 키워드 검색과 어휘 기반 점수화
- 공백 차이와 별칭을 허용하는 법령명·조문 번호 압축 문자열 비교
- 밀집 벡터를 생성하는 `EmbeddingClient`
- 메모리·Qdrant 구현을 갖는 `VectorSearchClient`
- mock·Cohere 구현을 갖는 `RerankerClient`

외부 제공자 구현:

- OpenRouter 채팅: `OpenRouterChatModelClient`
- OpenRouter 임베딩: `OpenRouterEmbeddingClient`
- Qdrant 벡터 저장소: `QdrantVectorSearchClient`
- Cohere 재정렬: `CohereRerankerClient`

mock 제공자는 로컬 개발용 대체 구현이며, 포트폴리오 검증에서 목표로 하는 최종 실서버 실행 근거는 아닙니다.

## 감사·운영 관측 대응

| 원본 요구 | Spring 구현 |
| --- | --- |
| 검색 결과 추적 | `SearchLog`에 요청 ID, 질문 해시, 상태, 인용 수, 소요 시간, 길이가 제한된 미리보기 저장 |
| 에이전트·처리 추적 | `AgentTrace`에 단계 순서, 요약, 상태, 소요 시간 저장 |
| 요청 연결 | `AskResponse.diagnostics.requestId`, `SearchLog.requestId`, `AgentTrace.requestId` 일치 |
| HTTP 감사 | `HttpRequestAuditFilter`가 본문·쿼리 내용 없이 메서드, 경로, 상태, 소요 시간 기록 |
| 관리자 운영 상태 | `AdminService`, `/api/admin/status`, `/api/admin/ingestion-runs`, `/api/admin/provider-smoke-test` |

## 하네스·검증 대응

원본 프로젝트는 인용 검사, 문서 확인, 제공자 비용 관리, 조용한 실패 탐지를 위한 Claude 하네스 지침을 사용했습니다. Spring 구현은 같은 취지를 코드 계약과 스크립트로 반영합니다.

- `AskResponseInvariantTest`: 인용·상태 불변 조건
- `EvaluationHarnessTest`, `AnswerQualityHarnessTest`: 고정 질문 답변 품질
- `ScenarioScriptContractTest`: 런타임 시나리오 스크립트 계약
- `SilentFailureContractTest`: 지침에서 제공자·검색·파싱·검증 실패를 숨기는 패턴 금지
- `ProviderInterfaceTest`: 서비스 경계가 제공자 인터페이스에 의존하는지 확인
- `scripts/verify-local.ps1`: 서버 비의존 로컬 회귀 검증
- `scripts/verify-readiness.ps1`: 키 값을 출력하지 않는 설정·비밀값 존재 여부 점검
- `scripts/verify-runtime.ps1`: 실행 중 서버의 health, 관리자 상태, 질문, 선택적 제공자 연결 점검
- `scripts/run-scenarios.ps1`: 시나리오 답변, 인용, 한국어 정책, 금지 문구, 검색 로그, 추적 검증
- `scripts/export-evidence-report.ps1`: 최종 검증 보고서
- `scripts/verify-completion-evidence.ps1`: 최종 검증 근거 통과 조건
- `scripts/verify-final.ps1`: 사용자가 서버를 수동 시작·재시작한 뒤 실행하는 최종 검증 묶음

## 의도적인 차이

- 원본은 FastAPI, Next.js, Qdrant, Docker Compose를 사용했습니다. 이 구현은 Spring Boot, JPA, 정적 프런트엔드 파일과 교체 가능한 Qdrant 지원을 사용합니다.
- 원본 P0는 기준일 검색과 개정 비교 UI를 제외했습니다. Spring 구현은 포트폴리오 설명을 강화하기 위해 통제된 `asOf`, 이력, 비교 지원을 추가했지만, 핵심 질문 흐름은 이 확장 없이도 동작합니다.
- 원본 P0는 재정렬을 인터페이스와 mock으로 제한했습니다. 이 구현은 Cohere 재정렬도 지원하고 로컬 개발용 mock을 유지합니다.

## 남은 실서버 검증

로컬 테스트는 서버를 시작하지 않고 구현 계약을 검증할 수 있습니다. 전체 포트폴리오 검증 근거를 만들려면 제공자를 설정한 서버를 수동 실행한 뒤 다음 명령을 사용해야 합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-final.ps1
```

이 명령은 Spring Boot 서버를 시작하거나 중지·재시작하지 않습니다. 사용자가 이미 실행한 서버만 검증합니다.
