import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "트레이드옵스 허브",
  description:
    "BIS 우려거래자 자료 수집, 이름 검색, 버전별 변경 확인과 CSV 다운로드",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>
        <a className="skip-link" href="#main-content">
          본문으로 건너뛰기
        </a>
        {children}
      </body>
    </html>
  );
}
