import Link from "next/link";
export default function NotFound() {
  return (
    <main id="main-content" className="login-page">
      <section className="login-card">
        <h1>페이지를 찾을 수 없습니다</h1>
        <p>주소를 확인하거나 우려거래자 검색으로 이동해 주세요.</p>
        <Link href="/watchlist/search">우려거래자 검색으로 이동</Link>
      </section>
    </main>
  );
}
