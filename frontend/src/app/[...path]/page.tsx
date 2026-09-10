import { Suspense } from "react";
import { notFound } from "next/navigation";
import Console from "../../components/console";
export default async function Page({
  params,
}: {
  params: Promise<{ path: string[] }>;
}) {
  const route = (await params).path.join("/");
  const pages = [
    "login",
    "account",
    "users",
    "audit",
    "search-history",
    "law-search",
    "watchlist/search",
    "watchlist/sources",
    "watchlist/runs",
  ];
  if (
    !pages.includes(route) &&
    !/^watchlist\/(records|runs)\/[1-9][0-9]*$/.test(route)
  )
    notFound();
  return (
    <Suspense fallback={<p>업무 화면을 불러오는 중입니다.</p>}>
      <Console key={route} />
    </Suspense>
  );
}
