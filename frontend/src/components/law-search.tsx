"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { ApiError, request } from "../lib/api";

type Article = {
  articleId: number;
  lawTitle?: string;
  articleNumber: string;
  articleTitle?: string;
  content: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  amendmentKind?: string;
  previousArticleId?: number;
  current?: boolean;
  historicalEntries?: Article[];
};
type Answer = {
  status: "OK" | "LOW_CONFIDENCE" | "INSUFFICIENT_INFO" | "FAILED";
  reasoning?: string;
  disclaimer?: string;
  message?: string;
  followUpQuestions?: string[];
  citedArticles: Article[];
  effectiveBasis: { asOf?: string };
  requestId: string;
  correlationId: string;
};
type Diff = { contentA: string; contentB: string; contentHashEqual: boolean };
const text = (value?: string) => value?.replaceAll("**", "") ?? "";

function Citation({
  article,
  onAuthError,
}: {
  article: Article;
  onAuthError: (e: unknown) => void;
}) {
  const [history, setHistory] = useState<Article[] | null>(null);
  const [diff, setDiff] = useState<Diff | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const active = useRef<AbortController | null>(null);
  useEffect(
    () => () => {
      active.current?.abort();
      active.current = null;
    },
    [],
  );
  async function load(kind: "history" | "diff", previous?: number) {
    if (active.current) return;
    const controller = new AbortController();
    active.current = controller;
    const timer = setTimeout(() => controller.abort(), 205000);
    setPending(true);
    setError("");
    setDiff(null);
    try {
      const base = `/law-search/articles/${article.articleId}`;
      if (kind === "history") {
        const result = await request<{ entries: Article[] }>(
          base + "/history",
          { signal: controller.signal },
        );
        if (!controller.signal.aborted) setHistory(result.entries);
      } else {
        const result = await request<Diff>(
          base + "/diff?compareWith=" + previous,
          { signal: controller.signal },
        );
        if (!controller.signal.aborted) setDiff(result);
      }
    } catch (e) {
      if (active.current === controller) {
        setError(
          controller.signal.aborted
            ? "응답 대기 시간이 지났습니다. 다시 시도해 주세요."
            : e instanceof Error
              ? e.message
              : "조문을 불러오지 못했습니다.",
        );
        onAuthError(e);
      }
    } finally {
      clearTimeout(timer);
      if (active.current === controller) {
        active.current = null;
        setPending(false);
      }
    }
  }
  const entries = history ?? article.historicalEntries ?? [];
  return (
    <details className="law-citation">
      <summary>
        <span>
          <strong>{article.lawTitle}</strong>
          <span className="law-article-title">
            {article.articleNumber} {article.articleTitle}
          </span>
        </span>
        <span className="law-expand">
          <span className="law-open-label">전체 보기</span>
          <span className="law-close-label">접기</span>
        </span>
      </summary>
      <div className="law-citation-body">
        <p className="muted">
          유효기간: {article.effectiveFrom ?? "미제공"} ~{" "}
          {article.effectiveTo ?? "종료일 미제공"}
        </p>
        <div
          className="law-text law-article-content"
          tabIndex={0}
          aria-label="조문 본문"
        >
          {text(article.content)}
        </div>
        <div className="law-actions">
          <button
            type="button"
            disabled={pending}
            onClick={() => load("history")}
          >
            조문 이력
          </button>
          <button
            type="button"
            disabled={pending || !article.previousArticleId}
            onClick={() => load("diff", article.previousArticleId)}
          >
            이전 조문 비교
          </button>
          {pending && (
            <span role="status" className="collection-progress">
              <span className="collection-spinner" aria-hidden="true" />
              불러오는 중
            </span>
          )}
        </div>
        {error && (
          <p role="alert" className="warning">
            {error}
          </p>
        )}
        {(history !== null || entries.length > 0) && (
          <section aria-label="조문 이력" className="law-history">
            <h3>조문 이력</h3>
            {!entries.length && (
              <p className="muted">제공된 조문 이력이 없습니다.</p>
            )}
            {entries.map((entry, index) => (
              <details key={`${entry.articleId}-${index}`}>
                <summary>
                  {entry.effectiveFrom ?? "시작일 미제공"} ~{" "}
                  {entry.effectiveTo ?? "종료일 미제공"} · {entry.articleNumber}
                  {entry.current ? " · 현재" : ""}
                </summary>
                <div
                  className="law-text law-article-content"
                  tabIndex={0}
                  aria-label="이력 조문 본문"
                >
                  {text(entry.content)}
                </div>
                {entry.articleId !== article.articleId && (
                  <button
                    type="button"
                    disabled={pending}
                    onClick={() => load("diff", entry.articleId)}
                  >
                    이 조문과 비교
                  </button>
                )}
              </details>
            ))}
          </section>
        )}
        {diff && (
          <section className="law-comparison" aria-label="조문 비교">
            <h3>조문 비교</h3>
            <p className="muted">
              {diff.contentHashEqual
                ? "두 조문의 본문이 같습니다."
                : "두 조문의 본문이 다릅니다."}
            </p>
            <div className="law-comparison-grid">
              <div>
                <h4>선택한 인용 조문</h4>
                <div className="law-text law-article-content" tabIndex={0}>
                  {text(diff.contentA)}
                </div>
              </div>
              <div>
                <h4>비교 조문</h4>
                <div className="law-text law-article-content" tabIndex={0}>
                  {text(diff.contentB)}
                </div>
              </div>
            </div>
          </section>
        )}
      </div>
    </details>
  );
}

export default function LawSearch({
  onAuthError,
}: {
  onAuthError: (e: unknown) => void;
}) {
  const [question, setQuestion] = useState("");
  const [asOf, setAsOf] = useState("");
  const [areas, setAreas] = useState<string[]>([]);
  const [answer, setAnswer] = useState<Answer | null>(null);
  const [submitted, setSubmitted] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const active = useRef<AbortController | null>(null);
  const resultHeading = useRef<HTMLHeadingElement>(null);
  useEffect(
    () => () => {
      active.current?.abort();
      active.current = null;
    },
    [],
  );
  useEffect(() => {
    if (answer) resultHeading.current?.focus();
  }, [answer]);
  function authError(e: unknown) {
    if (
      e instanceof ApiError &&
      (e.status === 401 ||
        ["CSRF_INVALID", "PASSWORD_CHANGE_REQUIRED"].includes(e.code))
    )
      onAuthError(e);
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (active.current || !question.trim()) return;
    const controller = new AbortController();
    active.current = controller;
    const timer = setTimeout(() => controller.abort(), 205000);
    setBusy(true);
    setError("");
    setAnswer(null);
    setSubmitted(question);
    try {
      const result = await request<Answer>("/law-search/ask", {
        method: "POST",
        signal: controller.signal,
        body: JSON.stringify({
          question,
          asOf: asOf || null,
          researchAreas: areas,
        }),
      });
      if (active.current === controller && !controller.signal.aborted)
        setAnswer(result);
    } catch (e) {
      if (active.current === controller) {
        setError(
          controller.signal.aborted
            ? "응답 대기 시간이 지났습니다. 검색 버튼을 눌러 다시 시도해 주세요."
            : e instanceof Error
              ? e.message
              : "법령 검색을 완료하지 못했습니다.",
        );
        authError(e);
      }
    } finally {
      clearTimeout(timer);
      if (active.current === controller) {
        active.current = null;
        setBusy(false);
      }
    }
  }
  return (
    <div className="law-search">
      <form className="panel filters" onSubmit={submit}>
        <p className="muted" id="law-scope">
          무역안보·방산·기술보호 관련 8개 핵심 법령과 수집된 하위 법령을
          검색합니다. 대한민국 전체 법령이나 실시간 공식 법령 동기화를 제공하지
          않습니다.
        </p>
        <label htmlFor="law-question">질문</label>
        <textarea
          id="law-question"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          required
          maxLength={4000}
          rows={4}
          placeholder="궁금한 상황이나 찾고 싶은 법령 내용을 입력하세요"
          aria-describedby="law-scope law-question-count"
          disabled={busy}
        />
        <div id="law-question-count" className="law-count">
          {question.length.toLocaleString("ko-KR")} / 4,000자
        </div>
        <div className="law-options">
          <fieldset disabled={busy}>
            <legend>
              분야 <span className="muted">(선택 사항)</span>
            </legend>
            <div className="law-area-options">
              {[
                ["STRATEGIC_GOODS", "전략물자"],
                ["DEFENSE_MATERIALS", "방산물자·국방과학기술"],
              ].map(([code, label]) => (
                <label key={code}>
                  <input
                    type="checkbox"
                    checked={areas.includes(code)}
                    onChange={(e) =>
                      setAreas(
                        e.target.checked
                          ? [...areas, code]
                          : areas.filter((a) => a !== code),
                      )
                    }
                  />
                  {label}
                </label>
              ))}
            </div>
            <p className="muted">
              선택한 분야는 검색 우선순위와 답변 맥락에 반영됩니다.
            </p>
          </fieldset>
          <label>
            기준일{" "}
            <input
              type="date"
              value={asOf}
              onChange={(e) => setAsOf(e.target.value)}
              disabled={busy}
            />
            <small>미선택 시 현재 유효 조문 기준</small>
          </label>
        </div>
        <div className="law-actions">
          <button
            type="submit"
            className="primary"
            disabled={busy || !question.trim()}
          >
            {busy ? (
              <span className="collection-progress">
                <span className="collection-spinner" aria-hidden="true" />
                검색 중
              </span>
            ) : (
              "검색"
            )}
          </button>
          <span role="status">
            {busy ? "답변을 기다리고 있습니다. 잠시만 기다려 주세요." : ""}
          </span>
        </div>
        {error && (
          <p className="warning" role="alert">
            {error}
          </p>
        )}
      </form>
      {answer && (
        <section className="panel law-answer" aria-label="법령 검색 결과">
          <h2 ref={resultHeading} tabIndex={-1}>
            검색 결과
          </h2>
          <p className="law-submitted">{submitted}</p>
          <p className="muted">
            기준: {answer.effectiveBasis.asOf ?? "현재 유효 조문"}
          </p>
          {answer.status !== "OK" && (
            <p role="status" className="warning">
              {answer.status === "LOW_CONFIDENCE"
                ? "확인된 근거가 충분하지 않습니다. 답변과 인용 조문을 함께 확인해 주세요."
                : answer.status === "INSUFFICIENT_INFO"
                  ? "추가 정보가 필요합니다. 아래 확인 질문을 참고해 질문을 보완해 주세요."
                  : answer.message}
            </p>
          )}
          {answer.reasoning && (
            <div className="law-text">{text(answer.reasoning)}</div>
          )}
          {!!answer.followUpQuestions?.length && (
            <section>
              <h3>추가 확인 질문</h3>
              <ul>
                {answer.followUpQuestions.map((q, i) => (
                  <li key={i}>{q}</li>
                ))}
              </ul>
            </section>
          )}
          {!!answer.citedArticles.length && (
            <section>
              <h3>
                인용 조문{" "}
                <span className="muted">{answer.citedArticles.length}건</span>
              </h3>
              {answer.citedArticles.map((article, i) => (
                <Citation
                  key={`${answer.requestId}-${article.articleId}-${i}`}
                  article={article}
                  onAuthError={authError}
                />
              ))}
            </section>
          )}
          {answer.disclaimer && (
            <p className="law-disclaimer">{answer.disclaimer}</p>
          )}
          {answer.status === "FAILED" && (
            <p className="muted">
              입력한 질문은 유지됩니다. 위 검색 버튼으로 다시 시도할 수
              있습니다.
            </p>
          )}
        </section>
      )}
    </div>
  );
}
