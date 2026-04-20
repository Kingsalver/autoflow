import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import Layout from '../components/Layout'
import {
  listWorkflows,
  createWorkflow,
  updateWorkflow,
  deleteWorkflow,
  type Workflow,
  type CreateWorkflowRequest,
} from '../api/workflows'

const TRIGGER_TYPES = [
  'GITHUB_PUSH',
  'GITHUB_PR',
  'EMAIL_RECEIVED',
  'SCHEDULE',
  'WEBHOOK',
  'MANUAL',
]

export default function Workflows() {
  const queryClient = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null)

  const { data: workflows = [], isLoading } = useQuery({
    queryKey: ['workflows'],
    queryFn: listWorkflows,
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      updateWorkflow(id, { enabled }),
    onSuccess: (updated) => {
      queryClient.setQueryData<Workflow[]>(['workflows'], (prev) =>
        prev ? prev.map((w) => (w.id === updated.id ? updated : w)) : [updated],
      )
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteWorkflow,
    onSuccess: (_, id) => {
      queryClient.setQueryData<Workflow[]>(['workflows'], (prev) =>
        prev ? prev.filter((w) => w.id !== id) : [],
      )
      setConfirmDelete(null)
    },
  })

  return (
    <Layout>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Workflows</h1>
          <p className="text-gray-500 mt-1">
            Automate actions triggered by events from your integrations.
          </p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors flex items-center gap-2"
        >
          <span>+</span> New Workflow
        </button>
      </div>

      {/* Create form modal */}
      {showCreate && (
        <CreateWorkflowModal
          onClose={() => setShowCreate(false)}
          onCreate={(payload) =>
            createWorkflow(payload).then((w) => {
              queryClient.setQueryData<Workflow[]>(['workflows'], (prev) =>
                prev ? [w, ...prev] : [w],
              )
              setShowCreate(false)
            })
          }
        />
      )}

      {/* Delete confirm modal */}
      {confirmDelete && (
        <div className="fixed inset-0 z-40 bg-black/30 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-2xl p-6 max-w-sm w-full">
            <h2 className="font-bold text-gray-900 mb-2">Delete Workflow</h2>
            <p className="text-gray-500 text-sm mb-5">
              This workflow and all its execution history will be permanently
              deleted. This cannot be undone.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setConfirmDelete(null)}
                className="px-4 py-2 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={() => deleteMutation.mutate(confirmDelete)}
                disabled={deleteMutation.isPending}
                className="px-4 py-2 text-sm bg-red-600 hover:bg-red-700 text-white rounded-lg font-medium disabled:opacity-60"
              >
                {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div
              key={i}
              className="h-20 bg-white border border-gray-100 rounded-xl animate-pulse"
            />
          ))}
        </div>
      ) : workflows.length === 0 ? (
        <div className="text-center py-20 text-gray-400">
          <p className="text-4xl mb-3">⚡</p>
          <p className="font-medium text-gray-600">No workflows yet</p>
          <p className="text-sm mt-1">
            Create your first workflow to start automating.
          </p>
          <button
            onClick={() => setShowCreate(true)}
            className="mt-4 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium px-5 py-2 rounded-lg"
          >
            Create Workflow
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {workflows.map((wf) => (
            <WorkflowRow
              key={wf.id}
              workflow={wf}
              onToggle={() =>
                toggleMutation.mutate({ id: wf.id, enabled: !wf.enabled })
              }
              isToggling={
                toggleMutation.isPending &&
                toggleMutation.variables?.id === wf.id
              }
              onDelete={() => setConfirmDelete(wf.id)}
            />
          ))}
        </div>
      )}
    </Layout>
  )
}

interface WorkflowRowProps {
  workflow: Workflow
  onToggle: () => void
  isToggling: boolean
  onDelete: () => void
}

function WorkflowRow({
  workflow,
  onToggle,
  isToggling,
  onDelete,
}: WorkflowRowProps) {
  return (
    <div className="bg-white border border-gray-200 rounded-xl px-5 py-4 flex items-center gap-4 shadow-sm hover:shadow-md transition-shadow">
      {/* Enable toggle */}
      <button
        onClick={onToggle}
        disabled={isToggling}
        className={`relative w-10 h-5 rounded-full transition-colors flex-shrink-0 ${
          workflow.enabled ? 'bg-blue-600' : 'bg-gray-200'
        } disabled:opacity-60`}
        title={workflow.enabled ? 'Disable' : 'Enable'}
      >
        <span
          className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform ${
            workflow.enabled ? 'translate-x-5' : 'translate-x-0.5'
          }`}
        />
      </button>

      {/* Info */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className="font-semibold text-gray-900 truncate">{workflow.name}</p>
          {!workflow.enabled && (
            <span className="text-xs bg-gray-100 text-gray-500 px-2 py-0.5 rounded-full flex-shrink-0">
              Disabled
            </span>
          )}
        </div>
        <p className="text-sm text-gray-400 mt-0.5 truncate">
          {workflow.description || 'No description'}
        </p>
      </div>

      {/* Trigger type */}
      <div className="hidden md:block flex-shrink-0">
        <span className="text-xs bg-slate-100 text-slate-600 px-2.5 py-1 rounded-lg font-mono">
          {workflow.triggerType}
        </span>
      </div>

      {/* Created */}
      <div className="hidden lg:block text-xs text-gray-400 flex-shrink-0">
        {new Date(workflow.createdAt).toLocaleDateString()}
      </div>

      {/* Actions */}
      <button
        onClick={onDelete}
        className="text-xs text-gray-300 hover:text-red-500 transition-colors flex-shrink-0 p-1"
        title="Delete workflow"
      >
        🗑
      </button>
    </div>
  )
}

interface CreateWorkflowModalProps {
  onClose: () => void
  onCreate: (payload: CreateWorkflowRequest) => Promise<void>
}

function CreateWorkflowModal({ onClose, onCreate }: CreateWorkflowModalProps) {
  const [form, setForm] = useState<CreateWorkflowRequest>({
    name: '',
    description: '',
    triggerType: 'GITHUB_PUSH',
    triggerConfig: {},
    enabled: true,
  })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!form.name.trim()) return

    setSubmitting(true)
    setError(null)
    try {
      await onCreate(form)
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Failed to create workflow',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-40 bg-black/30 flex items-center justify-center p-4">
      <div className="bg-white rounded-xl shadow-2xl p-6 max-w-md w-full">
        <div className="flex items-center justify-between mb-5">
          <h2 className="font-bold text-gray-900 text-lg">New Workflow</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-700 text-xl leading-none"
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Name <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="e.g. Notify on GitHub Push"
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Description
            </label>
            <textarea
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              placeholder="What does this workflow do?"
              rows={2}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Trigger Type
            </label>
            <select
              value={form.triggerType}
              onChange={(e) => setForm({ ...form, triggerType: e.target.value })}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
            >
              {TRIGGER_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="enabled"
              checked={form.enabled}
              onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            />
            <label htmlFor="enabled" className="text-sm text-gray-700">
              Enable immediately
            </label>
          </div>

          {error && (
            <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">
              {error}
            </p>
          )}

          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-2 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !form.name.trim()}
              className="flex-1 py-2 text-sm bg-blue-600 hover:bg-blue-700 disabled:bg-gray-200 disabled:text-gray-400 text-white font-medium rounded-lg transition-colors"
            >
              {submitting ? 'Creating…' : 'Create Workflow'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
