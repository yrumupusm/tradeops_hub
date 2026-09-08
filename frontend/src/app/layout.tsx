import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = { title: "TradeOps Hub", description: "Watchlist updates, change review, and trade data operations." };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko"><body><a className="skip-link" href="#main-content">본문으로 건너뛰기</a>{children}</body></html>;
}
