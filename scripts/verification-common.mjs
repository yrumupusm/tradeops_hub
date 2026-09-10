import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { readFileSync, existsSync, mkdirSync, realpathSync } from "node:fs";
import { dirname, resolve, relative, isAbsolute } from "node:path";
import { fileURLToPath } from "node:url";

export const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
export const sha256 = data => createHash("sha256").update(data).digest("hex");
export const definitions = () => JSON.parse(readFileSync(resolve(root, "harness/scenarios.json"), "utf8"));
export const scenarioHash = () => sha256(readFileSync(resolve(root, "harness/scenarios.json")));
export function sourceHash() {
    const paths = [...new Set(execFileSync("git", ["ls-files", "--cached", "--others", "--exclude-standard"],
        { cwd: root, encoding: "utf8" }).trim().split(/\r?\n/))]
        .filter(path => /^(backend\/|frontend\/|scripts\/|harness\/|data\/)/.test(path))
        .filter(path => !/^(backend\/(target|build)\/|frontend\/(node_modules|\.next)\/)/.test(path))
        .filter(path => !path.endsWith(".tsbuildinfo")).sort();
    const hash = createHash("sha256");
    for (const path of paths) {
        hash.update(path + "\0");
        hash.update(existsSync(resolve(root, path)) ? readFileSync(resolve(root, path)) : "<deleted>");
        hash.update("\0");
    }
    return hash.digest("hex");
}
export function artifactPath(input) {
    const directory = resolve(root, "artifacts");
    mkdirSync(directory, { recursive: true });
    const target = resolve(root, input);
    const rel = relative(directory, target);
    if (!rel || rel.startsWith("..") || isAbsolute(rel)) throw new Error("EVIDENCE_PATH_INVALID");
    mkdirSync(dirname(target), { recursive: true });
    const actual = relative(realpathSync(directory), realpathSync(dirname(target)));
    if (actual.startsWith("..") || isAbsolute(actual)) throw new Error("EVIDENCE_PATH_INVALID");
    // Never overwrite a symlink pointing outside the artifact directory.
    if (existsSync(target)) {
        const actualFile = relative(realpathSync(directory), realpathSync(target));
        if (actualFile.startsWith("..") || isAbsolute(actualFile)) throw new Error("EVIDENCE_PATH_INVALID");
    }
    return target;
}
function exactKeys(value, keys) {
    if (!value || typeof value !== "object" || Array.isArray(value) ||
        Object.keys(value).sort().join("|") !== [...keys].sort().join("|")) throw new Error("EVIDENCE_SCHEMA_INVALID");
}
const endpoints = new Set(["/health", "/auth/csrf", "/auth/login", "/auth/me", "/auth/logout", "/account/password", "/users", "/users/:id", "/users/:id/reset-password", "/audit-events", "/watchlist/runs", "/watchlist/sources", "/watchlist/search", "/watchlist/exports", "/search-history", "/search-history/:id", "/transactions", "/"]);
export function validateEvidence(report, { verificationId, now = Date.now(), expectedSourceHash = sourceHash(),
    expectedScenarioHash = scenarioHash(), scenarios = definitions() }) {
    exactKeys(report, ["schemaVersion", "verificationId", "generatedAt", "sourceHash", "scenarioHash", "status", "scenarios"]);
    if (report.schemaVersion !== 1 || report.status !== "PASSED" || !verificationId ||
        report.verificationId !== verificationId) throw new Error("EVIDENCE_RUN_INVALID");
    const time = Date.parse(report.generatedAt);
    if (!Number.isFinite(time) || time > now + 60000 || now - time > 2 * 60 * 60 * 1000) throw new Error("EVIDENCE_STALE");
    if (report.sourceHash !== expectedSourceHash || report.scenarioHash !== expectedScenarioHash) throw new Error("EVIDENCE_SOURCE_CHANGED");
    if (!Array.isArray(report.scenarios) || report.scenarios.length !== scenarios.length) throw new Error("EVIDENCE_INCOMPLETE");
    for (let i = 0; i < scenarios.length; i++) {
        const actual = report.scenarios[i], expected = scenarios[i];
        exactKeys(actual, ["id", "status", "checks", "requests"]);
        if (actual.id !== expected.id || actual.status !== "PASSED") throw new Error("EVIDENCE_SCENARIO_FAILED");
        if (!Array.isArray(actual.checks) || actual.checks.length !== expected.checks.length) throw new Error("EVIDENCE_CHECK_MISSING");
        for (let j = 0; j < expected.checks.length; j++) {
            exactKeys(actual.checks[j], ["name", "passed"]);
            if (actual.checks[j].name !== expected.checks[j] || actual.checks[j].passed !== true) throw new Error("EVIDENCE_CHECK_FAILED");
        }
        if (!Array.isArray(actual.requests) || actual.requests.length < 1) throw new Error("EVIDENCE_REQUEST_MISSING");
        for (const request of actual.requests) {
            exactKeys(request, ["method", "path", "status", "correlationId"]);
            if (!["GET", "POST", "PATCH", "DELETE"].includes(request.method) || !endpoints.has(request.path) ||
                !Number.isInteger(request.status) || request.status < 200 || request.status > 499 ||
                (request.path !== "/" && !/^[A-Za-z0-9-]{8,64}$/.test(request.correlationId)) ||
                (request.path === "/" && request.correlationId !== null)) throw new Error("EVIDENCE_REQUEST_INVALID");
        }
    }
    return true;
}
