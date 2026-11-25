import type { NextConfig } from "next";

// The checker runs in the browser; /api is only used for the optional server API (services/api).
const API = process.env.BARTAGUARD_API_URL ?? "http://127.0.0.1:8020";

const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${API}/api/:path*` }];
  },
};

export default nextConfig;
