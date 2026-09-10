let csrf: string | null = null;
const messages: Record<string, string> = {
  LAW_REINDEX_DISABLED: "법령 서버에서 재색인이 비활성화되어 있습니다.",
  LAW_ADMIN_BUSY:
    "다른 법령 관리 작업이 진행 중입니다. 완료 후 다시 시도해 주세요.",
  LAW_UNAVAILABLE:
    "법령 검색 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
  LAW_TIMEOUT: "법령 검색 응답 대기 시간이 지났습니다. 다시 검색해 주세요.",
  LAW_INVALID_RESPONSE:
    "법령 검색 응답을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.",
  LAW_REQUEST_REJECTED:
    "법령 검색 서비스가 요청을 처리하지 못했습니다. 입력 내용을 확인해 주세요.",
  LAW_ARTICLE_NOT_FOUND:
    "해당 조문을 찾을 수 없습니다. 법령을 다시 검색해 주세요.",
  AUTHENTICATION_FAILED: "아이디 또는 비밀번호를 확인해 주세요.",
  AUTHENTICATION_REQUIRED: "로그인이 필요합니다.",
  CSRF_INVALID: "접속 시간이 만료됐습니다. 다시 로그인해 주세요.",
  ACCESS_DENIED: "이 작업을 수행할 수 없습니다.",
  PASSWORD_CHANGE_REQUIRED: "먼저 임시 비밀번호를 변경해 주세요.",
  LOGIN_RATE_LIMITED: "로그인 시도가 많습니다. 10분 후 다시 시도해 주세요.",
  INVALID_PASSWORD:
    "비밀번호는 8자 이상, UTF-8 기준 72바이트 이내로 입력해 주세요.",
  PASSWORD_UNCHANGED: "기존과 다른 비밀번호를 입력해 주세요.",
  OWNER_PROTECTED: "운영 책임자 계정은 이 작업으로 변경할 수 없습니다.",
  USERNAME_EXISTS: "이미 사용 중인 아이디입니다.",
  COLLECTION_ALREADY_RUNNING: "이미 수집 중인 출처입니다.",
  ROW_COUNT_DROP: "이전보다 행 수가 30% 넘게 줄어 반영을 보류했습니다.",
  DOWNLOAD_LINK_MISSING:
    "다운로드 링크를 찾지 못했습니다. 안내 페이지를 확인해 주세요.",
  DOWNLOAD_LINK_AMBIGUOUS: "다운로드 후보가 여러 개여서 확인이 필요합니다.",
  SOURCE_URL_CHANGED: "검증된 새 다운로드 주소를 반영했습니다.",
  SOURCE_URL_NOT_ALLOWED: "허용되지 않은 다운로드 주소입니다.",
  SCHEMA_CHANGED: "원본 파일의 열 구성이 변경됐습니다.",
  ROW_VALIDATION_FAILED: "원본 행 검증에 실패했습니다.",
  NAME_REQUIRED: "이름이 비어 있어 이 파일을 반영하지 않았습니다.",
  COLUMN_COUNT_INVALID: "열 개수가 머리글과 일치하지 않습니다.",
  ENCODING_INVALID: "원본 파일의 문자 인코딩을 확인해 주세요.",
  SOURCE_TOO_LARGE: "원본 파일이 수집 허용 크기인 32MB를 초과했습니다.",
  REDIRECT_LIMIT: "다운로드 주소가 너무 여러 번 바뀌었습니다.",
  SNAPSHOT_NOT_FOUND: "조회하려는 원본 버전을 찾을 수 없습니다.",
  COLLECTION_INTERRUPTED: "수집이 중단됐습니다. 다시 실행해 주세요.",
  SOURCE_INVALID: "수집할 출처를 선택해 주세요.",
  RUN_NOT_HELD: "현재 반영 보류 상태인 실행만 승인할 수 있습니다.",
  CSV_INVALID: "CSV 구조를 읽을 수 없습니다.",
  EMPTY_SOURCE: "원본에 데이터가 없습니다.",
  SOURCE_HTTP_403: "BIS에서 접근을 거절했습니다.",
  SOURCE_CONNECTION_FAILED: "BIS에 연결하지 못했습니다.",
  SOURCE_RETRY_LATER: "BIS가 재시도 대기를 요청했습니다.",
  SOURCE_TEMPORARILY_UNAVAILABLE: "BIS에서 일시적으로 응답하지 않습니다.",
  COLLECTION_STORAGE_FAILED: "수집 결과를 저장하지 못했습니다.",
  PROCESS_INTERRUPTED: "서버 중단으로 수집이 종료됐습니다.",
  NOT_FOUND: "요청한 항목이 없습니다.",
  FILE_NOT_AVAILABLE: "원본 파일을 사용할 수 없습니다.",
  RUN_SUPERSEDED: "더 최신 버전이 반영돼 이 결과를 적용할 수 없습니다.",
  COUNTRY_UNRESOLVED: "국가 코드를 확인할 수 없습니다.",
  DATE_UNRESOLVED: "날짜 원문을 확인해 주세요.",
  PERSISTENCE_UNAVAILABLE:
    "저장소에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
  INVALID_REQUEST: "입력 내용을 확인해 주세요.",
};
export function message(code: unknown) {
  return (
    messages[String(code)] ??
    "작업을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
  );
}
export class ApiError extends Error {
  constructor(
    public code: string,
    public status: number,
  ) {
    super(message(code));
  }
}
export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const method = options.method ?? "GET";
  const headers = new Headers(options.headers);
  if (!["GET", "HEAD"].includes(method)) {
    if (!csrf) {
      const r = await fetch("/api/v1/auth/csrf", { cache: "no-store" });
      if (!r.ok) throw new Error("서버에 연결할 수 없습니다.");
      csrf = (await r.json()).token;
    }
    headers.set("X-CSRF-TOKEN", csrf!);
    headers.set("Content-Type", "application/json");
  }
  let response: Response;
  try {
    response = await fetch("/api/v1" + path, {
      ...options,
      headers,
      cache: "no-store",
    });
  } catch {
    throw new Error("서버에 연결할 수 없습니다. 연결 상태를 확인해 주세요.");
  }
  if (!response.ok) {
    const body = await response
      .json()
      .catch(() => ({ code: "INVALID_RESPONSE" }));
    if (response.status === 401 || body.code === "CSRF_INVALID") csrf = null;
    throw new ApiError(body.code, response.status);
  }
  if (response.status === 204 || response.headers.get("content-length") === "0")
    return undefined as T;
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}
export async function exportCsv(query: unknown) {
  const token = await request<{ token: string }>("/auth/csrf");
  const r = await fetch("/api/v1/watchlist/exports", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-TOKEN": token.token,
    },
    body: JSON.stringify(query),
  });
  if (!r.ok) {
    const b = await r.json();
    throw new ApiError(b.code, r.status);
  }
  const url = URL.createObjectURL(await r.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = "BIS-검색결과.csv";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function resetCsrf() {
  csrf = null;
}
