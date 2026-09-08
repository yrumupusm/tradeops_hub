import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { readFileSync, mkdtempSync, mkdirSync } from "node:fs";
import { resolve } from "node:path";
import { artifactPath, root, validateEvidence } from "./verification-common.mjs";

const options = {
    verificationId: "test-run-001", now: Date.parse("2026-09-08T12:00:00Z"),
    expectedSourceHash: "source-hash", expectedScenarioHash: "scenario-hash",
    scenarios: [{ id: "sample", checks: ["expected-count"] }]
};
function valid() {
    return {
        schemaVersion: 1, verificationId: options.verificationId, generatedAt: "2026-09-08T12:00:00Z",
        sourceHash: "source-hash", scenarioHash: "scenario-hash", status: "PASSED",
        scenarios: [{ id: "sample", status: "PASSED", checks: [{ name: "expected-count", passed: true }],
            requests: [{ method: "GET", path: "/health", status: 200, correlationId: "test-request-001" }] }]
    };
}
test("accepts complete matching evidence", () => assert.equal(validateEvidence(valid(), options), true));
test("rejects stale, future, wrong-run and changed-source evidence", () => {
    for (const change of [
        { generatedAt: "2026-09-07T12:00:00Z" }, { generatedAt: "2026-09-09T12:00:00Z" },
        { verificationId: "another-run" }, { sourceHash: "old-source" }, { scenarioHash: "old-scenarios" }
    ]) assert.throws(() => validateEvidence({ ...valid(), ...change }, options), /EVIDENCE_/);
});
test("rejects skipped, missing and duplicate scenarios", () => {
    const skipped = valid(); skipped.scenarios[0].status = "SKIPPED";
    const missing = valid(); missing.scenarios = [];
    const duplicate = valid(); duplicate.scenarios.push(duplicate.scenarios[0]);
    for (const report of [skipped, missing, duplicate]) assert.throws(() => validateEvidence(report, options), /EVIDENCE_/);
});
test("rejects failed, absent and renamed assertions", () => {
    const failed = valid(); failed.scenarios[0].checks[0].passed = false;
    const missing = valid(); missing.scenarios[0].checks = [];
    const renamed = valid(); renamed.scenarios[0].checks[0].name = "another-check";
    for (const report of [failed, missing, renamed]) assert.throws(() => validateEvidence(report, options), /EVIDENCE_/);
});
test("rejects response dumps, headers and unsafe request metadata", () => {
    const raw = valid(); raw.scenarios[0].response = { accessToken: "private" };
    const headers = valid(); headers.scenarios[0].requests[0].headers = { Authorization: "private" };
    const url = valid(); url.scenarios[0].requests[0].path = "/health?secret=private";
    const trace = valid(); trace.scenarios[0].requests[0].correlationId = "invalid/request";
    for (const report of [raw, headers, url, trace]) assert.throws(() => validateEvidence(report, options), /EVIDENCE_/);
});
test("rejects evidence paths outside ignored artifacts", () => {
    assert.throws(() => artifactPath("../outside.json"), /EVIDENCE_PATH_INVALID/);
    assert.throws(() => artifactPath("README.md"), /EVIDENCE_PATH_INVALID/);
});

function runChild(args) {
    return new Promise((resolveResult, reject) => {
        const child = spawn(process.execPath, ["scripts/runtime-scenarios.mjs", ...args], { cwd: root });
        let output = "";
        child.stdout.on("data", chunk => { output += chunk; });
        child.stderr.on("data", chunk => { output += chunk; });
        child.on("error", reject);
        child.on("exit", code => resolveResult({ code, output }));
    });
}
test("runtime failure exits nonzero and writes safe failed evidence", async () => {
    const server = createServer(async (request, response) => {
        const chunks = [];
        for await (const chunk of request) chunks.push(chunk);
        response.setHeader("Content-Type", "application/json");
        response.setHeader("X-Correlation-Id", "stub-request-001");
        if (request.url === "/api/v1/auth/login") {
            const body = JSON.parse(Buffer.concat(chunks));
            response.end(JSON.stringify({ accessToken: "SECRET_STUB_TOKEN", user: { role: body.username.split("@")[0].toUpperCase() } }));
        } else if (request.url === "/api/v1/watchlist/runs") response.end('{"totalElements":0}');
        else if (request.url === "/api/v1/transactions") response.end("[]");
        else response.end('{"status":"ok","password":"SECRET_RESPONSE_VALUE"}');
    });
    await new Promise(resolveReady => server.listen(0, "127.0.0.1", resolveReady));
    mkdirSync(resolve(root, "artifacts/verification-tests"), { recursive: true });
    const directory = mkdtempSync(resolve(root, "artifacts/verification-tests/failure-"));
    const path = resolve(directory, "runtime.json");
    try {
        const result = await runChild(["--api", "http://127.0.0.1:" + server.address().port + "/api/v1", "--evidence", path]);
        assert.notEqual(result.code, 0);
        const text = readFileSync(path, "utf8");
        const report = JSON.parse(text);
        assert.equal(report.status, "FAILED");
        assert.equal(report.scenarios[0].status, "FAILED");
        assert.ok(report.scenarios.slice(1).every(s => s.status === "SKIPPED"));
        assert.doesNotMatch(text + result.output, /SECRET_STUB_TOKEN|SECRET_RESPONSE_VALUE|accessToken|password/);
    } finally { await new Promise(resolveClosed => server.close(resolveClosed)); }
});
test("runtime refuses existing data before any write", async () => {
    let mutations = 0;
    const server = createServer(async (request, response) => {
        const chunks = [];
        for await (const chunk of request) chunks.push(chunk);
        response.setHeader("Content-Type", "application/json");
        response.setHeader("X-Correlation-Id", "stub-request-001");
        if (request.url === "/api/v1/auth/login") {
            const body = JSON.parse(Buffer.concat(chunks));
            response.end(JSON.stringify({ accessToken: "local-stub", user: { role: body.username.split("@")[0].toUpperCase() } }));
        } else {
            if (request.method === "POST") mutations++;
            response.end('{"totalElements":1}');
        }
    });
    await new Promise(resolveReady => server.listen(0, "127.0.0.1", resolveReady));
    const directory = mkdtempSync(resolve(root, "artifacts/verification-tests/nonempty-"));
    try {
        const result = await runChild(["--api", "http://127.0.0.1:" + server.address().port + "/api/v1",
            "--evidence", resolve(directory, "runtime.json")]);
        assert.notEqual(result.code, 0);
        assert.match(result.output, /FRESH_RUNTIME_REQUIRED/);
        assert.equal(mutations, 0);
    } finally { await new Promise(resolveClosed => server.close(resolveClosed)); }
});
