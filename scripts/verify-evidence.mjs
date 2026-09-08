import { readFileSync } from "node:fs";
import { parseArgs } from "node:util";
import { artifactPath, validateEvidence } from "./verification-common.mjs";

try {
    const { values } = parseArgs({ options: { evidence: { type: "string" }, "verification-id": { type: "string" } } });
    const evidence = JSON.parse(readFileSync(artifactPath(values.evidence ?? "artifacts/runtime/latest.json"), "utf8"));
    validateEvidence(evidence, { verificationId: values["verification-id"] });
    console.log("Evidence passed: all current scenarios and assertions are present.");
} catch (error) {
    console.error(/^EVIDENCE_[A-Z_]+$/.test(error.message) ? error.message : "EVIDENCE_INVALID");
    process.exitCode = 1;
}
