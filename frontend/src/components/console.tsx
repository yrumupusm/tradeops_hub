"use client";
import { FormEvent, ReactNode, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { ApiError, exportCsv, message, request, resetCsrf } from "../lib/api";

type Row = Record<string, unknown>;
type User = {
  id: number;
  username: string;
  owner: boolean;
  mustChangePassword: boolean;
};
type Page = { items: Row[]; totalElements: number; page: number; size: number };
type Query = {
  q: string;
  mode: string;
  source: string;
  country: string;
  sort: string;
  page: number;
  size: number;
  snapshots?: Record<string, number>;
  saveHistory?: boolean;
};
const number = (v: unknown) => Number(v ?? 0).toLocaleString("ko-KR");
const value = (v: unknown) => (v == null || v === "" ? "—" : String(v));
const date = (v: unknown) =>
  v ? new Date(String(v)).toLocaleString("ko-KR") : "—";
const statuses: Record<string, string> = {
  RUNNING: "수집 중",
  COMPLETED: "완료",
  FAILED: "실패",
  HELD: "반영 보류",
  DISCOVERING: "링크 확인",
  DOWNLOADING: "다운로드",
  VALIDATING: "원본 검증",
  SAVING: "DB 저장",
  FINISHED: "처리 완료",
  VALID: "정상",
  WARNING: "확인 항목 있음",
  MANUAL: "수동",
  SCHEDULED: "예약",
  ADDED: "추가",
  REMOVED: "제외",
  NAME: "이름",
  ALIAS: "별칭",
};
const events: Record<string, string> = {
  LOGIN_SUCCEEDED: "로그인",
  LOGIN_FAILED: "로그인 실패",
  LOGOUT: "로그아웃",
  ACCOUNT_CREATED: "계정 생성",
  ACCOUNT_STATE_CHANGED: "계정 상태 변경",
  PASSWORD_RESET: "비밀번호 초기화",
  PASSWORD_CHANGED: "비밀번호 변경",
  COLLECTION_REQUESTED: "수집 요청",
  COLLECTION_FINISHED: "수집 종료",
  COLLECTION_FAILED: "수집 실패",
  COLLECTION_HELD: "반영 보류",
  COLLECTION_APPROVED: "보류 결과 반영",
  SOURCE_FILE_DOWNLOAD: "원본 다운로드",
  EXPORT_GENERATED: "CSV 생성",
  SEARCH_HISTORY_DELETED: "검색 이력 삭제",
  SEARCH_EXECUTED: "우려거래자 검색",
};
const labels: Record<string, string> = {
  Name: "이름",
  Street_Address: "도로명 주소",
  City: "도시",
  State: "주",
  Country: "국가",
  Postal_Code: "우편번호",
  Effective_Date: "발효일",
  Expiration_Date: "만료일",
  Standard_Order: "표준 명령",
  Last_Update: "원본 변경일",
  Action: "변경 설명",
  FR_Citation: "연방 관보",
  Address: "주소",
  "Source List": "출처 목록",
  "Entity Number": "원본 번호",
  "SDN Type": "대상 유형",
  Programs: "프로그램",
  Title: "직함",
  "State/Province": "주·지역",
  "Postal Code": "우편번호",
  "Federal Register Notice": "연방 관보",
  "Effective Date": "발효일",
  "Date Lifted/Waived/Expired": "해제·면제·만료일",
  "Standard Order": "표준 명령",
  "License Requirement": "허가 요건",
  "License Policy": "허가 검토 정책",
  "Call Sign": "호출 부호",
  "Vessel Type": "선박 유형",
  "Gross Tonnage": "총 톤수",
  "Gross Register Tonnage": "등록 총 톤수",
  "Vessel Flag": "선적 국가",
  "Vessel Owner": "선박 소유자",
  "Remarks/Notes": "비고",
  "Address Number": "주소 번호",
  "Address Remarks": "주소 비고",
  "Alternate Number": "대체 번호",
  "Alternate Type": "대체 유형",
  "Alternate Name": "다른 이름",
  "Alternate Remarks": "대체 정보 비고",
  "Web Link": "원문 링크",
};
function parsed(v: unknown): Row {
  try {
    return typeof v === "string" ? JSON.parse(v) : ((v as Row) ?? {});
  } catch {
    return {};
  }
}
function Badge({ status }: { status: unknown }) {
  return (
    <span className={"badge " + String(status).toLowerCase()}>
      {statuses[String(status)] ?? value(status)}
    </span>
  );
}
function Table({
  caption,
  headers,
  children,
  empty,
}: {
  caption: string;
  headers: string[];
  children: ReactNode;
  empty: boolean;
}) {
  return (
    <div className="table-wrap" role="region" aria-label={caption} tabIndex={0}>
      <table>
        <caption>{caption}</caption>
        <thead>
          <tr>
            {headers.map((h) => (
              <th scope="col" key={h}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {empty ? (
            <tr>
              <td colSpan={headers.length} className="empty">
                표시할 항목이 없습니다.
              </td>
            </tr>
          ) : (
            children
          )}
        </tbody>
      </table>
    </div>
  );
}
function Pager({
  page,
  total,
  size,
  onPage,
}: {
  page: number;
  total: number;
  size: number;
  onPage: (v: number) => void;
}) {
  return (
    <div className="pager">
      <span>
        총 {number(total)}건 · {page + 1} /{" "}
        {Math.max(1, Math.ceil(total / size))}페이지
      </span>
      <button disabled={page === 0} onClick={() => onPage(page - 1)}>
        이전
      </button>
      <button
        disabled={(page + 1) * size >= total}
        onClick={() => onPage(page + 1)}
      >
        다음
      </button>
    </div>
  );
}
export default function Console() {
  const router = useRouter(),
    path = usePathname(),
    params = useSearchParams();
  const [user, setUser] = useState<User | null>(null),
    [ready, setReady] = useState(false),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false);
  const [data, setData] = useState<Page>({
      items: [],
      totalElements: 0,
      page: 0,
      size: 20,
    }),
    [sources, setSources] = useState<Row[]>([]),
    [countries, setCountries] = useState<Row[]>([]),
    [settings, setSettings] = useState<Row>({}),
    [detail, setDetail] = useState<Row>({}),
    [issues, setIssues] = useState<Row[]>([]),
    [changes, setChanges] = useState<Row[]>([]),
    [version, setVersion] = useState<Record<string, number>>({});
  const [q, setQ] = useState(params.get("q") ?? ""),
    [mode, setMode] = useState(params.get("mode") ?? "HYBRID"),
    [source, setSource] = useState(params.get("source") ?? ""),
    [country, setCountry] = useState(params.get("country") ?? ""),
    [sort, setSort] = useState(params.get("sort") ?? "score"),
    [size, setSize] = useState(Number(params.get("size") ?? 20));
  const [auditActor, setAuditActor] = useState(""),
    [auditEvent, setAuditEvent] = useState(""),
    [auditFrom, setAuditFrom] = useState(""),
    [auditTo, setAuditTo] = useState("");
  const page = Number(params.get("page") ?? 0),
    isSearch = path === "/" || path === "/watchlist/search";
  const fail = useCallback(
    (error: unknown) => {
      setNotice(
        error instanceof Error ? error.message : "작업을 처리하지 못했습니다.",
      );
      if (
        error instanceof ApiError &&
        (error.status === 401 || error.code === "CSRF_INVALID")
      ) {
        setUser(null);
        router.replace("/login");
      }
      if (
        error instanceof ApiError &&
        error.code === "PASSWORD_CHANGE_REQUIRED"
      )
        router.replace("/account");
    },
    [router],
  );
  useEffect(() => {
    request<User>("/auth/me")
      .then((u) => {
        setUser(u);
        if (path === "/login")
          router.replace(
            u.mustChangePassword ? "/account" : "/watchlist/search",
          );
        else if (u.mustChangePassword && path !== "/account")
          router.replace("/account");
      })
      .catch(() => {
        if (path !== "/login") router.replace("/login");
      })
      .finally(() => setReady(true));
  }, [router, path]);
  const getQuery = useCallback(
    (overrides: Partial<Query> = {}): Query => ({
      q: params.get("q") ?? "",
      mode: params.get("mode") ?? "HYBRID",
      source: params.get("source") ?? "",
      country: params.get("country") ?? "",
      sort: params.get("sort") ?? "score",
      page: Number(params.get("page") ?? 0),
      size: Number(params.get("size") ?? 20),
      ...(params.has("snapshots")
        ? {
            snapshots: parsed(params.get("snapshots")) as Record<
              string,
              number
            >,
          }
        : {}),
      ...overrides,
    }),
    [params],
  );
  const [loadedLocation, setLoadedLocation] = useState("");
  const [recentSearches, setRecentSearches] = useState<Row[]>([]);
  useEffect(() => {
    setRecentSearches([]);
    if (!user || user.mustChangePassword || !isSearch) return;
    const controller = new AbortController();
    request<Row[]>("/search-history/recent", { signal: controller.signal })
      .then(setRecentSearches)
      .catch(() => {});
    return () => controller.abort();
  }, [user, isSearch]);
  useEffect(() => {
    if (!user || user.mustChangePassword || !isSearch) return;
    const controller = new AbortController();
    request<Row[]>("/watchlist/countries", { signal: controller.signal })
      .then(setCountries)
      .catch(() => {});
    return () => controller.abort();
  }, [user, isSearch]);
  const currentLocation = path + "?" + params.toString();
  const viewReady = loadedLocation === currentLocation;
  const load = useCallback(
    async (signal?: AbortSignal) => {
      if (
        !user ||
        path === "/login" ||
        (user.mustChangePassword && path !== "/account")
      )
        return;
      try {
        if (isSearch) {
          const result = await request<
            Page & { snapshots: Record<string, number> }
          >("/watchlist/search", {
            method: "POST",
            body: JSON.stringify(getQuery({ saveHistory: false })),
            signal,
          });
          setData(result);
          setVersion(result.snapshots);
          if (
            !params.has("snapshots") &&
            Object.keys(result.snapshots).length
          ) {
            const p = new URLSearchParams(params);
            p.set("snapshots", JSON.stringify(result.snapshots));
            router.replace("/watchlist/search?" + p.toString());
          }
        } else if (path === "/watchlist/sources") {
          const r = await request<{ items: Row[]; settings: Row }>(
            "/watchlist/sources",
            { signal },
          );
          setSources(r.items);
          setSettings(r.settings);
        } else if (path === "/watchlist/runs") {
          setData(
            await request<Page>(
              path.replace("/watchlist", "/watchlist") +
                "?page=" +
                page +
                "&size=20",
              { signal },
            ),
          );
        } else if (path.startsWith("/watchlist/runs/")) {
          const id = path.split("/").pop();
          const r = await request<{ run: Row; issues: Row[] }>(
            "/watchlist/runs/" + id,
            { signal },
          );
          setDetail(r.run);
          setIssues(r.issues);
          setChanges(
            await request<Row[]>(
              "/watchlist/runs/" + id + "/changes?page=" + page + "&size=20",
              { signal },
            ),
          );
        } else if (path.startsWith("/watchlist/records/"))
          setDetail(await request<Row>(path, { signal }));
        else if (path === "/search-history")
          setData(
            await request<Page>("/search-history?page=" + page + "&size=20", {
              signal,
            }),
          );
        else if (path === "/users") {
          const rows = await request<Row[]>("/users", { signal });
          setData({
            items: rows,
            totalElements: rows.length,
            page: 0,
            size: rows.length,
          });
        } else if (path === "/audit") {
          const p = new URLSearchParams({
            actor: auditActor,
            event: auditEvent,
            page: String(page),
            size: "20",
          });
          if (auditFrom)
            p.set("from", new Date(auditFrom + "T00:00:00").toISOString());
          if (auditTo)
            p.set(
              "to",
              new Date(
                new Date(auditTo + "T00:00:00").getTime() + 86400000,
              ).toISOString(),
            );
          setData(await request<Page>("/audit-events?" + p, { signal }));
        }
        if (!signal?.aborted) setLoadedLocation(currentLocation);
      } catch (e) {
        if (!signal?.aborted) fail(e);
      }
    },
    [
      user,
      path,
      isSearch,
      getQuery,
      page,
      params,
      router,
      fail,
      auditActor,
      auditEvent,
      auditFrom,
      auditTo,
      currentLocation,
    ],
  );
  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);
  useEffect(() => {
    if (path !== "/watchlist/sources" && !path.startsWith("/watchlist/runs"))
      return;
    const controller = new AbortController();
    const timer = setInterval(() => void load(controller.signal), 4000);
    return () => {
      clearInterval(timer);
      controller.abort();
    };
  }, [path, load]);
  useEffect(() => {
    setQ(params.get("q") ?? "");
    setMode(params.get("mode") ?? "HYBRID");
    setSource(params.get("source") ?? "");
    setCountry(params.get("country") ?? "");
    setSort(params.get("sort") ?? "score");
    setSize(Number(params.get("size") ?? 20));
  }, [params]);
  async function action(task: () => Promise<unknown>, success: string) {
    setBusy(true);
    setNotice("");
    try {
      await task();
      setNotice(success);
      await load();
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }
  function changePage(next: number) {
    const p = new URLSearchParams(params);
    p.set("page", String(next));
    router.push(path + "?" + p);
  }
  async function login(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    await action(async () => {
      const u = await request<User>("/auth/login", {
        method: "POST",
        body: JSON.stringify({
          username: f.get("username"),
          password: f.get("password"),
        }),
      });
      setUser(u);
      router.replace(u.mustChangePassword ? "/account" : "/watchlist/search");
    }, "로그인했습니다.");
  }
  async function submitSearch(e: FormEvent) {
    e.preventDefault();
    await runSearch(q);
  }
  async function runSearch(term: string) {
    setQ(term);
    setBusy(true);
    try {
      const query: Query = {
        q: term,
        mode,
        source,
        country,
        sort,
        page: 0,
        size,
        saveHistory: true,
      };
      const r = await request<Page & { snapshots: Record<string, number> }>(
        "/watchlist/search",
        { method: "POST", body: JSON.stringify(query) },
      );
      const p = new URLSearchParams({
        q: term,
        mode,
        source,
        country,
        sort,
        size: String(size),
        snapshots: JSON.stringify(r.snapshots),
      });
      router.push("/watchlist/search?" + p);
      setData(r);
      setVersion(r.snapshots);
      setNotice("검색 결과를 불러왔습니다.");
      setRecentSearches(await request<Row[]>("/search-history/recent"));
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }
  function sourceName(code: unknown) {
    return code === "DPL"
      ? "수출권한 거부자 목록 (DPL)"
      : "우려거래자 목록 (Entity List)";
  }
  function restore(row: Row) {
    const query = parsed(row.query_json);
    const p = new URLSearchParams();
    for (const key of ["q", "mode", "source", "country", "sort", "size"])
      if (query[key] != null) p.set(key, String(query[key]));
    p.set("snapshots", JSON.stringify(query.snapshots));
    router.push("/watchlist/search?" + p);
  }
  const menu = [
    ["우려거래자 조회/수집", ""],
    ["우려거래자 검색", "/watchlist/search"],
    ["우려거래자 수집 관리", "/watchlist/sources"],
    ["수집 이력", "/watchlist/runs"],
    ["내 작업", ""],
    ["내 검색 이력", "/search-history"],
    ["내 계정", "/account"],
    ...(user?.owner
      ? [
          ["운영 관리", ""],
          ["사용자 관리", "/users"],
          ["감사 이력", "/audit"],
        ]
      : []),
  ];
  const title = isSearch
    ? "우려거래자 검색"
    : path.startsWith("/watchlist/records/")
      ? "우려거래자 상세"
      : path.startsWith("/watchlist/runs/")
        ? "수집 결과 상세"
        : (menu.find((m) => m[1] === path)?.[0] ??
          "페이지를 찾을 수 없습니다.");
  const menuPath =
    isSearch || path.startsWith("/watchlist/records/")
      ? "/watchlist/search"
      : path.startsWith("/watchlist/runs/")
        ? "/watchlist/runs"
        : path;
  let currentGroup = "";
  let sectionTitle = "";
  for (const [label, href] of menu) {
    if (!href) currentGroup = label;
    else if (href === menuPath) {
      sectionTitle = currentGroup;
      break;
    }
  }
  if (!ready)
    return <main className="loading">업무 공간을 불러오는 중입니다.</main>;
  if (path === "/login")
    return (
      <main className="login-page">
        <div className="login-brand">트레이드옵스 허브</div>
        <form className="login-card" onSubmit={login}>
          <p className="eyebrow">우려거래자 데이터 관리</p>
          <h1>로그인</h1>
          <p>계정으로 접속해 BIS 공개자료를 조회하고 관리하세요.</p>
          <label>
            아이디
            <input
              name="username"
              autoComplete="username"
              required
              maxLength={120}
            />
          </label>
          <label>
            비밀번호
            <input
              name="password"
              type="password"
              autoComplete="current-password"
              required
            />
          </label>
          <button className="primary" disabled={busy}>
            {busy ? "접속 중…" : "로그인"}
          </button>
          <p role="status" className="notice">
            {notice}
          </p>
        </form>
        <small>공개자료 출처: 미국 산업안보국(BIS)</small>
      </main>
    );
  if (!user)
    return <main className="loading">로그인 상태를 확인하는 중입니다.</main>;
  return (
    <div className="app">
      <aside className="sidebar">
        <Link href="/watchlist/search" className="brand">
          트레이드옵스 <strong>허브</strong>
        </Link>
        <nav aria-label="업무 메뉴">
          {menu.map(([label, href]) =>
            href ? (
              <Link
                key={href}
                className={
                  path === href || (href === "/watchlist/search" && isSearch)
                    ? "active"
                    : ""
                }
                href={href}
              >
                {label}
              </Link>
            ) : (
              <p className="nav-group" key={label}>
                {label}
              </p>
            ),
          )}
        </nav>
      </aside>
      <div className="main-wrap">
        <header className="topbar">
          <span className="breadcrumb">
            {sectionTitle && `${sectionTitle} / `}
            {title}
          </span>
          <div className="user-menu">
            <span>{user.username}</span>
            {user.owner && <span className="badge">운영 책임자</span>}
            <button
              onClick={() =>
                void action(async () => {
                  await request("/auth/logout", { method: "POST" });
                  resetCsrf();
                  setUser(null);
                  router.replace("/login");
                }, "로그아웃했습니다.")
              }
            >
              로그아웃
            </button>
          </div>
        </header>
        <main id="main-content" aria-busy={!viewReady}>
          <div className="page-heading">
            <div>
              <h1>{title}</h1>
            </div>
          </div>
          {notice && (
            <div className="notice" role="status">
              {notice}
              <button aria-label="안내 닫기" onClick={() => setNotice("")}>
                ×
              </button>
            </div>
          )}
          {isSearch && (
            <>
              <form className="panel filters" onSubmit={submitSearch}>
                <div className="search-main">
                  <label>
                    기업명·거래자명
                    <input
                      value={q}
                      onChange={(e) => setQ(e.target.value)}
                      placeholder="이름 또는 별칭을 입력하세요"
                      maxLength={250}
                    />
                  </label>
                  <button className="primary" disabled={busy}>
                    검색
                  </button>
                </div>
                {recentSearches.length > 0 && (
                  <div className="recent-searches" aria-label="최근 검색어">
                    <span>최근 검색어</span>
                    {recentSearches.map((item) => (
                      <span
                        className="recent-search-item"
                        key={String(item.id)}
                      >
                        <button
                          type="button"
                          disabled={busy}
                          title={String(item.q)}
                          onClick={() => runSearch(String(item.q))}
                        >
                          {String(item.q)}
                        </button>
                        <button
                          type="button"
                          disabled={busy}
                          aria-label={`${item.q} 검색 이력 삭제`}
                          onClick={() =>
                            action(async () => {
                              await request(
                                "/search-history/recent/" + item.id,
                                { method: "DELETE" },
                              );
                              setRecentSearches(
                                await request<Row[]>("/search-history/recent"),
                              );
                            }, "검색 이력을 삭제했습니다.")
                          }
                        >
                          ×
                        </button>
                      </span>
                    ))}
                  </div>
                )}
                <div className="filter-row">
                  <label>
                    검색 방식
                    <select
                      value={mode}
                      onChange={(e) => setMode(e.target.value)}
                    >
                      <option value="HYBRID">통합 검색</option>
                      <option value="BASIC">기본 검색</option>
                      <option value="SIMILAR">유사 검색</option>
                    </select>
                  </label>
                  <label>
                    출처
                    <select
                      value={source}
                      onChange={(e) => setSource(e.target.value)}
                    >
                      <option value="">전체</option>
                      <option value="DPL">DPL</option>
                      <option value="EL">Entity List</option>
                    </select>
                  </label>
                  <label>
                    국가
                    <select
                      value={country}
                      onChange={(e) => setCountry(e.target.value)}
                    >
                      <option value="">전체 국가</option>
                      {[
                        ...new Set([
                          ...countries.map((r) => String(r.country)),
                          country,
                        ]),
                      ]
                        .filter((code) => /^[A-Z]{2}$/.test(code))
                        .sort()
                        .map((code) => (
                          <option key={code} value={code}>
                            {new Intl.DisplayNames(["ko"], {
                              type: "region",
                            }).of(code)}{" "}
                            ({code})
                          </option>
                        ))}
                    </select>
                  </label>
                  <label>
                    정렬
                    <select
                      value={sort}
                      onChange={(e) => setSort(e.target.value)}
                    >
                      <option value="score">일치도순</option>
                      <option value="name">이름순</option>
                      <option value="country">국가순</option>
                    </select>
                  </label>
                  <label>
                    표시 건수
                    <select
                      value={size}
                      onChange={(e) => setSize(Number(e.target.value))}
                    >
                      {[20, 50, 100].map((n) => (
                        <option key={n} value={n}>
                          {n}건
                        </option>
                      ))}
                    </select>
                  </label>
                  <button
                    type="button"
                    onClick={() => {
                      setQ("");
                      setMode("HYBRID");
                      setSource("");
                      setCountry("");
                      setSort("score");
                      router.push("/watchlist/search");
                    }}
                  >
                    초기화
                  </button>
                </div>
                <p className="help">
                  통합 검색은 기본 일치 결과와 유사 후보를 함께 표시합니다.
                  한글은 등록된 별칭·표기 사전으로 검색합니다.
                </p>
              </form>
              <section className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>
                      검색 결과 <em>{number(data.totalElements)}건</em>
                    </h2>
                  </div>
                  <button
                    disabled={busy || !viewReady || !data.totalElements}
                    onClick={() =>
                      void action(
                        () => exportCsv(getQuery({ snapshots: version })),
                        "CSV 파일을 생성했습니다.",
                      )
                    }
                  >
                    csv 다운로드
                  </button>
                </div>
                <Table
                  caption="우려거래자 검색 결과"
                  headers={[
                    "출처",
                    "이름·다른 이름",
                    "국가",
                    "주소",
                    "검색 일치",
                    "상세",
                  ]}
                  empty={!data.items.length}
                >
                  {data.items.map((r) => (
                    <tr key={String(r.id)}>
                      <td>
                        <span className="badge">{value(r.source_code)}</span>
                      </td>
                      <td>
                        <strong>{value(r.name)}</strong>
                        {Number(r.duplicate_count) > 1 && (
                          <small>동일 원본 {number(r.duplicate_count)}행</small>
                        )}
                      </td>
                      <td>{value(r.country || r.country_label)}</td>
                      <td className="address">{value(r.address)}</td>
                      <td>
                        <span
                          className={
                            "badge " +
                            (Number(r.priority) === 3 ? "held" : "completed")
                          }
                        >
                          {
                            [
                              "이름 일치",
                              "별칭 일치",
                              "부분 일치",
                              "유사 후보",
                            ][Number(r.priority)]
                          }
                        </span>
                        <small>{value(r.matched_name)}</small>
                      </td>
                      <td>
                        <Link
                          href={
                            "/watchlist/records/" +
                            r.id +
                            "?back=" +
                            encodeURIComponent(path + "?" + params)
                          }
                        >
                          상세 보기
                        </Link>
                      </td>
                    </tr>
                  ))}
                </Table>
                <Pager
                  page={page}
                  size={data.size}
                  total={data.totalElements}
                  onPage={changePage}
                />
              </section>
              <p className="help">
                원본에 포함된 정보와 이름 유사도를 제공합니다. 일치 결과가 동일
                기업 확인이나 제재 판단을 뜻하지 않습니다.
              </p>
            </>
          )}
          {path === "/watchlist/sources" && (
            <>
              <div className="panel-heading collection-actions">
                <button
                  className="primary"
                  disabled={busy || sources.some((s) => s.active_run_id)}
                  onClick={() =>
                    void action(
                      () =>
                        request("/watchlist/runs", {
                          method: "POST",
                          body: JSON.stringify({ source: "ALL" }),
                        }),
                      "두 목록의 수집을 시작했습니다.",
                    )
                  }
                >
                  <span className="collection-progress" aria-live="polite">
                    {sources.some((s) => s.active_run_id) && (
                      <span className="collection-spinner" aria-hidden="true" />
                    )}
                    {sources.some((s) => s.active_run_id)
                      ? "수집 중"
                      : "전체 수집"}
                  </span>
                </button>
              </div>
              <div className="source-grid">
                {sources.map((s) => (
                  <section className="panel" key={String(s.code)}>
                    <div className="panel-heading">
                      <h2>{sourceName(s.code)}</h2>
                      <span className="badge">
                        {s.active_run_id ? "수집 중" : "대기"}
                      </span>
                    </div>
                    <div className="metric">
                      {number(s.total_rows)}
                      <small>원본 행</small>
                    </div>
                    <dl className="detail-list">
                      <dt>최근 확인</dt>
                      <dd>{date(s.last_checked)}</dd>
                      <dt>최근 성공</dt>
                      <dd>{date(s.last_success)}</dd>
                      <dt>다음 예약</dt>
                      <dd>{date(s.next_scheduled)}</dd>
                      <dt>최근 데이터 변경일</dt>
                      <dd>{date(s.data_updated_at)}</dd>
                      <dt>다운로드 주소</dt>
                      <dd className="url">{value(s.download_url)}</dd>
                    </dl>
                    {!!s.alert_code && (
                      <p className="warning">{message(s.alert_code)}</p>
                    )}
                    <button
                      disabled={busy || !!s.active_run_id}
                      onClick={() =>
                        void action(
                          () =>
                            request("/watchlist/runs", {
                              method: "POST",
                              body: JSON.stringify({ source: s.code }),
                            }),
                          "수집을 시작했습니다.",
                        )
                      }
                    >
                      <span className="collection-progress" aria-live="polite">
                        {!!s.active_run_id && (
                          <span
                            className="collection-spinner"
                            aria-hidden="true"
                          />
                        )}
                        {s.active_run_id ? "수집 중" : "데이터 수집"}
                      </span>
                    </button>
                  </section>
                ))}
              </div>
              <section className="panel">
                <h2>자동 수집 설정</h2>
                <p>
                  {settings.scheduleEnabled
                    ? "매주 월요일 오전 9시 · 한국 시간"
                    : "예약 실행이 꺼져 있습니다."}
                </p>
                <a
                  href={String(settings.guideUrl ?? "#")}
                  target="_blank"
                  rel="noreferrer"
                >
                  BIS 공식 안내 페이지 열기 ↗
                </a>
              </section>
            </>
          )}
          {path === "/watchlist/runs" && (
            <section className="panel">
              <Table
                caption={title}
                headers={[
                  "번호",
                  "출처",
                  "수집 시작",
                  "실행",
                  "상태",
                  "처리 행",
                  "작업",
                ]}
                empty={!data.items.length}
              >
                {data.items.map((r) => (
                  <tr key={String(r.id)}>
                    <td>{value(r.id)}</td>
                    <td>{value(r.source_code)}</td>
                    <td>{date(r.started_at)}</td>
                    <td>
                      <Badge status={r.trigger_type} />
                    </td>
                    <td>
                      <Badge status={r.status} />
                      {r.idempotent === true && <small>동일 원본</small>}
                    </td>
                    <td>{number(r.total_rows)}건</td>
                    <td>
                      <Link href={"/watchlist/runs/" + r.id}>결과 보기</Link>
                    </td>
                  </tr>
                ))}
              </Table>
              <Pager
                page={page}
                size={20}
                total={data.totalElements}
                onPage={changePage}
              />
            </section>
          )}
          {path.startsWith("/watchlist/runs/") && (
            <>
              <section className="panel">
                <div className="panel-heading">
                  <h2>
                    {value(detail.source_code)} 수집 #{value(detail.id)}
                  </h2>
                  <Badge status={detail.status} />
                </div>
                {!!detail.error_code && (
                  <p className="warning">{message(detail.error_code)}</p>
                )}
                <dl className="detail-list">
                  <dt>현재 단계</dt>
                  <dd>
                    {statuses[String(detail.stage)] ?? value(detail.stage)}
                  </dd>
                  <dt>실행 시각</dt>
                  <dd>{date(detail.started_at)}</dd>
                  <dt>완료 시각</dt>
                  <dd>{date(detail.completed_at)}</dd>
                  <dt>처리 결과</dt>
                  <dd>
                    전체 {number(detail.total_rows)} · 추가{" "}
                    {number(detail.added_rows)} · 제외{" "}
                    {number(detail.removed_rows)} · 유지{" "}
                    {number(detail.unchanged_rows)}
                  </dd>
                  <dt>검증 결과</dt>
                  <dd>
                    경고 {number(detail.warnings)} · 오류{" "}
                    {number(detail.rejected_rows)}
                  </dd>
                  <dt>이전 다운로드 주소</dt>
                  <dd className="url">{value(detail.previous_url)}</dd>
                  <dt>발견한 주소</dt>
                  <dd className="url">{value(detail.download_url)}</dd>
                  <dt>최종 응답 주소</dt>
                  <dd className="url">{value(detail.final_url)}</dd>
                  <dt>원본 변경 응답</dt>
                  <dd>{value(detail.last_modified)}</dd>
                  <dt>추적 ID</dt>
                  <dd>
                    <code>{value(detail.correlation_id)}</code>
                  </dd>
                </dl>
                <div className="actions">
                  {!!detail.file_hash && (
                    <a
                      className="button"
                      href={
                        "/api/v1/watchlist/files/" + detail.id + "/download"
                      }
                    >
                      원본 다운로드
                    </a>
                  )}
                  {!!detail.snapshot_id && (
                    <Link
                      className="button"
                      href={
                        "/watchlist/search?snapshots=" +
                        encodeURIComponent(
                          JSON.stringify({
                            [String(detail.source_code)]: detail.snapshot_id,
                          }),
                        )
                      }
                    >
                      이 버전 조회
                    </Link>
                  )}
                  {detail.status === "HELD" && user.owner && (
                    <button
                      className="primary"
                      disabled={busy}
                      onClick={() => {
                        if (
                          confirm(
                            "행 수 감소와 원본 내용을 확인했습니까? 이 버전을 현재 목록으로 반영합니다.",
                          )
                        )
                          void action(
                            () =>
                              request(
                                "/watchlist/runs/" + detail.id + "/approve",
                                { method: "POST" },
                              ),
                            "보류된 결과를 반영했습니다.",
                          );
                      }}
                    >
                      검토 후 반영
                    </button>
                  )}
                </div>
              </section>
              <section className="panel">
                <h2>갱신 내용</h2>
                <p className="help">
                  안정적인 원천 식별자가 없는 경우 행 내용 기준으로 추가·제외를
                  표시합니다.
                </p>
                <Table
                  caption="갱신 내용"
                  headers={["유형", "이름", "국가", "상세"]}
                  empty={!changes.length}
                >
                  {changes.map((r, i) => (
                    <tr key={i}>
                      <td>
                        <Badge status={r.change_type} />
                      </td>
                      <td>{value(r.name)}</td>
                      <td>{value(r.country)}</td>
                      <td>
                        <Link href={"/watchlist/records/" + r.id}>
                          상세 보기
                        </Link>
                      </td>
                    </tr>
                  ))}
                </Table>
                <div className="pager">
                  <button
                    disabled={page === 0}
                    onClick={() => changePage(page - 1)}
                  >
                    이전
                  </button>
                  <span>{page + 1}페이지</span>
                  <button
                    disabled={changes.length < 20}
                    onClick={() => changePage(page + 1)}
                  >
                    다음
                  </button>
                </div>
              </section>
              <section className="panel">
                <h2>검증 안내</h2>
                <Table
                  caption="원본 행 검증"
                  headers={["원본 행", "구분", "안내"]}
                  empty={!issues.length}
                >
                  {issues.map((r, i) => (
                    <tr key={i}>
                      <td>{value(r.row_number)}</td>
                      <td>{r.severity === "ERROR" ? "오류" : "경고"}</td>
                      <td>{message(r.code)}</td>
                    </tr>
                  ))}
                </Table>
              </section>
            </>
          )}
          {path.startsWith("/watchlist/records/") && (
            <>
              <div className="actions">
                <Link
                  className="button"
                  href={
                    params.get("back")?.startsWith("/watchlist/")
                      ? params.get("back")!
                      : "/watchlist/search"
                  }
                >
                  검색 결과로
                </Link>
                <Link href={"/watchlist/runs/" + detail.run_id}>
                  원본 수집 이력
                </Link>
              </div>
              <section className="panel">
                <h2 className="record-name">{value(detail.name)}</h2>
                <p>
                  {value(detail.source_code)} · 버전 #
                  {value(detail.snapshot_id)} · 원본 {value(detail.row_number)}
                  행
                </p>
                <dl className="raw-fields">
                  {Object.entries(parsed(detail.raw_json))
                    .filter(([, v]) => v !== "" && v != null)
                    .map(([k, v]) => (
                      <div key={k}>
                        <dt>{labels[k] ?? k}</dt>
                        <dd>{value(v)}</dd>
                      </div>
                    ))}
                </dl>
                <p className="help">
                  BIS 원문을 보존해 표시합니다. 검색용 정규화 값은 원본을
                  변경하지 않습니다.
                </p>
              </section>
            </>
          )}
          {path === "/account" && (
            <form
              className="panel account-form"
              onSubmit={(e) => {
                e.preventDefault();
                const form = e.currentTarget;
                const f = new FormData(form);
                if (f.get("next") !== f.get("confirm")) {
                  setNotice("새 비밀번호가 일치하지 않습니다.");
                  return;
                }
                void action(async () => {
                  await request("/account/password", {
                    method: "POST",
                    body: JSON.stringify({
                      currentPassword: f.get("current"),
                      newPassword: f.get("next"),
                    }),
                  });
                  resetCsrf();
                  setUser(null);
                  router.replace("/login");
                }, "비밀번호가 변경됐습니다. 다시 로그인해 주세요.");
              }}
            >
              <h2>비밀번호 변경</h2>
              <p>{user.username}</p>
              {user.mustChangePassword && (
                <p className="warning">
                  임시 비밀번호를 변경한 뒤 업무를 시작할 수 있습니다.
                </p>
              )}
              <label>
                현재 비밀번호
                <input
                  name="current"
                  type="password"
                  autoComplete="current-password"
                  required
                />
              </label>
              <label>
                새 비밀번호
                <input
                  name="next"
                  type="password"
                  minLength={8}
                  autoComplete="new-password"
                  required
                />
              </label>
              <label>
                새 비밀번호 확인
                <input
                  name="confirm"
                  type="password"
                  minLength={8}
                  autoComplete="new-password"
                  required
                />
              </label>
              <p className="help">
                8자 이상, UTF-8 기준 72바이트 이내. 변경하면 모든 기기에서
                로그아웃됩니다.
              </p>
              <button className="primary" disabled={busy}>
                비밀번호 변경
              </button>
            </form>
          )}
          {path === "/users" && user.owner && (
            <>
              <form
                className="panel filters"
                onSubmit={(e) => {
                  e.preventDefault();
                  const f = new FormData(e.currentTarget);
                  void action(
                    () =>
                      request("/users", {
                        method: "POST",
                        body: JSON.stringify({
                          username: f.get("username"),
                          password: f.get("password"),
                        }),
                      }),
                    "계정을 생성했습니다. 처음 로그인하면 비밀번호를 변경해야 합니다.",
                  );
                }}
              >
                <h2>사용자 추가</h2>
                <div className="filter-row">
                  <label>
                    아이디
                    <input
                      name="username"
                      required
                      pattern={"[A-Za-z0-9@._+\\-]{3,120}"}
                      autoComplete="off"
                    />
                  </label>
                  <label>
                    임시 비밀번호
                    <input
                      name="password"
                      type="password"
                      required
                      minLength={8}
                      autoComplete="new-password"
                    />
                  </label>
                  <button className="primary" disabled={busy}>
                    계정 생성
                  </button>
                </div>
              </form>
              <section className="panel">
                <Table
                  caption="사용자 계정"
                  headers={["아이디", "상태", "비밀번호", "생성 시각", "관리"]}
                  empty={!data.items.length}
                >
                  {data.items.map((r) => (
                    <tr key={String(r.id)}>
                      <td>
                        {value(r.username)}
                        {r.username === user.username && (
                          <small>운영 책임자</small>
                        )}
                      </td>
                      <td>{r.enabled ? "활성" : "비활성"}</td>
                      <td>{r.must_change_password ? "변경 필요" : "설정됨"}</td>
                      <td>{date(r.created_at)}</td>
                      <td>
                        {r.username !== user.username && (
                          <div className="actions">
                            <button
                              disabled={busy}
                              onClick={() => {
                                if (
                                  confirm(
                                    "계정 상태를 변경하시겠습니까? 비활성화하면 모든 세션이 종료됩니다.",
                                  )
                                )
                                  void action(
                                    () =>
                                      request("/users/" + r.id, {
                                        method: "PATCH",
                                        body: JSON.stringify({
                                          enabled: !r.enabled,
                                        }),
                                      }),
                                    "계정 상태를 변경했습니다.",
                                  );
                              }}
                            >
                              {r.enabled ? "비활성화" : "활성화"}
                            </button>
                            <button
                              onClick={() =>
                                setDetail({
                                  resetId: r.id,
                                  resetName: r.username,
                                })
                              }
                            >
                              비밀번호 초기화
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </Table>
              </section>
              {!!detail.resetId && (
                <form
                  className="panel account-form"
                  onSubmit={(e) => {
                    e.preventDefault();
                    const f = new FormData(e.currentTarget);
                    void action(async () => {
                      await request(
                        "/users/" + detail.resetId + "/reset-password",
                        {
                          method: "POST",
                          body: JSON.stringify({ password: f.get("password") }),
                        },
                      );
                      setDetail({});
                    }, "비밀번호를 초기화하고 기존 세션을 종료했습니다.");
                  }}
                >
                  <h2>{String(detail.resetName)} 비밀번호 초기화</h2>
                  <label>
                    새 임시 비밀번호
                    <input
                      name="password"
                      type="password"
                      autoComplete="new-password"
                      required
                      minLength={8}
                    />
                  </label>
                  <div className="actions">
                    <button className="primary" disabled={busy}>
                      초기화
                    </button>
                    <button type="button" onClick={() => setDetail({})}>
                      취소
                    </button>
                  </div>
                </form>
              )}
            </>
          )}
          {path === "/search-history" && (
            <section className="panel">
              <div className="panel-heading">
                <p>
                  본인의 검색 내용만 표시합니다. 직접 삭제할 때까지 보관됩니다.
                </p>
                <button
                  disabled={busy || !data.items.length}
                  onClick={() => {
                    if (confirm("본인의 검색 이력을 모두 삭제하시겠습니까?"))
                      void action(
                        () => request("/search-history", { method: "DELETE" }),
                        "검색 이력을 삭제했습니다.",
                      );
                  }}
                >
                  전체 삭제
                </button>
              </div>
              <Table
                caption="내 검색 이력"
                headers={["검색 시각", "검색어", "조건", "결과", "작업"]}
                empty={!data.items.length}
              >
                {data.items.map((r) => {
                  const q = parsed(r.query_json);
                  return (
                    <tr key={String(r.id)}>
                      <td>{date(r.created_at)}</td>
                      <td>{q.q ? String(q.q) : "전체 조회"}</td>
                      <td>
                        {q.mode === "BASIC"
                          ? "기본"
                          : q.mode === "SIMILAR"
                            ? "유사"
                            : "통합"}{" "}
                        · {q.source ? String(q.source) : "전체 출처"} ·{" "}
                        {q.country ? String(q.country) : "전체 국가"}
                      </td>
                      <td>{number(r.total_results)}건</td>
                      <td>
                        <div className="actions">
                          <button onClick={() => restore(r)}>다시 조회</button>
                          <button
                            disabled={busy}
                            onClick={() =>
                              void action(
                                () =>
                                  request("/search-history/" + r.id, {
                                    method: "DELETE",
                                  }),
                                "검색 이력을 삭제했습니다.",
                              )
                            }
                          >
                            삭제
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </Table>
              <Pager
                page={page}
                size={20}
                total={data.totalElements}
                onPage={changePage}
              />
            </section>
          )}
          {path === "/audit" && user.owner && (
            <>
              <form
                className="panel filters"
                onSubmit={(e) => {
                  e.preventDefault();
                  void load();
                }}
              >
                <div className="filter-row">
                  <label>
                    사용자
                    <input
                      value={auditActor}
                      onChange={(e) => setAuditActor(e.target.value)}
                      placeholder="아이디"
                    />
                  </label>
                  <label>
                    작업
                    <select
                      value={auditEvent}
                      onChange={(e) => setAuditEvent(e.target.value)}
                    >
                      <option value="">전체 작업</option>
                      {Object.entries(events).map(([k, v]) => (
                        <option key={k} value={k}>
                          {v}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    시작일
                    <input
                      type="date"
                      value={auditFrom}
                      onChange={(e) => setAuditFrom(e.target.value)}
                    />
                  </label>
                  <label>
                    종료일
                    <input
                      type="date"
                      value={auditTo}
                      onChange={(e) => setAuditTo(e.target.value)}
                    />
                  </label>
                  <button>조회</button>
                </div>
              </form>
              <section className="panel">
                <Table
                  caption="감사 이력"
                  headers={["시각", "사용자", "작업", "대상", "결과·추적"]}
                  empty={!data.items.length}
                >
                  {data.items.map((r) => (
                    <tr key={String(r.id)}>
                      <td>{date(r.occurred_at)}</td>
                      <td>
                        {r.actor_username ? String(r.actor_username) : "시스템"}
                      </td>
                      <td>{events[String(r.event_type)] ?? "작업 기록"}</td>
                      <td>{value(r.entity_id)}</td>
                      <td>
                        <details>
                          <summary>상세 보기</summary>
                          <p>
                            {parsed(r.detail).success === false
                              ? "실패"
                              : "기록됨"}
                          </p>
                          <code>{value(r.correlation_id)}</code>
                          <pre>{JSON.stringify(parsed(r.detail), null, 2)}</pre>
                        </details>
                      </td>
                    </tr>
                  ))}
                </Table>
                <Pager
                  page={page}
                  size={20}
                  total={data.totalElements}
                  onPage={changePage}
                />
              </section>
            </>
          )}
        </main>
      </div>
    </div>
  );
}
