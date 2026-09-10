import { Link } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { useAuth } from '../context/auth-context';

function Navbar() {
  const { user, logout } = useAuth();

  return (
    <nav className="navbar">
      <Link to="/dashboard" className="navbar-brand gradient-text">
        DocPipeline
      </Link>
      <div className="navbar-right">
        <span className="navbar-user">{user?.email || user?.fullName || 'User'}</span>
        <button className="btn btn-secondary btn-sm" onClick={() => void logout()}>
          <LogOut size={16} />
          Logout
        </button>
      </div>
    </nav>
  );
}

export default Navbar;
