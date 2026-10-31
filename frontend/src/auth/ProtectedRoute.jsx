import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * ProtectedRoute – redirects unauthenticated visitors to /login.
 *
 * Wraps all routes that require a valid JWT.  Nested via <Outlet> so any
 * number of child routes can be protected with a single parent.
 *
 * Props:
 *   roles {string[]} – optional; if provided, only users with a matching role
 *                      may access these routes.  Others are sent to /.
 *
 * After login the router redirects back to the originally requested path
 * using the `state.from` location object.
 */
export default function ProtectedRoute({ roles }) {
  const { user, isAuthenticated } = useAuth();
  const location = useLocation();

  // Not logged in → redirect to /login, saving the attempted path
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Logged in but wrong role → redirect to dashboard
  if (roles && !roles.includes(user?.role)) {
    return <Navigate to="/" replace />;
  }

  // Authenticated (and correct role if specified) → render the child route
  return <Outlet />;
}
