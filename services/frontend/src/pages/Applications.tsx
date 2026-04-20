import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import Layout from '../components/Layout'
import StatusBadge from '../components/StatusBadge'
import {
  getApplications,
  updateApplicationStatus,
  updateApplicationNotes,
  type Application,
  type ApplicationStatus,
} from '../api/jobs'

const COLUMNS: { status: ApplicationStatus; label: string; color: string }[] = [
  { status: 'saved',     label: 'Saved',     color: 'border-gray-200 bg-gray-50' },
  { status: 'applied',   label: 'Applied',   color: 'border-blue-200 bg-blue-50' },
  { status: 'interview', label: 'Interview', color: 'border-yellow-200 bg-yellow-50' },
  { status: 'offer',     label: 'Offer',     color: 'border-green-200 bg-green-50' },
  { status: 'rejected',  label: 'Rejected',  color: 'border-red-200 bg-red-50' },
]

const STATUS_ORDER: ApplicationStatus[] = [
  'saved', 'applied', 'interview', 'offer', 'rejected',
]

function nextStatus(current: ApplicationStatus): ApplicationStatus | null {
  const idx = STATUS_ORDER.indexOf(current)
  if (idx === -1 || idx >= STATUS_ORDER.length - 1) return null
  return STATUS_ORDER[idx + 1]
}

function prevStatus(current: ApplicationStatus): ApplicationStatus | null {
  const idx = STATUS_ORDER.indexOf(current)
  if (idx <= 0) return null
  return STATUS_ORDER[idx - 1]
}

export default function Applications() {
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<Application | null>(null)
  const [notes, setNotes] = useState('')
  const [savingNotes, setSavingNotes] = useState(false)

  const { data: applications = [], isLoading } = useQuery({
    queryKey: ['applications'],
    queryFn: getApplications,
  })

  const moveMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: ApplicationStatus }) =>
      updateApplicationStatus(id, status),
    onSuccess: (updated) => {
      queryClient.setQueryData<Application[]>(['applications'], (prev) =>
        prev ? prev.map((a) => (a.id === updated.id ? updated : a)) : [updated],
      )
      // Update selected if open
      if (selected?.id === updated.id) {
        setSelected(updated)
      }
    },
  })

  async function handleSaveNotes() {
    if (!selected) return
    setSavingNotes(true)
    try {
      const updated = await updateApplicationNotes(selected.id, notes)
      queryClient.setQueryData<Application[]>(['applications'], (prev) =>
        prev ? prev.map((a) => (a.id === updated.id ? updated : a)) : [updated],
      )
      setSelected(updated)
    } finally {
      setSavingNotes(false)
    }
  }

  function openDrawer(app: Application) {
    setSelected(app)
    setNotes(app.notes)
  }

  function closeDrawer() {
    setSelected(null)
    setNotes('')
  }

  const byStatus = COLUMNS.reduce(
    (acc, col) => {
      acc[col.status] = applications.filter((a) => a.status === col.status)
      return acc
    },
    {} as Record<ApplicationStatus, Application[]>,
  )

  return (
    <Layout>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Applications</h1>
        <p className="text-gray-500 mt-1">
          {applications.length} total application{applications.length !== 1 ? 's' : ''}
          {' · '}
          Track your job search pipeline
        </p>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center h-64 text-gray-400">
          Loading…
        </div>
      ) : (
        <div className="flex gap-4 overflow-x-auto pb-4">
          {COLUMNS.map((col) => {
            const items = byStatus[col.status] ?? []
            return (
              <div
                key={col.status}
                className={`flex-shrink-0 w-64 rounded-xl border ${col.color} p-3`}
              >
                {/* Column header */}
                <div className="flex items-center justify-between mb-3 px-1">
                  <span className="text-xs font-bold text-gray-600 uppercase tracking-wider">
                    {col.label}
                  </span>
                  <span className="text-xs bg-white border border-gray-200 text-gray-500 font-medium w-5 h-5 rounded-full flex items-center justify-center">
                    {items.length}
                  </span>
                </div>

                {/* Cards */}
                <div className="space-y-2">
                  {items.map((app) => (
                    <AppCard
                      key={app.id}
                      app={app}
                      onOpen={() => openDrawer(app)}
                      onMove={(status) =>
                        moveMutation.mutate({ id: app.id, status })
                      }
                      isMoving={
                        moveMutation.isPending &&
                        moveMutation.variables?.id === app.id
                      }
                    />
                  ))}

                  {items.length === 0 && (
                    <div className="text-center py-6 text-gray-300 text-xs">
                      No applications
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Detail drawer */}
      {selected && (
        <div
          className="fixed inset-0 z-40 flex"
          onClick={(e) => {
            if (e.target === e.currentTarget) closeDrawer()
          }}
        >
          {/* Overlay */}
          <div className="flex-1 bg-black/30" onClick={closeDrawer} />

          {/* Drawer */}
          <div className="w-full max-w-md bg-white shadow-2xl flex flex-col overflow-y-auto">
            {/* Header */}
            <div className="px-6 py-5 border-b border-gray-100 flex items-start justify-between">
              <div>
                <h2 className="font-bold text-gray-900 text-lg leading-tight">
                  {selected.company}
                </h2>
                <p className="text-gray-500 text-sm mt-0.5">{selected.role}</p>
                <p className="text-gray-400 text-xs mt-0.5">{selected.location}</p>
              </div>
              <button
                onClick={closeDrawer}
                className="text-gray-400 hover:text-gray-700 text-xl leading-none p-1"
              >
                ✕
              </button>
            </div>

            {/* Body */}
            <div className="px-6 py-5 flex-1 space-y-5">
              {/* Status + move */}
              <div>
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                  Status
                </p>
                <div className="flex items-center gap-2 flex-wrap">
                  <StatusBadge status={selected.status} size="md" />
                  {prevStatus(selected.status) && (
                    <button
                      onClick={() =>
                        moveMutation.mutate({
                          id: selected.id,
                          status: prevStatus(selected.status)!,
                        })
                      }
                      className="text-xs border border-gray-200 hover:border-gray-300 text-gray-500 px-3 py-1 rounded-lg transition-colors"
                    >
                      ← Move back
                    </button>
                  )}
                  {nextStatus(selected.status) && (
                    <button
                      onClick={() =>
                        moveMutation.mutate({
                          id: selected.id,
                          status: nextStatus(selected.status)!,
                        })
                      }
                      className="text-xs bg-blue-600 hover:bg-blue-700 text-white px-3 py-1 rounded-lg transition-colors"
                    >
                      Move to {nextStatus(selected.status)} →
                    </button>
                  )}
                </div>
              </div>

              {/* Meta */}
              <div className="grid grid-cols-2 gap-3">
                {selected.appliedDate && (
                  <div className="bg-gray-50 rounded-lg p-3">
                    <p className="text-xs text-gray-400 mb-0.5">Applied</p>
                    <p className="text-sm font-medium text-gray-800">
                      {selected.appliedDate}
                    </p>
                  </div>
                )}
                {selected.salary && (
                  <div className="bg-gray-50 rounded-lg p-3">
                    <p className="text-xs text-gray-400 mb-0.5">Salary</p>
                    <p className="text-sm font-medium text-gray-800">
                      {selected.salary}
                    </p>
                  </div>
                )}
                {selected.contactName && (
                  <div className="bg-gray-50 rounded-lg p-3 col-span-2">
                    <p className="text-xs text-gray-400 mb-0.5">Contact</p>
                    <p className="text-sm font-medium text-gray-800">
                      {selected.contactName}
                      {selected.contactEmail && (
                        <span className="text-gray-400 ml-1">
                          · {selected.contactEmail}
                        </span>
                      )}
                    </p>
                  </div>
                )}
              </div>

              {/* Notes */}
              <div>
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                  Notes
                </p>
                <textarea
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Add notes about this application…"
                  rows={5}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                />
                <button
                  onClick={handleSaveNotes}
                  disabled={savingNotes || notes === selected.notes}
                  className="mt-2 text-sm bg-blue-600 hover:bg-blue-700 disabled:bg-gray-200 disabled:text-gray-400 text-white font-medium px-4 py-2 rounded-lg transition-colors"
                >
                  {savingNotes ? 'Saving…' : 'Save Notes'}
                </button>
              </div>
            </div>

            {/* Footer */}
            <div className="px-6 py-4 border-t border-gray-100 text-xs text-gray-400">
              Added {new Date(selected.createdAt).toLocaleDateString()} · Updated{' '}
              {new Date(selected.updatedAt).toLocaleDateString()}
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}

interface AppCardProps {
  app: Application
  onOpen: () => void
  onMove: (status: ApplicationStatus) => void
  isMoving: boolean
}

function AppCard({ app, onOpen, onMove, isMoving }: AppCardProps) {
  const next = nextStatus(app.status)

  return (
    <div
      className="bg-white rounded-lg border border-gray-100 p-3 shadow-sm hover:shadow-md transition-shadow cursor-pointer"
      onClick={onOpen}
    >
      <p className="font-semibold text-gray-900 text-sm leading-tight">
        {app.company}
      </p>
      <p className="text-xs text-gray-500 mt-0.5 line-clamp-1">{app.role}</p>

      {app.appliedDate && (
        <p className="text-xs text-gray-400 mt-1.5">
          Applied {app.appliedDate}
        </p>
      )}

      {next && (
        <button
          onClick={(e) => {
            e.stopPropagation()
            onMove(next)
          }}
          disabled={isMoving}
          className="mt-2 w-full text-center text-xs border border-gray-200 hover:border-blue-300 hover:text-blue-600 text-gray-400 py-1 rounded transition-colors disabled:opacity-40"
        >
          {isMoving ? '…' : `Move to ${next} →`}
        </button>
      )}
    </div>
  )
}
