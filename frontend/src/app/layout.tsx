import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = { title: "트레이드옵스 허브", description: "검토 대상 목록 갱신, 버전별 변경 검토, 수집 이력 관리." };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko"><body><a className="skip-link" href="#main-content">본문으로 건너뛰기</a>{children}</body></html>;
}
