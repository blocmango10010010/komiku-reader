import Link from "next/link";
import Icon from "./Icons";

export default function ErrorNotice({ message, retryHref }: { message: string; retryHref: string }) {
  return (
    <div className="state-box">
      <span className="state-icon">
        <Icon name="refresh" size={28} />
      </span>
      <p>{message}</p>
      <Link href={retryHref} className="btn btn-primary">
        Coba Lagi
      </Link>
    </div>
  );
}
