import type { Metadata, Viewport } from "next";
import { Inter, Noto_Sans_Bengali } from "next/font/google";
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });
const bangla = Noto_Sans_Bengali({ subsets: ["bengali"], variable: "--font-bangla", weight: ["400", "500", "600", "700"] });

export const metadata: Metadata = {
  title: "BartaGuard",
  description: "Check a Bangla SMS or WhatsApp message for scam signs. Runs in your browser; nothing is uploaded.",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: [{ media: "(prefers-color-scheme: light)", color: "#f2f2f7" }, { media: "(prefers-color-scheme: dark)", color: "#000000" }],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="bn" suppressHydrationWarning>
      <head>
        {/* Apply a saved Light/Dark choice before first paint, so the page doesn't flash. */}
        <script dangerouslySetInnerHTML={{ __html: `try{var t=localStorage.getItem("theme");if(t==="light"||t==="dark")document.documentElement.dataset.theme=t}catch(e){}` }} />
      </head>
      <body className={`${inter.variable} ${bangla.variable}`}>{children}</body>
    </html>
  );
}
