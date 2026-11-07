import Link from "next/link";
import Icon from "@/components/Icons";

export default function NotFound() {
  return (
    <div className="state-box">
      <span className="state-icon">
        <Icon name="search" size={28} />
      </span>
      <h1>Tidak ditemukan</h1>
      <p className="muted">Komik atau chapter ini tidak ada, atau komiku.org sedang tidak bisa diakses.</p>
      <Link href="/" className="btn btn-primary">
        Ke Beranda
      </Link>
    </div>
  );
}
