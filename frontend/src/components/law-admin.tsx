"use client";

import { useEffect, useRef, useState } from "react";
import { request } from "../lib/api";

type Row = Record<string, string | number | boolean | null>;
type Data = { items?: Row[]; total?: number; totalCount?: number };
type Detail = { title: string; articles: Row[]; revisions?: Row[] };
const tabs = [
  ["status", "운영 현황"],
  ["laws", "보유 법령"],
  ["search-logs", "검색 로그"],
];
const labels: Record<string, string> = {
  OK: "정상",
  LOW_CONFIDENCE: "확인 필요",
  INSUFFICIENT_INFO: "정보 부족",
  FAILED: "실패",
  SUCCESS: "성공",
  SUCCEEDED: "성공",
  RUNNING: "진행 중",
  COMPLETED: "완료",
  ready: "정상",
  LAW: "법률",
  ENFORCEMENT_DECREE: "시행령",
  ENFORCEMENT_RULE: "시행규칙",
  failed: "실패",
  ok: "정상",
  success: "성공",
  stale: "갱신 확인 필요",
  empty: "색인 없음",
  EXPLORATORY: "탐색형",
  CONFIRMATORY: "확인형",
  METADATA: "정보 확인",
  REVISION_COMPARE: "개정 비교",
  INSUFFICIENT: "정보 부족",
};
function display(value: unknown) {
  if (typeof value === "number") return value.toLocaleString("ko-KR");
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}T/.test(value)) {
    const date = new Date(value);
    if (!Number.isNaN(date.getTime())) return date.toLocaleString("ko-KR");
  }
  return value == null ? "—" : (labels[String(value)] ?? String(value));
}
const columns: Record<string, [string, string][]> = {
  laws: [
    ["title", "법령명"],
    ["lawType", "종류"],
    ["lawNumber", "법령 번호"],
    ["articleCount", "조문 수"],
    ["revisionCount", "개정 회차"],
  ],
  "ingestion-runs": [
    ["ingestionRunId", "실행 번호"],
    ["startedAt", "시작 시각"],
    ["finishedAt", "완료 시각"],
    ["status", "상태"],
    ["filesProcessed", "처리 파일"],
    ["filesFailed", "실패 파일"],
  ],
  "search-logs": [
    ["createdAt", "검색 시각"],
    ["questionType", "질문 유형"],
    ["status", "상태"],
    ["citedArticleCount", "인용 수"],
    ["requestId", "요청 ID"],
  ],
  "agent-traces": [
    ["createdAt", "실행 시각"],
    ["stepName", "처리 단계"],
    ["status", "상태"],
    ["latencyMs", "소요 시간(ms)"],
    ["requestId", "요청 ID"],
  ],
};

export default function LawAdmin({
  onAuthError,
}: {
  onAuthError: (error: unknown) => void;
}) {
  const [action, setAction] = useState("");
  const [running, setRunning] = useState(false);
  const [actionMessage, setActionMessage] = useState("");
  const [actionResult, setActionResult] = useState<Record<
    string,
    unknown
  > | null>(null);
  const actionNames: Record<string, string> = {
    "sync-source": "자료 동기화 및 수집",
    "ingest-local": "법령 파일 수집",
    reindex: "재색인",
    "provider-smoke-test": "외부 서비스 연결 점검",
  };
  async function execute() {
    if (running || !action) return;
    setRunning(true);
    setActionMessage("");
    setActionResult(null);
    try {
      const result = await request<Record<string, unknown>>(
        `/law-admin/actions/${action}`,
        { method: "POST", body: JSON.stringify({}) },
      );
      setActionResult(
        result.ingestion && typeof result.ingestion === "object"
          ? { ...result, ...result.ingestion }
          : result,
      );
      setActionMessage("처리 결과를 확인하세요.");
      setRevision((v) => v + 1);
    } catch (e) {
      setActionMessage(
        (e instanceof Error ? e.message : "작업 응답을 확인하지 못했습니다.") +
          " 작업이 서버에서 계속될 수 있으니 수집 이력과 상태를 확인한 후 다시 실행하세요.",
      );
      onAuthError(e);
    } finally {
      setRunning(false);
      setAction("");
    }
  }
  const [tab, setTab] = useState("status");
  const [draft, setDraft] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<Data & Record<string, unknown>>({});
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<number | null>(null);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [detailError, setDetailError] = useState("");
  useEffect(() => {
    if (tab === "search-logs") return;
    const controller = new AbortController();
    setPending(true);
    setError("");
    setData({});
    const params = new URLSearchParams({
      page: String(page),
      ...(tab === "laws" ? { q: query } : { requestId: query }),
    });
    request<Data & Record<string, unknown>>(`/law-admin/${tab}?${params}`, {
      signal: controller.signal,
    })
      .then((value) => {
        if (!controller.signal.aborted) setData(value);
      })
      .catch((e) => {
        if (!controller.signal.aborted) {
          setError(e instanceof Error ? e.message : "조회에 실패했습니다.");
          onAuthError(e);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setPending(false);
      });
    return () => controller.abort();
    // Authentication callback belongs to the parent console; requests follow filters only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, page, query, revision]);
  useEffect(() => {
    setDetail(null);
    setDetailError("");
    if (selected === null) return;
    const controller = new AbortController();
    Promise.all([
      request<Detail>(`/law-admin/laws/${selected}`, {
        signal: controller.signal,
      }),
      request<{ revisions: Row[] }>(`/law-admin/laws/${selected}/revisions`, {
        signal: controller.signal,
      }),
    ])
      .then(([law, history]) => {
        if (!controller.signal.aborted)
          setDetail({ ...law, revisions: history.revisions });
      })
      .catch((e) => {
        if (!controller.signal.aborted) {
          setDetailError(
            e instanceof Error ? e.message : "법령 상세 조회에 실패했습니다.",
          );
          onAuthError(e);
        }
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected]);
  function changeTab(value: string, filter = "") {
    setTab(value);
    setPage(1);
    setQuery(filter);
    setDraft(filter);
    setSelected(null);
  }
  return (
    <div className="law-admin">
      <nav className="law-admin-tabs" aria-label="법령 관리 항목">
        {tabs.map(([value, label]) => (
          <button
            key={value}
            className={tab === value ? "primary" : ""}
            aria-pressed={tab === value}
            onClick={() => changeTab(value)}
          >
            {label}
          </button>
        ))}
      </nav>
      {tab !== "search-logs" && (
        <>
          <section className="panel">
            <div className="law-admin-toolbar">
              <h2>{tabs.find(([value]) => value === tab)?.[1]}</h2>
              <button
                disabled={pending}
                onClick={() => setRevision((v) => v + 1)}
              >
                새로고침
              </button>
            </div>
            {(tab === "laws" || tab === "agent-traces") && (
              <form
                className="law-admin-toolbar"
                onSubmit={(e) => {
                  e.preventDefault();
                  setQuery(draft.trim());
                  setPage(1);
                  setRevision((v) => v + 1);
                  setSelected(null);
                }}
              >
                <label>
                  {tab === "laws" ? "법령명" : "요청 ID"}
                  <input
                    value={draft}
                    maxLength={tab === "laws" ? 200 : 36}
                    onChange={(e) => setDraft(e.target.value)}
                  />
                </label>
                <button disabled={pending}>조회</button>
                <button
                  type="button"
                  onClick={() => {
                    setDraft("");
                    setQuery("");
                    setPage(1);
                  }}
                >
                  초기화
                </button>
              </form>
            )}
            {error && <p role="alert">{error}</p>}
            {pending ? (
              <p role="status">불러오는 중입니다.</p>
            ) : (
              !error &&
              (tab === "status" ? (
                <dl className="law-admin-stats">
                  {[
                    ["indexStatus", "색인 상태"],
                    ["lawsCount", "보유 법령"],
                    ["articlesCount", "조문"],
                    ["indexedArticlesCount", "색인 조문"],
                    ["unindexedArticlesCount", "미색인 조문"],
                    ["searchLogCount", "검색 로그"],
                    ["lastSnapshotVersion", "최근 스냅샷"],
                    ["lastIndexedAt", "최근 색인 시각"],
                  ].map(([key, label]) => (
                    <div key={key}>
                      <dt>{label}</dt>
                      <dd>{display(data[key])}</dd>
                    </div>
                  ))}
                </dl>
              ) : (
                <>
                  <div
                    className="law-admin-table"
                    role="region"
                    aria-label="조회 결과"
                    tabIndex={0}
                  >
                    <table>
                      <thead>
                        <tr>
                          {columns[tab].map(([key, label]) => (
                            <th key={key}>{label}</th>
                          ))}
                          {(tab === "laws" || tab === "search-logs") && (
                            <th>상세</th>
                          )}
                        </tr>
                      </thead>
                      <tbody>
                        {(data.items ?? []).map((row, i) => (
                          <tr
                            key={String(
                              row.id ?? row.lawId ?? row.ingestionRunId ?? i,
                            )}
                          >
                            {columns[tab].map(([key]) => (
                              <td key={key}>{display(row[key])}</td>
                            ))}
                            {tab === "laws" && (
                              <td>
                                <button
                                  aria-expanded={selected === Number(row.lawId)}
                                  onClick={() =>
                                    setSelected(
                                      selected === Number(row.lawId)
                                        ? null
                                        : Number(row.lawId),
                                    )
                                  }
                                >
                                  조문 보기
                                </button>
                              </td>
                            )}
                            {tab === "search-logs" && (
                              <td>
                                <button
                                  onClick={() =>
                                    changeTab(
                                      "agent-traces",
                                      String(row.requestId),
                                    )
                                  }
                                >
                                  실행 추적
                                </button>
                              </td>
                            )}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {!data.items?.length && <p>조회 결과가 없습니다.</p>}
                  <p className="muted">
                    총{" "}
                    {Number(data.total ?? data.totalCount ?? 0).toLocaleString(
                      "ko-KR",
                    )}
                    건
                    {tab === "search-logs"
                      ? " · 최근 50건 표시"
                      : tab === "agent-traces"
                        ? " · 최대 100건 표시"
                        : ""}
                  </p>
                  {tab === "laws" && (
                    <div className="law-admin-tabs">
                      <button
                        disabled={page === 1}
                        onClick={() => {
                          setPage((p) => p - 1);
                          setSelected(null);
                        }}
                      >
                        이전
                      </button>
                      <span>{page}페이지</span>
                      <button
                        disabled={page * 20 >= Number(data.total ?? 0)}
                        onClick={() => {
                          setPage((p) => p + 1);
                          setSelected(null);
                        }}
                      >
                        다음
                      </button>
                    </div>
                  )}
                </>
              ))
            )}
          </section>
          {tab === "status" && (
            <section className="panel">
              <h2>관리 작업</h2>
              <p className="muted">
                법령 서버에 설정된 자료 저장소와 경로를 사용합니다.
                수집·동기화·재색인은 법령 데이터와 색인을 갱신합니다.
              </p>
              <div className="law-admin-tabs">
                {Object.entries(actionNames).map(([key, label]) => (
                  <button
                    key={key}
                    disabled={
                      running ||
                      pending ||
                      !!error ||
                      (key === "reindex" && data.reindexEnabled !== true)
                    }
                    onClick={() => {
                      setAction(key);
                      setActionMessage("");
                    }}
                  >
                    {label}
                  </button>
                ))}
              </div>
              {data.reindexEnabled === false && (
                <p className="muted">
                  재색인은 법령 서버에서 비활성화되어 있습니다.
                </p>
              )}
              {action && !running && (
                <div role="group" aria-label="관리 작업 확인">
                  <p>
                    {actionNames[action]} 작업을 실행할까요?
                    {action !== "provider-smoke-test"
                      ? " 기존 자료 또는 색인이 변경될 수 있습니다."
                      : " 외부 모델 서비스 호출이 발생합니다."}
                  </p>
                  <button className="primary" onClick={execute}>
                    실행
                  </button>{" "}
                  <button onClick={() => setAction("")}>취소</button>
                </div>
              )}
              {running && (
                <p role="status">
                  관리 작업을 처리하는 중입니다. 완료까지 기다려 주세요.
                </p>
              )}
              <p role="status">{actionMessage}</p>
              {actionResult && (
                <dl className="law-admin-stats">
                  {Object.entries(actionResult)
                    .filter(([, value]) => typeof value !== "object")
                    .map(([key, value]) => (
                      <div key={key}>
                        <dt>
                          {{
                            status: "상태",
                            llmStatus: "답변 모델",
                            embeddingStatus: "임베딩",
                            rerankerStatus: "재정렬",
                            indexedArticles: "색인 조문",
                            failedArticles: "실패 조문",
                            lawsImported: "수집 법령",
                            articlesImported: "수집 조문",
                            filesProcessed: "처리 파일",
                            filesFailed: "실패 파일",
                            startedAt: "시작 시각",
                            finishedAt: "완료 시각",
                            snapshotVersion: "스냅샷",
                            ingestionRunId: "실행 번호",
                            embeddingDimensions: "벡터 차원",
                            rerankedCount: "재정렬 건수",
                            action: "동기화 결과",
                            commitHash: "자료 리비전",
                          }[key] ?? key}
                        </dt>
                        <dd>{display(value)}</dd>
                      </div>
                    ))}
                </dl>
              )}
            </section>
          )}
          {tab === "status" && (
            <RunHistory revision={revision} onAuthError={onAuthError} />
          )}
        </>
      )}
      {tab === "search-logs" && <SearchLogs onAuthError={onAuthError} />}
      {selected !== null && (
        <section className="panel" aria-label="법령 상세">
          {detailError ? (
            <p role="alert">{detailError}</p>
          ) : !detail ? (
            <p role="status">법령 상세를 불러오는 중입니다.</p>
          ) : (
            <>
              <h2>{detail.title}</h2>
              <h3>개정 이력</h3>
              {detail.revisions?.map((r, i) => (
                <p key={i}>
                  {display(r.effectiveFrom)} · {display(r.amendmentKind)} · 조문{" "}
                  {display(r.articleCount)}건
                </p>
              ))}
              <h3>조문</h3>
              {detail.articles.map((a, i) => (
                <details key={i}>
                  <summary>
                    {display(a.articleNumber)} {a.articleTitle}
                  </summary>
                  <p className="law-admin-content">{a.content}</p>
                </details>
              ))}
            </>
          )}
        </section>
      )}
    </div>
  );
}

type AuthFailure = { onAuthError: (error: unknown) => void };
function useAdminList(
  path: string | null,
  revision: number,
  onAuthError: AuthFailure["onAuthError"],
) {
  const [state, setState] = useState<{
    path: string | null;
    data: Data;
    pending: boolean;
    error: string;
  }>({ path: null, data: {}, pending: false, error: "" });
  const auth = useRef(onAuthError);
  auth.current = onAuthError;
  useEffect(() => {
    const controller = new AbortController();
    setState({ path, data: {}, pending: !!path, error: "" });
    if (path)
      request<Data>(path, { signal: controller.signal })
        .then((data) => {
          if (!controller.signal.aborted)
            setState({ path, data, pending: false, error: "" });
        })
        .catch((error) => {
          if (!controller.signal.aborted) {
            setState({
              path,
              data: {},
              pending: false,
              error:
                error instanceof Error ? error.message : "조회에 실패했습니다.",
            });
            auth.current(error);
          }
        });
    return () => controller.abort();
  }, [path, revision]);
  return state.path === path
    ? state
    : { path, data: {}, pending: !!path, error: "" };
}
function DataTable({
  data,
  fields,
  name,
}: {
  data: Data;
  fields: [string, string][];
  name: string;
}) {
  return (
    <>
      <div
        className="law-admin-table"
        role="region"
        aria-label={name}
        tabIndex={0}
      >
        <table>
          <thead>
            <tr>
              {fields.map(([key, label]) => (
                <th key={key}>{label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.items?.map((row, i) => (
              <tr key={String(row.id ?? row.ingestionRunId ?? i)}>
                {fields.map(([key]) => (
                  <td key={key}>{display(row[key])}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!data.items?.length && <p>조회 결과가 없습니다.</p>}
    </>
  );
}
function RunHistory({
  revision,
  onAuthError,
}: AuthFailure & { revision: number }) {
  const [refresh, setRefresh] = useState(0);
  const state = useAdminList(
    "/law-admin/ingestion-runs",
    revision + refresh,
    onAuthError,
  );
  return (
    <section className="panel" aria-label="수집 이력">
      <div className="law-admin-toolbar">
        <h2>수집 이력</h2>
        <button
          disabled={state.pending}
          onClick={() => setRefresh((v) => v + 1)}
        >
          수집 이력 새로고침
        </button>
      </div>
      {state.pending ? (
        <p role="status">수집 이력을 불러오는 중입니다.</p>
      ) : state.error ? (
        <p role="alert">{state.error}</p>
      ) : (
        <>
          <DataTable
            data={state.data}
            fields={columns["ingestion-runs"]}
            name="수집 이력 목록"
          />
          <p className="muted">
            총 {display(state.data.totalCount ?? 0)}건 · 최근 20건 표시
          </p>
        </>
      )}
    </section>
  );
}
function SearchLogs({ onAuthError }: AuthFailure) {
  const [revision, setRevision] = useState(0);
  const [traceRevision, setTraceRevision] = useState(0);
  const [selected, setSelected] = useState("");
  const [draft, setDraft] = useState("");
  const [inputError, setInputError] = useState("");
  const logs = useAdminList("/law-admin/search-logs", revision, onAuthError);
  const traces = useAdminList(
    selected
      ? `/law-admin/agent-traces?requestId=${encodeURIComponent(selected)}`
      : null,
    traceRevision,
    onAuthError,
  );
  function select(id: string) {
    setSelected(id);
    setDraft(id);
    setInputError("");
    setTraceRevision((v) => v + 1);
  }
  return (
    <div className="law-admin-log-split">
      <section className="panel" aria-label="검색 로그 목록">
        <div className="law-admin-toolbar">
          <h2>검색 로그</h2>
          <button
            disabled={logs.pending}
            onClick={() => setRevision((v) => v + 1)}
          >
            새로고침
          </button>
        </div>
        {logs.pending ? (
          <p role="status">검색 로그를 불러오는 중입니다.</p>
        ) : logs.error ? (
          <p role="alert">{logs.error}</p>
        ) : (
          <>
            <div
              className="law-admin-table"
              role="region"
              aria-label="검색 로그 표"
              tabIndex={0}
            >
              <table>
                <thead>
                  <tr>
                    <th>검색 시각</th>
                    <th>상태</th>
                    <th>인용 수</th>
                    <th>요청 ID</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.data.items?.map((row, i) => (
                    <tr
                      key={String(row.id ?? i)}
                      className={
                        row.requestId === selected ? "law-log-selected" : ""
                      }
                    >
                      <td>{display(row.createdAt)}</td>
                      <td>{display(row.status)}</td>
                      <td>{display(row.citedArticleCount)}</td>
                      <td>
                        <button
                          className="law-request-link"
                          aria-pressed={row.requestId === selected}
                          aria-controls="law-request-traces"
                          onClick={() => select(String(row.requestId))}
                        >
                          {row.requestId}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!logs.data.items?.length && <p>검색 로그가 없습니다.</p>}
            <p className="muted">
              총 {display(logs.data.total ?? 0)}건 · 최근 50건 표시
            </p>
          </>
        )}
      </section>
      <section
        className="panel"
        id="law-request-traces"
        aria-label="선택한 요청의 실행 추적"
      >
        <h2>실행 추적</h2>
        <form
          className="law-admin-toolbar"
          onSubmit={(e) => {
            e.preventDefault();
            const id = draft.trim();
            if (
              !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(
                id,
              )
            ) {
              setInputError("올바른 요청 ID를 입력하세요.");
              return;
            }
            select(id);
          }}
        >
          <label>
            요청 ID
            <input
              value={draft}
              maxLength={36}
              onChange={(e) => setDraft(e.target.value)}
            />
          </label>
          <button>조회</button>
          <button
            type="button"
            onClick={() => {
              setSelected("");
              setDraft("");
              setInputError("");
            }}
          >
            초기화
          </button>
        </form>
        {inputError && <p role="alert">{inputError}</p>}
        {selected && (
          <p className="law-selected-request">선택한 요청: {selected}</p>
        )}
        <div aria-live="polite">
          {!selected ? (
            <p className="muted">
              검색 로그를 선택하면 처리 과정을 볼 수 있습니다.
            </p>
          ) : traces.pending ? (
            <p role="status">실행 추적을 불러오는 중입니다.</p>
          ) : traces.error ? (
            <p role="alert">{traces.error}</p>
          ) : (
            <>
              <DataTable
                data={traces.data}
                fields={columns["agent-traces"].filter(
                  ([key]) => key !== "requestId",
                )}
                name="선택한 요청의 처리 단계"
              />
              <p className="muted">
                총 {display(traces.data.total ?? 0)}건 · 최대 100건 표시
              </p>
            </>
          )}
        </div>
      </section>
    </div>
  );
}
