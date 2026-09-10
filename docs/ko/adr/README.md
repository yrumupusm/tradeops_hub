# 아키텍처 결정 기록

[한국어](README.md) · [English](../../en/adr/README.md)

- [0001 — 웹·API와 관계형 저장소](0001-web-api-relational-storage.md): 분리 원칙 유지. 세부 구현은 당시 기록이며 현재 구성은 C4와 0005를 참고한다.
- [0002 — 버전별 스냅샷](0002-versioned-snapshots.md): 과거 결정. BIS 행 식별 방식은 0005로 대체했다.
- [0003 — 로컬 원본 경계](0003-local-source-boundary.md): 과거 결정. 로컬 전용 경계를 공개 BIS 수집으로 대체했다.
- [0004 — 권한과 추적](0004-authorization-and-traceability.md): 과거 결정. JWT·역할을 서버 세션과 고정 운영 책임자로 대체했다.
- [0005 — BIS 세션과 원본 추적](0005-bis-sessions-and-provenance.md): 현재 수집·검색·행 식별·계정 결정.
