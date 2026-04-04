import { type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

interface NavItem {
  to: string
  label: string
  icon: string
}

const NAV_ITEMS: NavItem[] = [
  { to: '/dashboard',    label: 'Dashboard',    icon: '⬡' },
  { to: '/jobs',         label: 'Browse Jobs',  icon: '🔍' },
  { to: '/applications', label: 'Applications', icon: '📋' },
  { to: '/workflows',    label: 'Workflows',    icon: '⚡' },
]

interface Props {
  children: ReactNode
}

export default function Layout({ children }: Props) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen flex">
      {/* Sidebar */}
      <aside className="w-60 flex-shrink-0 bg-[#1e293b] flex flex-col">
        {/* Brand */}
        <div className="h-16 flex items-center px-5 border-b border-white/10">
          <span className="text-white font-bold text-lg tracking-tight">
            Autoflow
          </span>
          <span className="ml-2 text-xs text-blue-400 font-medium px-1.5 py-0.5 bg-blue-400/10 rounded">
            beta
          </span>
        </div>

        {/* Nav */}
        <nav className="flex-1 px-3 py-4 space-y-0.5">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                [
                  'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-blue-600 text-white'
                    : 'text-slate-300 hover:bg-white/10 hover:text-white',
                ].join(' ')
              }
            >
              <span className="text-base leading-none">{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* User section */}
        <div className="border-t border-white/10 px-4 py-4">
          {user && (
            <div className="flex items-center gap-3 mb-3">
              <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                {user.displayName?.charAt(0).toUpperCase() ?? '?'}
              </div>
              <div className="min-w-0">
                <p className="text-sm font-medium text-white truncate">
                  {user.displayName}
                </p>
                <p className="text-xs text-slate-400 truncate">{user.email}</p>
              </div>
            </div>
          )}
          <button
            onClick={handleLogout}
            className="w-full text-left text-xs text-slate-400 hover:text-white transition-colors px-1 py-1"
          >
            Sign out →
          </button>
        </div>
      </aside>

      {/* Main content */}
      <div className="flex-1 flex flex-col min-w-0 bg-gray-50">
        <main className="flex-1 overflow-auto p-8">{children}</main>
      </div>
    </div>
  )
}
