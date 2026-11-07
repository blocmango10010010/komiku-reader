"use client";

import { useEffect, useRef, useState } from "react";
import { coverUrl } from "@/lib/format";

/** Cover image with a gradient placeholder showing the title's initial while loading or on error. */
export default function Cover({
  src,
  title,
  className = "",
  width = 300,
  height = 400,
  eager = false,
}: {
  src: string;
  title: string;
  className?: string;
  width?: number;
  height?: number;
  eager?: boolean;
}) {
  const [state, setState] = useState<"loading" | "loaded" | "error">(src ? "loading" : "error");
  const imgRef = useRef<HTMLImageElement>(null);

  // Server-rendered images can finish loading before hydration, so onLoad never fires.
  useEffect(() => {
    const img = imgRef.current;
    if (img?.complete) setState(img.naturalWidth > 0 ? "loaded" : "error");
  }, []);
  const initial = title.trim().charAt(0).toUpperCase() || "?";
  return (
    <div className={`cover ${className}`} data-state={state}>
      <span className="cover-initial" aria-hidden="true">
        {initial}
      </span>
      {src && state !== "error" && (
        // eslint-disable-next-line @next/next/no-img-element -- covers are served by the source CDN
        <img
          ref={imgRef}
          src={coverUrl(src, width, height)}
          alt=""
          loading={eager ? "eager" : "lazy"}
          decoding="async"
          referrerPolicy="no-referrer"
          onLoad={() => setState("loaded")}
          onError={() => setState("error")}
        />
      )}
    </div>
  );
}
