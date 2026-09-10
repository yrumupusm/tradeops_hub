# 실행 준비 점검

[한국어](../ko/runtime-readiness.md) | [English](../en/runtime-readiness.md)

서버를 수동으로 시작하거나 재시작하기 전에 이 목록을 확인합니다. 아래 명령은 Spring Boot 프로세스를 시작하거나 중지·재시작하지 않습니다.

## 1. 설정 확인

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-readiness.ps1 -RequireExternalProviders
```

Qdrant를 사용하는 실행 환경:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-readiness.ps1 -RequireExternalProviders -RequireQdrant
```

Cohere 재정렬을 사용하는 실행 환경:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-readiness.ps1 -RequireExternalProviders -RequireCohere
```

스크립트는 `target/readiness-summary.json`을 생성합니다. 비밀값의 존재 여부만 확인하고 API 키 값은 출력하지 않습니다.
`VECTOR_PROVIDER=inmemory` 또는 `RERANKER_PROVIDER=mock`이면 경고가 있어도 통과할 수 있습니다. 이 경우 런타임 검증은 가능하지만, 해당 `-RequireQdrant` 또는 `-RequireCohere` 옵션을 사용하지 않으면 Qdrant나 Cohere 사용을 최종 검증 근거로 삼을 수 없습니다.

## 2. 실행 중인 서버 검증

서버를 직접 시작하거나 재시작한 뒤 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-final.ps1
```

`verify-final.ps1`은 준비 상태, 제공자 연결을 포함한 런타임 점검, 시나리오 하네스, 검증 보고서 생성, 완료 근거 검증을 순서대로 실행합니다. 서버를 시작하거나 중지·재시작하지 않습니다.
최종 검증에는 이 명령을 기본으로 사용합니다. 아래 개별 실행 순서는 특정 단계의 문제를 점검할 때 사용합니다.

개별 실행 순서:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1 -IncludeProviderSmoke
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-scenarios.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-evidence-report.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-completion-evidence.ps1
```

예상 결과 파일:

```text
target/readiness-summary.json
target/runtime-evidence/runtime-summary.json
target/scenario-responses/scenario-summary.json
target/evidence-report.md
```

이 파일들은 제공자 연결, 한국어 답변 정책, 인용 동작, requestId 감사 연결, 시나리오 품질을 확인하는 최종 검증 자료입니다.

`target/readiness-summary.json`이 있으면 `export-evidence-report.ps1`이 이를 포함하므로, 설정 준비 상태·런타임 연결 점검·시나리오 품질을 한 보고서에서 확인할 수 있습니다.

`verify-completion-evidence.ps1`은 최종 통과 조건을 검사합니다. 준비 상태 오류, 제공자 연결 점검 누락, 런타임 인용 누락, 시나리오 감사 연결 누락, 금지 문구 노출, 최종 보고서 부재 중 하나라도 있으면 실패합니다.
