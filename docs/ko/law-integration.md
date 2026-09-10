# 법령 검색 통합

[한국어](law-integration.md) · [English](../en/law-integration.md)

## 책임 경계와 API

Hub `/law-search`는 기존 한국어 공통 화면과 구성요소를 사용한다. 법령 RAG 저장소가 분석·검색·답변 생성·PostgreSQL·Qdrant를 담당한다. iframe, 화면 복사, 관리 프록시, 공유 DB나 두 검색 결과를 합친 수출 가능 판정은 만들지 않는다. 기존 RAG 화면은 개발 도구로 유지한다.

모든 경로는 Hub 세션·활성 계정·비밀번호 변경 의무를 확인하며 POST에는 CSRF가 필요하다. 일반 로그인 사용자가 이용한다. 다음 고정 경로만 중계한다.

| Hub 접두사 /api/v1/law-search | RAG |
| --- | --- |
| POST /ask | POST /api/ask |
| GET /articles/{id}/history | GET /api/articles/{id}/history |
| GET /articles/{id}/diff?compareWith={id} | GET /api/articles/{id}/diff?compareWith={id} |

입력은 question(공백 불가, 최대 4000자), asOf(null 또는 유효한 YYYY-MM-DD), researchAreas(생략/null/빈 배열, STRATEGIC_GOODS, DEFENSE_MATERIALS 또는 중복 없는 두 분야)다. 미지원 필드와 양수가 아닌 조문 ID는 거부한다. 쿠키·CSRF·임의 브라우저 헤더를 전달하지 않는다. 관리·로그·수집·색인 API는 없다.

응답은 status, reasoning, followUpQuestions, citedArticles, disclaimer, effectiveBasis.asOf, RAG requestId와 Hub correlationId를 유지한다. nullable 조문 필드·본문·previousArticleId·historicalEntries를 보존한다. 질문 해석·점수·후보 법령·원본 errorMessage·서버 경로는 제외한다. 서버 감사에는 사용자·Hub 상관 ID·RAG 요청 ID·전체 effectiveBasis를 남기되 질문·답변·조문 전문은 넣지 않는다. 이를 통해 RAG SearchLog·AgentTrace와 연결하며 해당 로그는 개인 이력으로 공개하지 않는다. 질문은 URL이나 우려거래자 이력에 저장하지 않는다.

OK에는 인용이 필요하고 LOW_CONFIDENCE도 retrieved/hydrated 근거가 있으면 인용이 필요하다. 필수 필드 누락·형식 오류는 LAW_INVALID_RESPONSE(502)다. LOW_CONFIDENCE·INSUFFICIENT_INFO는 서로 다른 200 상태로 유지한다. FAILED는 안전한 안내만 제공하는 200 상태이며 신뢰할 수 없는 부분 답변은 숨긴다. 연결·HTTP·리다이렉트 실패는 LAW_UNAVAILABLE(503), 대기 초과는 LAW_TIMEOUT(504), JSON 오류는 LAW_INVALID_RESPONSE(502), RAG 400은 LAW_REQUEST_REJECTED(502), 조문 없음은 LAW_ARTICLE_NOT_FOUND(404)다. provider 예외 원문을 공개하지 않는다. RAG 장애가 BIS나 Hub 상태 확인을 막지 않는다.

## 한국어 화면

우려거래자 그룹 아래에 별도 법령 조사 메뉴 영역을 둔다. 질문, 선택 분야 전략물자 / 방산물자·국방과학기술, 선택 기준일을 제공한다. 분야는 검색 우선순위·맥락이며 법적 분류나 특정 법령만 허용하는 필터가 아니다. 범위는 무역안보·방산·기술보호 핵심 8개 법령과 수집된 하위 법령이며 전체 대한민국 법령이나 실시간 공식 동기화가 아니다.

답변은 텍스트로 표시한다. 인용 카드는 법령명·번호·제목을 먼저 보여주고 키보드로 접기·펼치기가 가능한 본문·날짜·이력·비교를 제공한다. previousArticleId가 없으면 이전 비교를 비활성화한다. 포함된 historicalEntries를 보존하며 조회한 이력과도 비교할 수 있다. 비교는 두 원문과 동일 여부만 표시하고 개정 문구를 생성하지 않는다. ** 표시만 화면에서 정리하며 HTML을 주입하지 않는다.

로딩 중 중복 전송을 막고 단계·진행률을 추측하지 않는다. 화면 이동 시 브라우저 요청을 취소하고 늦은 응답을 버리되 provider 내부 작업 취소까지 보장하지 않는다. 입력을 유지해 수동 재시도하게 하며 POST를 자동 재전송하지 않는다.

## 배포와 보존

서버 전용 LAW_RAG_BASE_URL에 기존 내부 origin을 설정한다(로컬 예: http://127.0.0.1:8080). 빈 값이면 법령 연결만 사용할 수 없다. 계정·쿼리·fragment·추가 경로 없는 http/https origin만 받는다. RAG는 Hub 세션을 확인하지 않으므로 RAG·DB·Qdrant 접근망을 제한한다. 컨테이너는 서비스 DNS를 사용한다. 확인한 DB 호스트 포트는 Hub 5433, RAG 5432다.

LAW_RAG_TIMEOUT_SECONDS는 기본 180초, 허용 1–180초이며 연결 제한은 5초다. 전체 한도는 본문 읽기를 포함하며 응답은 최대 4 MiB다. Next.js는 195초, 브라우저는 205초다. provider별 기본 30초가 여러 호출·재시도로 누적되면 이 한도를 넘을 수 있다. 측정 후 provider 설정을 별도 조정하며 유료 질문을 조용히 재시도하지 않는다.

RAG의 기존 설정과 자체 실행 스크립트로 먼저 시작하고 Hub에 LAW_RAG_BASE_URL을 전달한다. 연결 때문에 DB 재생성·볼륨/컬렉션 삭제·재수집·재색인을 하지 않는다. Hub에는 RAG DB 계정이 없고 법령 마이그레이션·벡터 쓰기를 하지 않는다. Hub만 재시작해도 RAG 데이터 작업은 발생하지 않는다. 저장소와 볼륨은 분리한다.

## 검증

확인한 RAG 리비전은 9c2d67fca773c50eca885a98b0a0b144f4dd742c이며 미추적 output은 건드리지 않았다. Hub의 feat/law-search는 7445fbf에서 분기했다. 최종 검증 리비전과 결과는 실행 확인 후 기록한다.

최초 읽기 전용 상태는 DB 정상, 조문 4112, 색인 4431, 미색인 0, index stale, 전체 degraded다. 완전한 준비 상태가 아니며 자동 재색인 지시로 삼지 않는다. 검사 후 건수를 비교한다.

LawIntegrationTest는 입력·상태·인용·내부 필드 제외·추적 연결·이력/비교·세션/CSRF/강제 변경/비활성화를 검증한다. LawClientTest는 통제된 HTTP 서버로 헤더·리다이렉트·JSON 오류·대기 한도·재시도 금지를 확인한다. 브라우저와 소수 실제 provider 검사로 보완하며 빌드만으로 완료를 판단하지 않는다. 기존 BIS 회귀 검증도 로컬 검사에 유지한다.
