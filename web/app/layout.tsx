import type { Metadata, Viewport } from "next";
import { Plus_Jakarta_Sans } from "next/font/google";
import { Analytics } from "@vercel/analytics/next";
import { SpeedInsights } from "@vercel/speed-insights/next";
import { BottomNav, TopBar } from "@/components/Nav";
import "./globals.css";

const font = Plus_Jakarta_Sans({ subsets: ["latin"], display: "swap", variable: "--font-sans" });

export const metadata: Metadata = {
  title: { default: "Komiku Reader", template: "%s · Komiku Reader" },
  description: "Baca manga, manhwa, dan manhua Bahasa Indonesia.",
};

export const viewport: Viewport = {
  themeColor: "#0b0b12",
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="id" className={font.variable}>
      <body>
        <TopBar />
        <main className="container">{children}</main>
        <BottomNav />
        <Analytics />
        <SpeedInsights />
      </body>
    </html>
  );
}
