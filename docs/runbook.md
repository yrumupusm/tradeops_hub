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

목록 화면은 목록·변경·실행 이력만 로드합니다. 거래 집계 API의 장애가 목록 로딩을 막지 않습니다. 소스 실행이 `FAILED`이면 안전한 오류 코드에 대응하는 한국어 안내와 현재 목록 보존 안내를 표시합니다. 알려지지 않은 오류는 공통 안내로 처리하며 서버의 원문 오류를 그대로 표시하지 않습니다.

운영 화면의 메뉴, 버튼, 상태, 오류 안내는 한국어로 표시합니다. 국가·등재 사유와 변경 필드도 한국어로 풀어 쓰며, 변경 전후 값을 함께 표시합니다. 원본 식별자·대상 고유명·버전·파일 형식은 원본 값을 유지합니다. 등록된 데이터가 없으면 다음 작업을 안내하고, 가상 데이터 사용 표시는 작은 화면에서도 유지합니다. API 필드와 저장된 원본 값은 변경하지 않습니다.

## 검증

```powershell
.\scripts\verify-local.ps1 -MavenPath C:\path\to\mvn.cmd
cd frontend
npm run build
```

로컬 게이트는 Maven 테스트, TypeScript 검사, 증빙 검증기 테스트를 실행합니다. 실제 PostgreSQL을 포함한 최종 검증은 다음 명령으로 실행합니다.

```powershell
.\scripts\verify-final.ps1 -MavenPath C:\path\to\mvn.cmd
```

이 명령은 로컬 `.env`를 읽지 않습니다. 임시 DB와 난수 인증 설정을 새로 만들고 15432(DB)·18081(API)·13000(웹) 포트를 사용합니다. 기존 Docker 볼륨을 사용하지 않으며 사용 중인 포트는 덮어쓰지 않고 실패합니다. 포트는 `-PostgresPort`, `-ApiPort`, `-WebPort`로 변경할 수 있습니다. 기본 종료 절차는 생성한 API·웹 프로세스와 임시 컨테이너만 정리합니다.

실행 결과는 `artifacts/final/<실행 ID>/runtime.json`과 `summary.json`, 최근 결과는 `artifacts/final/latest.json`입니다. 원본·시나리오 해시와 모든 검사 항목을 대조하므로 이전 실행의 파일을 새 성공 증빙으로 재사용할 수 없습니다. 상세 로그도 이 폴더에 저장하지만 공개 증빙에는 포함하지 않습니다.

이미 시작한 **빈 검증 DB**가 있다면 다음 명령을 사용할 수 있습니다. 목록 실행이나 거래가 이미 있으면 쓰기 전에 중단합니다.

```powershell
.\scripts\verify-runtime.ps1 -ApiBase http://127.0.0.1:18081/api/v1 -WebBase http://127.0.0.1:13000
```

런타임 게이트는 루프백 HTTP 주소만 허용하며, 기본 로컬 시드 계정을 사용합니다. 별도 비밀번호는 실행 셸의 `VERIFY_LOCAL_PASSWORD`로 전달합니다. 헤더·토큰·비밀번호·원본 행은 증빙에 저장하지 않습니다. 기본 게이트의 웹 검사는 HTML 응답을 검사하며 브라우저 조작을 대신하지 않습니다.

브라우저 디버깅이 필요하면 최종 게이트에 `-KeepRuntime`을 지정할 수 있습니다. 성공했을 때만 자원을 유지하며 `runtime-info.json`에 해당 컨테이너·프로세스 ID를 남깁니다. 검토 후 해당 ID의 자원만 종료해야 합니다. 로컬 로그와 생성 결과는 `artifacts/`에 두며 커밋하지 않습니다.

## 종료와 데이터 보존

웹과 API는 실행 터미널에서 Ctrl+C로 종료합니다. DB는 `docker compose stop postgres`로 중지합니다. 기존 볼륨 데이터는 유지됩니다. 볼륨 삭제·초기화는 일반 종료 절차에 포함하지 않습니다.
