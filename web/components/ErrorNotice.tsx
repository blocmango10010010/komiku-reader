import Link from "next/link";

export default function ErrorNotice({ message, retryHref }: { message: string; retryHref: string }) {
  return (
    <div className="error-box">
      <p>⚠️ {message}</p>
      <Link href={retryHref} className="btn">
        Coba Lagi
      </Link>
    </div>
  );
}
