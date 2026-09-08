import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { randomUUID } from "node:crypto";
import { parseArgs, isDeepStrictEqual } from "node:util";
import { root, artifactPath, definitions, sourceHash, scenarioHash, validateEvidence } from "./verification-common.mjs";

const { values } = parseArgs({ options: {
    api: { type: "string", default: "http://127.0.0.1:18081/api/v1" },
    web: { type: "string", default: "http://127.0.0.1:13000" },
    evidence: { type: "string", default: "artifacts/runtime/latest.json" },
    "verification-id": { type: "string", default: randomUUID() }
} });
function localUrl(value) {
    const url = new URL(value);
    if (!["127.0.0.1", "localhost", "[::1]"].includes(url.hostname) || url.protocol !== "http:" ||
        url.username || url.password || url.search || url.hash) throw new Error("LOCAL_RUNTIME_REQUIRED");
    return url.toString().replace(/\/$/, "");
}

let report, output, current;
const state = {};
function check(name, actual, expected = true) {
    const passed = isDeepStrictEqual(actual, expected);
    current.checks.push({ name, passed });
    if (!passed) throw new Error("SCENARIO_ASSERTION_FAILED");
}
async function request(path, { token, method = "GET", body, status = 200, web = false, correlationId } = {}) {
    const headers = {};
    if (token) headers.Authorization = "Bearer " + token;
    if (correlationId) headers["X-Correlation-Id"] = correlationId;
    if (body && !(body instanceof FormData)) { headers["Content-Type"] = "application/json"; body = JSON.stringify(body); }
    const response = await fetch((web ? state.web : state.api) + path, {
        method, body, headers, redirect: "error", signal: AbortSignal.timeout(15000)
    });
    const cid = response.headers.get("X-Correlation-Id");
    if (current) current.requests.push({ method, path: path.split("?")[0], status: response.status,
        correlationId: web ? null : (/^[A-Za-z0-9-]{8,64}$/.test(cid ?? "") ? cid : null) });
    if (response.status !== status) throw new Error("UNEXPECTED_HTTP_STATUS");
    if (!web && (!/^[A-Za-z0-9-]{8,64}$/.test(cid ?? "") || (correlationId && cid !== correlationId))) {
        throw new Error("CORRELATION_ID_INVALID");
    }
    return web ? response.text() : response.json();
}
async function login(role) {
    const data = await request("/auth/login", { method: "POST", body: {
        username: role + "@tradeops.test", password: process.env.VERIFY_LOCAL_PASSWORD ?? "portfolio-demo"
    } });
    if (!data.accessToken || data.user.role !== role.toUpperCase()) throw new Error("LOGIN_INVALID");
    return data.accessToken;
}
const run = (version, format, token = state.operator) => request("/watchlist/runs",
    { method: "POST", token, body: { fixtureVersion: version, format }, correlationId: randomUUID() });
const list = path => request(path, { token: state.operator });
const counts = r => [r.totalRows, r.addedCount, r.changedCount, r.removedCount, r.unchangedCount];

const handlers = {
    "health-and-auth-safe-errors": async () => {
        const h = await request("/health");
        check("health", h.status, "ok");
        check("health-fields", Object.keys(h).sort(), ["checkedAt", "correlationId", "service", "status"]);
        const anon = await request("/watchlist/entities", { status: 401 });
        check("anonymous-denied", anon.code, "AUTHENTICATION_REQUIRED");
        const bad = await request("/auth/login", { method: "POST", body: {
            username: "missing@tradeops.test", password: "invalid-local-password"
        }, status: 401 });
        check("login-denied", bad.code, "AUTHENTICATION_FAILED");
        check("safe-error-fields", [anon, bad].every(x => Object.keys(x).every(
            key => ["code", "message", "correlationId", "timestamp"].includes(key))));
    },
    "operator-creates-first-watchlist-snapshot": async () => {
        state.a = await run("2026-01-A", "XML");
        check("completed", state.a.status === "COMPLETED" && !state.a.idempotent);
        check("counts", counts(state.a), [4, 4, 0, 0, 0]);
        const entities = await list("/watchlist/entities");
        check("current-version", entities.items.every(x => x.sourceVersion === "2026-01-A"));
        check("four-entities", entities.totalElements, 4);
    },
    "operator-reviews-watchlist-version-diff": async () => {
        state.b = await run("2026-02-B", "CSV");
        check("completed", state.b.status === "COMPLETED" && !state.b.idempotent);
        check("counts", counts(state.b), [4, 1, 1, 1, 2]);
        state.changes = await list("/watchlist/changes");
        check("change-types", state.changes.items.map(x => x.type).sort(), ["ADDED", "CHANGED", "REMOVED"]);
        const changed = state.changes.items.find(x => x.type === "CHANGED");
        check("changed-fields", Object.keys(JSON.parse(changed.differencesJson)).sort(), ["aliases", "countryCode", "listingReason"]);
        const entities = await list("/watchlist/entities");
        check("current-version", entities.totalElements === 4 && entities.items.every(x => x.sourceVersion === "2026-02-B"));
    },
    "same-payload-is-idempotent": async () => {
        const rerun = await run("2026-02-B", "CSV");
        check("same-snapshot", rerun.snapshotId, state.b.snapshotId);
        check("idempotent", rerun.idempotent);
        check("changes-unchanged", await list("/watchlist/changes"), state.changes);
        const runs = await list("/watchlist/runs");
        check("prior-counts-immutable", [state.a, state.b].every(before =>
            isDeepStrictEqual(counts(runs.items.find(r => r.runId === before.runId)), counts(before))));
    },
    "failed-source-run-preserves-current-view": async () => {
        const before = await list("/watchlist/entities");
        const failed = await run("2099-01-Z", "XML");
        check("failed", failed.status === "FAILED" && failed.snapshotId === null);
        check("safe-code", failed.safeErrorCode, "FIXTURE_NOT_FOUND");
        check("entities-unchanged", await list("/watchlist/entities"), before);
        check("changes-unchanged", await list("/watchlist/changes"), state.changes);
        const runs = await list("/watchlist/runs");
        check("failure-reviewable", runs.items.some(x => x.runId === failed.runId && x.safeErrorCode === "FIXTURE_NOT_FOUND"));
    },
    "viewer-cannot-trigger-watchlist-run": async () => {
        const before = (await list("/watchlist/runs")).totalElements;
        const denied = await request("/watchlist/runs", { method: "POST", token: state.viewer,
            body: { fixtureVersion: "2026-01-A", format: "XML" }, status: 403 });
        check("write-denied", denied.code, "ACCESS_DENIED");
        check("read-allowed", (await request("/watchlist/entities", { token: state.viewer })).totalElements, 4);
        check("run-count-unchanged", (await list("/watchlist/runs")).totalElements, before);
    },
    "watchlist-search-filters": async () => {
        const search = await list("/watchlist/entities?search=%20ember%20");
        check("normalized-search", search.items.map(x => x.externalId), ["FCP-1001"]);
        check("country", (await list("/watchlist/entities?country=my")).items.map(x => x.externalId), ["FCP-1002"]);
        check("provider", (await list("/watchlist/entities?provider=UNKNOWN")).totalElements, 0);
        check("status", (await list("/watchlist/entities?status=inactive")).totalElements, 0);
        const first = await list("/watchlist/entities?page=0&size=2"), second = await list("/watchlist/entities?page=1&size=2");
        check("pagination", first.totalPages === 2 && first.items.length === 2 && second.items.length === 2 &&
            new Set([...first.items, ...second.items].map(x => x.externalId)).size === 4);
        check("change-filter", (await list("/watchlist/changes?type=changed")).items.map(x => x.externalId), ["FCP-1002"]);
    },
    "transaction-import-and-monthly-summary": async () => {
        const fixture = readFileSync(resolve(root, "data/fixtures/fictional-transactions.csv"), "utf8").trim();
        const mixed = fixture + "\n" + fixture.split(/\r?\n/)[1] +
            "\nTX-INVALID-001,2026-01-20,FICTIONAL INVALID ROW,KR,-1.00,USD,FICTIONAL\n";
        const upload = (token, status = 200) => {
            const form = new FormData();
            form.set("file", new Blob([mixed], { type: "text/csv" }), "fictional-runtime.csv");
            return request("/imports", { method: "POST", token, body: form, status });
        };
        const imported = await upload(state.operator);
        check("import-counts", [imported.status, imported.acceptedCount, imported.rejectedCount, imported.duplicateCount,
            Number.isInteger(imported.runId)], ["COMPLETED_WITH_ERRORS", 3, 1, 1, true]);
        const duplicate = await upload(state.operator);
        check("duplicate-file", duplicate.safeErrorCode === "DUPLICATE_FILE" && duplicate.runId === null);
        check("all-transactions", (await list("/transactions")).length, 3);
        check("filtered-search", (await list("/transactions?search=ember&country=kr")).map(x => x.transactionId), ["TX-2026-001"]);
        check("monthly-totals", await list("/transactions/monthly-summary"), [
            { month: "2026-01", transactionCount: 2, totalAmountUsd: 20900.50 },
            { month: "2026-02", transactionCount: 1, totalAmountUsd: 3190.00 }
        ]);
        check("viewer-denied", (await upload(state.viewer, 403)).code, "ACCESS_DENIED");
    },
    "screening-review-authorization": async () => {
        const body = { transactionId: "TX-2026-001", watchlistExternalId: "FCP-1001", matchScore: 0.75, disposition: "NEEDS_FOLLOW_UP" };
        const review = (token, status, change = {}) => request("/screening-reviews", {
            token, method: "POST", status, body: { ...body, ...change }, correlationId: randomUUID()
        });
        check("operator-allowed", (await review(state.operator, 200)).status, "RECORDED");
        check("admin-allowed", (await review(state.admin, 200)).status, "RECORDED");
        check("viewer-denied", (await review(state.viewer, 403)).code, "ACCESS_DENIED");
        check("anonymous-denied", (await review(undefined, 401)).code, "AUTHENTICATION_REQUIRED");
        check("null-disposition", (await review(state.operator, 400, { disposition: null })).code, "INVALID_REQUEST");
        check("invalid-score", (await review(state.operator, 400, { matchScore: 1.5 })).code, "INVALID_REQUEST");
    },
    "invalid-request-contract": async () => {
        const invalid = body => request("/watchlist/runs", { method: "POST", token: state.operator, body, status: 400 });
        check("unknown-field", (await invalid({ fixtureVersion: "2026-01-A", format: "XML", unexpected: true })).code, "INVALID_REQUEST");
        check("invalid-format", (await invalid({ fixtureVersion: "2026-01-A", format: "JSON" })).code, "INVALID_REQUEST");
        check("invalid-page", (await request("/watchlist/entities?page=-1", { token: state.operator, status: 400 })).code, "INVALID_REQUEST");
    },
    "console-product-copy": async () => {
        const html = await request("/", { web: true });
        check("web-ready", html.includes("트레이드옵스"));
        check("workspace-access", html.includes("업무 공간 접속") && html.includes("운영자로 로그인"));
        check("fictional-data", html.includes("가상 데이터 사용"));
        check("korean-language", html.includes('lang="ko"') && html.includes("검토 대상 목록 운영") && !/Watchlist updates|Operator sign in|Sign in as operator/.test(html));
        check("product-copy", !/Portfolio demonstration|DEMO ACCESS|for portfolio use/.test(html));
    }
};

try {
    output = artifactPath(values.evidence);
    report = { schemaVersion: 1, verificationId: values["verification-id"], generatedAt: new Date().toISOString(),
        sourceHash: sourceHash(), scenarioHash: scenarioHash(), status: "FAILED",
        scenarios: definitions().map(x => ({ id: x.id, status: "SKIPPED", checks: [], requests: [] })) };
    state.api = localUrl(values.api); state.web = localUrl(values.web);
    if (definitions().some(x => !handlers[x.id])) throw new Error("SCENARIO_HANDLER_MISSING");
    state.operator = await login("operator"); state.viewer = await login("viewer"); state.admin = await login("admin");
    // Refuse existing data before the first mutation. The final gate creates a fresh isolated DB.
    if ((await list("/watchlist/runs")).totalElements !== 0 || (await list("/transactions")).length !== 0) {
        throw new Error("FRESH_RUNTIME_REQUIRED");
    }
    for (const scenario of report.scenarios) {
        current = scenario;
        scenario.status = "FAILED";
        await handlers[scenario.id]();
        scenario.status = "PASSED";
        console.log("PASS " + scenario.id);
    }
    report.status = "PASSED";
    validateEvidence(report, { verificationId: values["verification-id"] });
} catch (error) {
    if (report) report.status = "FAILED";
    const safe = /^(EVIDENCE_[A-Z_]+|LOCAL_RUNTIME_REQUIRED|FRESH_RUNTIME_REQUIRED|SCENARIO_[A-Z_]+|UNEXPECTED_HTTP_STATUS|CORRELATION_ID_INVALID|LOGIN_INVALID)$/.test(error.message)
        ? error.message : "RUNTIME_REQUEST_FAILED";
    console.error((current ? current.id + ": " : "") + safe);
    process.exitCode = 1;
} finally {
    if (report && output) writeFileSync(output, JSON.stringify(report, null, 2) + "\n");
}
