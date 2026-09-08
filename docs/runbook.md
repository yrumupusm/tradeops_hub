# 실행 및 운영 안내

## 로컬 준비

Java 17, Maven, Node.js와 npm, Docker Compose를 설치합니다. 루트의 `.env.example`을 `.env`로 복사하고 DB 비밀번호와 JWT 키를 설정합니다. `JWT_SECRET`에는 최소 32바이트 난수의 Base64 값을 사용합니다. 예제 키는 로컬 설정 형식을 보여 주는 공개 값입니다.

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
.\scripts\start-api-local.ps1 -MavenPath C:\path\to\mvn.cmd
```

시작 스크립트는 루트 `.env`를 프로세스 환경변수로 읽습니다. Maven을 직접 실행할 때는 환경변수를 별도로 설정해야 합니다. 웹은 다른 터미널에서 실행합니다.

```powershell
cd frontend
npm ci
npm run dev
```

## 설정

| 변수 | 기본값 / 역할 |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER` | `tradeops_hub`, `tradeops` |
| `POSTGRES_PASSWORD` | 로컬에서 설정; Git에 저장하지 않음 |
| `DB_HOST`, `POSTGRES_PORT` | `localhost`, `5432` |
| `API_PORT` | `8081` |
| `JWT_SECRET`, `JWT_ISSUER`, `JWT_EXPIRATION_MINUTES` | 서명 키, 발급자, 토큰 유효 분 |
| `SEED_DEMO_USERS` | 예제에서 true; API 기본값 false |
| `NEXT_PUBLIC_API_BASE` | 웹 기본 `http://localhost:8081/api/v1` |
| `TRADEOPS_WEB_ALLOWED_ORIGIN` | API 기본 `http://localhost:3000` |
| `WEB_PORT` | 예약 값; 현재 웹 실행 명령에 자동 적용되지 않음 |
| `FIXTURE_SEED` | 재현용 시드 메타데이터; 갱신 API는 이미 생성한 파일을 읽음 |

웹 환경변수는 웹을 실행하는 셸에 설정합니다. 루트 `.env`가 Next.js에 자동 로드되는 구조는 아닙니다. 예를 들어 포트 3001을 사용하려면 API 환경에 `TRADEOPS_WEB_ALLOWED_ORIGIN=http://localhost:3001`을 설정하고 웹을 `npm run dev -- --port 3001`로 실행합니다.

## 로컬 계정

`SEED_DEMO_USERS=true`일 때 다음 계정이 없으면 생성됩니다. 이미 존재하는 계정의 비밀번호는 시더가 변경하지 않습니다.

| 계정 | 역할 |
| --- | --- |
| `admin@tradeops.test` | ADMIN |
| `operator@tradeops.test` | OPERATOR |
| `viewer@tradeops.test` | VIEWER |

공통 로컬 비밀번호는 `portfolio-demo`입니다. 현재 웹 로그인 버튼은 이 운영자 계정을 사용합니다. 외부 공개 서비스에 사용할 일반 로그인·계정 운영은 아직 구현되어 있지 않습니다.

## 기본 확인 순서

1. `GET http://localhost:8081/api/v1/health`의 응답과 `X-Correlation-Id`를 확인합니다. 이 엔드포인트는 API 응답 상태만 나타내며 DB 연결 점검은 아닙니다.
2. 웹에서 운영자로 로그인합니다.
3. 깨끗한 DB에서 버전 A/XML을 실행하면 추가 4건, 이어 B/CSV를 실행하면 추가 1·변경 1·삭제 1·유지 2건입니다.
4. B/CSV를 다시 실행하면 기존 스냅샷을 재사용합니다.
5. API에서 조회자 계정의 목록 갱신·거래 업로드가 403인지 확인합니다.

현재 PostgreSQL 월별 집계 쿼리에 호환성 문제가 있으며 웹은 이 API를 목록과 함께 호출합니다. 로그인 뒤 전체 데이터 로딩이 실패하면 [기술 부채](arc42.md#11-위험과-기술-부채)의 집계 항목을 확인합니다. H2 테스트 성공은 이 문제를 해소하지 않습니다.

## 검증

```powershell
.\scripts\verify-local.ps1 -MavenPath C:\path\to\mvn.cmd
cd frontend
npm run build
```

검증 스크립트는 Maven 테스트와 TypeScript 검사를 실행합니다. 실행 서비스 시나리오·증빙 자동화는 미완료입니다. 로컬 로그와 생성 결과는 `artifacts/`에 두며 커밋하지 않습니다.

## 종료와 데이터 보존

웹과 API는 실행 터미널에서 Ctrl+C로 종료합니다. DB는 `docker compose stop postgres`로 중지합니다. 기존 볼륨 데이터는 유지됩니다. 볼륨 삭제·초기화는 일반 종료 절차에 포함하지 않습니다.
