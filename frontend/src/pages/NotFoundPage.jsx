import { useNavigate } from 'react-router-dom';

/**
 * NotFoundPage – displayed for any unmatched route inside the protected area.
 */
export default function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <div className="not-found">
      <div className="nf-code" aria-hidden="true">404</div>
      <h1 className="nf-title">Page Not Found</h1>
      <p className="nf-sub">
        This page doesn&apos;t exist or hasn&apos;t been implemented yet.
      </p>
      <button
        id="btn-go-home"
        className="btn-primary-custom"
        onClick={() => navigate('/')}
        style={{ marginTop: 8 }}
      >
        ← Back to Dashboard
      </button>
    </div>
  );
}
