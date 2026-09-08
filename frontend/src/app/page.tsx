"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";

type Page<T> = { items: T[]; totalElements: number };
type Entity = { externalId: string; entityName: string; countryCode: string; listingReason: string; status: string };
type Change = { type: string; externalId: string; entityName: string; differencesJson: string };
type Run = { idempotent: boolean; runId: number; requestedVersion: string; format: string; status: string; totalRows: number; addedCount: number; changedCount: number; removedCount: number; unchangedCount: number; safeErrorCode?: string };

const api = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8081/api/v1";
const countries = new Intl.DisplayNames(["ko"], { type: "region" });
const labels: Record<string, string> = {
  ACTIVE: "활성", INACTIVE: "비활성", RUNNING: "진행 중", COMPLETED: "완료", FAILED: "실패",
  ADDED: "추가", CHANGED: "변경", REMOVED: "삭제",
  TRADE_REVIEW: "거래 검토", DOCUMENT_REVIEW: "서류 검토", OWNERSHIP_REVIEW: "소유 관계 검토",
};
const fields: Record<string, string> = {
  entityName: "대상명", aliases: "다른 이름", countryCode: "국가", listingReason: "등재 사유", status: "상태",
};
const errors: Record<string, string> = {
  AUTHENTICATION_FAILED: "로그인 정보를 확인해 주세요.",
  AUTHENTICATION_REQUIRED: "로그인이 필요합니다. 새로고침한 뒤 다시 로그인해 주세요.",
  ACCESS_DENIED: "이 작업을 수행할 권한이 없습니다.",
  INVALID_REQUEST: "요청 내용을 확인한 뒤 다시 시도해 주세요.",
  PERSISTENCE_UNAVAILABLE: "데이터 저장소에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
  INTERNAL_ERROR: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
  FIXTURE_NOT_FOUND: "선택한 버전의 원본 파일을 찾을 수 없습니다.",
  FIXTURE_READ_FAILED: "원본 파일을 읽을 수 없습니다.",
  FIXTURE_METADATA_INVALID: "원본 파일의 버전 정보가 올바르지 않습니다.",
};
const count = (value: number) => value.toLocaleString("ko-KR");
const label = (value: string) => labels[value] ?? "확인 필요";
const errorMessage = (code?: string) => errors[code ?? ""] ?? "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";

function displayValue(field: string, value: unknown): string {
  if (typeof value !== "string" || !value) return "없음";
  if (field === "countryCode") return /^[A-Z]{2}$/.test(value) ? countries.of(value) ?? value : "확인 필요";
  if (field === "listingReason" || field === "status") return label(value);
  if (field === "aliases") return value.split("|").join(", ");
  return value;
}

function ChangeDetails({ change }: { change: Change }) {
  if (change.type === "ADDED") return <>새로 추가된 항목입니다.</>;
  if (change.type === "REMOVED") return <>이번 버전에서 제외된 항목입니다.</>;
  try {
    const differences: unknown = JSON.parse(change.differencesJson);
    if (!differences || typeof differences !== "object" || Array.isArray(differences)) throw new Error();
    const entries = Object.entries(differences).filter(([key, value]) =>
      Object.hasOwn(fields, key) && value && typeof value === "object" &&
      "previousValue" in value && "currentValue" in value);
    if (!entries.length) throw new Error();
    return <dl className="change-details">{entries.map(([key, value]) =>
      <div key={key}><dt>{fields[key]}</dt><dd>{displayValue(key, value.previousValue)} → {displayValue(key, value.currentValue)}</dd></div>
    )}</dl>;
  } catch {
    return <>변경 내용을 확인할 수 없습니다.</>;
  }
}

export default function DashboardPage() {
  const [token, setToken] = useState("");
  const [message, setMessage] = useState("운영자 계정으로 로그인하면 검토 대상 목록을 갱신할 수 있습니다.");
  const [entities, setEntities] = useState<Page<Entity>>({ items: [], totalElements: 0 });
  const [changes, setChanges] = useState<Page<Change>>({ items: [], totalElements: 0 });
  const [runs, setRuns] = useState<Page<Run>>({ items: [], totalElements: 0 });
  const [busy, setBusy] = useState(false);

  const request = useCallback(async <T,>(path: string, options: RequestInit = {}) => {
    let response: Response;
    try {
      response = await fetch(api + path, { ...options, headers: {
        "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}),
      } });
    } catch {
      throw new Error("서버에 연결할 수 없습니다. 연결 상태를 확인한 뒤 다시 시도해 주세요.");
    }
    let body;
    try { body = await response.json(); }
    catch { throw new Error("서버 응답을 확인할 수 없습니다. 잠시 후 다시 시도해 주세요."); }
    if (!response.ok) throw new Error(errorMessage(body?.code));
    return body as T;
  }, [token]);

  const load = useCallback(async () => {
    if (!token) return;
    const [e, c, r] = await Promise.all([
      request<Page<Entity>>("/watchlist/entities"), request<Page<Change>>("/watchlist/changes"),
      request<Page<Run>>("/watchlist/runs"),
    ]);
    setEntities(e); setChanges(c); setRuns(r);
  }, [token, request]);
  useEffect(() => { void load().catch(e => setMessage(e instanceof Error ? e.message : errorMessage())); }, [load]);

  async function login(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      const body = await request<{ accessToken: string }>("/auth/login", {
        method: "POST", body: JSON.stringify({ username: "operator@tradeops.test", password: "portfolio-demo" }),
      });
      setToken(body.accessToken);
      setMessage("운영자로 로그인했습니다. 목록과 실행 이력을 확인해 주세요.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : errorMessage());
    } finally { setBusy(false); }
  }

  async function run(version: string, format: "XML" | "CSV") {
    setBusy(true);
    try {
      const result = await request<Run>("/watchlist/runs", {
        method: "POST", body: JSON.stringify({ fixtureVersion: version, format }),
      });
      setMessage(result.status === "FAILED"
        ? `목록 갱신에 실패했습니다. ${errorMessage(result.safeErrorCode)} 현재 목록은 그대로 유지됩니다.`
        : result.idempotent ? "이미 반영된 원본입니다. 기존 목록을 유지합니다."
          : `목록을 갱신했습니다. 추가 ${count(result.addedCount)}건 · 변경 ${count(result.changedCount)}건 · 삭제 ${count(result.removedCount)}건`);
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : errorMessage());
    } finally { setBusy(false); }
  }

  return <div className="app-shell">
    <header className="topbar"><a className="brand" href="#main-content">트레이드옵스 <strong>허브</strong></a><span className="environment">가상 데이터 사용</span></header>
    <div className="workspace">
      <aside className="sidebar">
        <nav aria-label="운영 메뉴"><p>데이터 운영</p><a className="active" href="#overview">목록 갱신</a><a href="#entities">현재 목록</a><a href="#changes">변경 검토</a><a href="#runs">실행 이력</a></nav>
        <small>원본 버전별 목록과 변경 내역, 수집 이력을 확인하세요.</small>
      </aside>
      <main id="main-content">
        <section className="heading" id="overview"><div><span>검토 대상 관리</span><h1>검토 대상 목록 운영</h1><p>원본을 갱신하고 이전 버전과 비교해 변경 내용을 확인합니다.</p></div></section>
        <p className="notice" role="status">{message}</p>
        {!token ? <form className="panel login" onSubmit={login}>
          <span>업무 공간 접속</span><h2>운영자 로그인</h2><p>이 환경에 설정된 운영자 계정으로 접속합니다.</p>
          <button disabled={busy}>{busy ? "로그인 중…" : "운영자로 로그인"}</button>
        </form> : <>
          <section className="panel run-card"><div><span>목록 갱신</span><h2>원본 버전 불러오기</h2><p>갱신에 실패하면 현재 목록을 유지하며, 동일한 원본은 중복 반영하지 않습니다.</p></div>
            <div className="run-actions"><button disabled={busy} onClick={() => void run("2026-01-A", "XML")}>버전 A 불러오기 (XML)</button><button disabled={busy} className="secondary" onClick={() => void run("2026-02-B", "CSV")}>버전 B 불러오기 (CSV)</button></div>
          </section>
          <section className="metrics" aria-label="운영 현황">
            <Metric label="현재 대상" value={count(entities.totalElements) + "건"} /><Metric label="변경 내역" value={count(changes.totalElements) + "건"} />
            <Metric label="실행 이력" value={count(runs.totalElements) + "건"} /><Metric label="최근 실행 상태" value={runs.items[0] ? label(runs.items[0].status) : "실행 전"} />
          </section>
          <section className="panel" id="entities"><span>현재 목록</span><h2>최근 갱신된 검토 대상</h2>
            <Table caption="현재 검토 대상 목록" headers={["원본 식별자", "대상명", "국가", "등재 사유", "상태"]} empty={!entities.items.length} emptyText="아직 등록된 대상이 없습니다. 원본 버전을 불러와 주세요.">
              {entities.items.map(x => <tr key={x.externalId}><td><code>{x.externalId}</code></td><td>{x.entityName}</td><td>{displayValue("countryCode", x.countryCode)}</td><td>{displayValue("listingReason", x.listingReason)}</td><td><b className={x.status === "ACTIVE" ? "done" : "warn"}>{label(x.status)}</b></td></tr>)}
            </Table>
          </section>
          <section className="content">
            <article className="panel" id="changes"><span>변경 검토</span><h2>추가·변경·삭제 내역</h2>
              <Table caption="검토 대상 변경 내역" headers={["변경 유형", "대상", "상세 내용"]} empty={!changes.items.length} emptyText="아직 기록된 변경 내역이 없습니다.">
                {changes.items.map(x => <tr key={x.type + x.externalId}><td><b className="pending">{label(x.type)}</b></td><td><code>{x.externalId}</code><small>{x.entityName}</small></td><td><ChangeDetails change={x} /></td></tr>)}
              </Table>
            </article>
            <aside className="panel" id="runs"><span>실행 이력</span><h2>최근 수집 기록</h2>
              {!runs.items.length ? <p className="empty">아직 실행 이력이 없습니다.</p> : <ul className="run-list">{runs.items.map(x => <li key={x.runId}><strong>{x.requestedVersion}</strong><small>{x.format} · {label(x.status)} · {count(x.totalRows)}건</small>{x.status === "FAILED" && <small>{errorMessage(x.safeErrorCode)}</small>}</li>)}</ul>}
            </aside>
          </section>
        </>}
      </main>
    </div>
  </div>;
}

function Metric({ label, value }: { label: string; value: string }) {
  return <article><p>{label}</p><strong>{value}</strong></article>;
}
function Table({ caption, headers, children, empty, emptyText }: { caption: string; headers: string[]; children: React.ReactNode; empty: boolean; emptyText: string }) {
  return <div className="table-wrap" tabIndex={0} role="region" aria-label={caption}><table><caption>{caption}</caption><thead><tr>{headers.map(x => <th key={x} scope="col">{x}</th>)}</tr></thead><tbody>{empty ? <tr><td className="empty" colSpan={headers.length}>{emptyText}</td></tr> : children}</tbody></table></div>;
}
