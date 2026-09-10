import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/auth-context';

function ProtectedRoute() {
  const { isAuthenticated, loading } = useAuth();

  if (loading) {
    return <div className="loading-center"><div className="loading-spinner" /></div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}

export default ProtectedRoute;
