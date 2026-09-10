import type { NextConfig } from "next";
const nextConfig: NextConfig = {
  experimental: { proxyTimeout: 195000 },
  distDir: process.env.NEXT_DIST_DIR ?? ".next",
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination:
          (process.env.API_PROXY_TARGET ?? "http://127.0.0.1:8081") +
          "/api/v1/:path*",
      },
    ];
  },
};
export default nextConfig;
