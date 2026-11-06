import Link from "next/link";

export default function NotFound() {
  return (
    <div className="error-box">
      <p>Halaman atau komik tidak ditemukan, atau komiku.org sedang tidak bisa diakses.</p>
      <Link href="/" className="btn">Ke Beranda</Link>
    </div>
  );
}
