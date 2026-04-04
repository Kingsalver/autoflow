import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import Layout from '../components/Layout'
import StatCard from '../components/StatCard'
import { useAuth } from '../hooks/useAuth'
import { listWorkflows, getWorkflowExecutions } from '../api/workflows'
import { getApplications } from '../api/jobs'
import type { WorkflowExecution } from '../api/workflows'

const STATUS_COLORS: Record<WorkflowExecution['status'], string> = {
  SUCCESS: 'text-green-600 bg-green-50',
  FAILURE: 'text-red-600 bg-red-50',
  RUNNING: 'text-blue-600 bg-blue-50',
  PENDING: 'text-gray-600 bg-gray-100',
}

function formatDuration(start: string, end?: string): string {
  if (!end) return '—'
  const ms = new Date(end).getTime() - new Date(start).getTime()
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60_000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs}h ago`
  return `${Math.floor(hrs / 24)}d ago`
}

export default function Dashboard() {
  const { user } = useAuth()

  const { data: workflows = [] } = useQuery({
    queryKey: ['workflows'],
    queryFn: listWorkflows,
  })

  const { data: applications = [] } = useQuery({
    queryKey: ['applications'],
    queryFn: getApplications,
  })

  // Fetch executions for the first few workflows (recent activity)
  const firstWorkflowId = workflows[0]?.id
  const { data: recentExecPage } = useQuery({
    queryKey: ['executions', firstWorkflowId],
    queryFn: () => getWorkflowExecutions(firstWorkflowId!, 0, 5),
    enabled: !!firstWorkflowId,
  })

  const recentExecutions = recentExecPage?.content ?? []

  // Application counts by status
  const appCounts = applications.reduce(
    (acc, app) => {
      acc[app.status] = (acc[app.status] ?? 0) + 1
      return acc
    },
    {} as Record<string, number>,
  )

  const totalApps = applications.length

  return (
    <Layout>
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">
          Good morning, {user?.displayName?.split(' ')[0] ?? 'there'} 👋
        </h1>
        <p className="text-gray-500 mt-1">
          Here's what's happening with your job search today.
        </p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-8">
        <StatCard
          title="Active Workflows"
          value={workflows.filter((w) => w.enabled).length}
          subtitle={`${workflows.length} total`}
          icon="⚡"
          accent="blue"
        />
        <StatCard
          title="Jobs Available"
          value="120+"
          subtitle="Mock data · real data coming soon"
          icon="🔍"
          accent="purple"
        />
        <StatCard
          title="Applications"
          value={totalApps}
          subtitle={`${appCounts.interview ?? 0} in interview · ${appCounts.offer ?? 0} offers`}
          icon="📋"
          accent="green"
        />
        <StatCard
          title="Saved Jobs"
          value={appCounts.saved ?? 0}
          subtitle="Ready to apply"
          icon="🔖"
          accent="yellow"
        />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        {/* Application pipeline */}
        <div className="xl:col-span-2 bg-white rounded-xl border border-gray-200 shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-semibold text-gray-900">Application Pipeline</h2>
            <Link
              to="/applications"
              className="text-sm text-blue-600 hover:underline"
            >
              View board →
            </Link>
          </div>

          <div className="grid grid-cols-5 gap-2">
            {(
              [
                { key: 'saved',     label: 'Saved',     color: 'bg-gray-100 text-gray-700' },
                { key: 'applied',   label: 'Applied',   color: 'bg-blue-100 text-blue-700' },
                { key: 'interview', label: 'Interview', color: 'bg-yellow-100 text-yellow-700' },
                { key: 'offer',     label: 'Offer',     color: 'bg-green-100 text-green-700' },
                { key: 'rejected',  label: 'Rejected',  color: 'bg-red-100 text-red-700' },
              ] as const
            ).map(({ key, label, color }) => (
              <div key={key} className="text-center">
                <div
                  className={`rounded-lg py-4 px-2 ${color}`}
                >
                  <div className="text-2xl font-bold">{appCounts[key] ?? 0}</div>
                  <div className="text-xs font-medium mt-1 opacity-80">{label}</div>
                </div>
              </div>
            ))}
          </div>

          {/* Recent applications */}
          {applications.length > 0 && (
            <div className="mt-5 border-t border-gray-100 pt-4 space-y-2">
              <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                Recent Activity
              </p>
              {applications.slice(0, 4).map((app) => (
                <div
                  key={app.id}
                  className="flex items-center justify-between py-1.5"
                >
                  <div>
                    <span className="text-sm font-medium text-gray-800">
                      {app.company}
                    </span>
                    <span className="text-sm text-gray-400 mx-1">·</span>
                    <span className="text-sm text-gray-500">{app.role}</span>
                  </div>
                  <span
                    className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                      {
                        saved: 'bg-gray-100 text-gray-600',
                        applied: 'bg-blue-100 text-blue-700',
                        interview: 'bg-yellow-100 text-yellow-700',
                        offer: 'bg-green-100 text-green-700',
                        rejected: 'bg-red-100 text-red-600',
                      }[app.status]
                    }`}
                  >
                    {app.status.charAt(0).toUpperCase() + app.status.slice(1)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Right column */}
        <div className="space-y-6">
          {/* Quick actions */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
            <h2 className="font-semibold text-gray-900 mb-4">Quick Actions</h2>
            <div className="space-y-2">
              {[
                { to: '/jobs',         label: 'Browse Jobs',         icon: '🔍', color: 'hover:bg-blue-50 hover:text-blue-700' },
                { to: '/applications', label: 'My Applications',     icon: '📋', color: 'hover:bg-green-50 hover:text-green-700' },
                { to: '/workflows',    label: 'Manage Workflows',    icon: '⚡', color: 'hover:bg-purple-50 hover:text-purple-700' },
              ].map(({ to, label, icon, color }) => (
                <Link
                  key={to}
                  to={to}
                  className={`flex items-center gap-3 px-4 py-3 rounded-lg border border-gray-100 text-sm font-medium text-gray-700 transition-colors ${color}`}
                >
                  <span>{icon}</span>
                  {label}
                  <span className="ml-auto text-gray-300">→</span>
                </Link>
              ))}
            </div>
          </div>

          {/* Recent executions */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-gray-900">Workflow Runs</h2>
              <Link to="/workflows" className="text-sm text-blue-600 hover:underline">
                See all →
              </Link>
            </div>

            {recentExecutions.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-4">
                No executions yet.
                <br />
                <Link to="/workflows" className="text-blue-600 hover:underline">
                  Create a workflow
                </Link>
              </p>
            ) : (
              <div className="space-y-2">
                {recentExecutions.slice(0, 5).map((exec) => (
                  <div
                    key={exec.id}
                    className="flex items-center justify-between text-sm"
                  >
                    <div>
                      <span
                        className={`inline-block text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[exec.status]}`}
                      >
                        {exec.status}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 text-gray-400 text-xs">
                      <span>{formatDuration(exec.startedAt, exec.completedAt)}</span>
                      <span>{timeAgo(exec.startedAt)}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </Layout>
  )
}
