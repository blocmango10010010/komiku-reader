export default function Loading() {
  return (
    <div className="stack" role="status" aria-label="Memuat">
      <div className="skeleton skeleton-hero" />
      <div className="grid">
        {Array.from({ length: 12 }, (_, i) => (
          <div key={i} className="skeleton-card">
            <div className="skeleton skeleton-cover" />
            <div className="skeleton skeleton-line" />
            <div className="skeleton skeleton-line short" />
          </div>
        ))}
      </div>
    </div>
  );
}
