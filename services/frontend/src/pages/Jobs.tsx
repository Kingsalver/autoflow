import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import Layout from '../components/Layout'
import { listJobs, saveJobAsApplication, type JobListing } from '../api/jobs'

const PAGE_SIZE = 20

export default function Jobs() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [location, setLocation] = useState('')
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set())
  const [toast, setToast] = useState<string | null>(null)

  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['jobs', page, search, location],
    queryFn: () => listJobs(page, PAGE_SIZE, search, location),
    placeholderData: (prev) => prev,
  })

  const saveMutation = useMutation({
    mutationFn: saveJobAsApplication,
    onSuccess: (_, job) => {
      setSavedIds((prev) => new Set([...prev, job.id]))
      queryClient.invalidateQueries({ queryKey: ['applications'] })
      showToast(`Saved "${job.role}" at ${job.company}`)
    },
  })

  function showToast(message: string) {
    setToast(message)
    setTimeout(() => setToast(null), 3000)
  }

  const jobs = data?.items ?? []
  const total = data?.total ?? 0
  const totalPages = Math.ceil(total / PAGE_SIZE)

  function handleSearch(e: React.FormEvent) {
    e.preventDefault()
    setPage(0)
  }

  return (
    <Layout>
      {/* Toast */}
      {toast && (
        <div className="fixed top-5 right-5 z-50 bg-green-600 text-white text-sm px-4 py-2.5 rounded-lg shadow-lg animate-fade-in">
          {toast}
        </div>
      )}

      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Browse Jobs</h1>
        <p className="text-gray-500 mt-1">
          Discover opportunities sourced from multiple job boards.
          <span className="ml-2 text-xs bg-yellow-100 text-yellow-700 px-2 py-0.5 rounded-full font-medium">
            Mock data — real sync coming in Phase 4
          </span>
        </p>
      </div>

      {/* Filter bar */}
      <form
        onSubmit={handleSearch}
        className="flex flex-wrap gap-3 mb-6 bg-white border border-gray-200 rounded-xl p-4 shadow-sm"
      >
        <div className="flex-1 min-w-48">
          <input
            type="text"
            placeholder="Search role, company, or skill…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div className="w-44">
          <input
            type="text"
            placeholder="Location or Remote"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <button
          type="submit"
          className="bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors"
        >
          Search
        </button>
        {(search || location) && (
          <button
            type="button"
            onClick={() => { setSearch(''); setLocation(''); setPage(0) }}
            className="text-sm text-gray-500 hover:text-gray-700 px-3 py-2"
          >
            Clear
          </button>
        )}
      </form>

      {/* Results info */}
      <p className="text-sm text-gray-400 mb-3">
        {isLoading ? 'Loading…' : `${total} job${total !== 1 ? 's' : ''} found`}
      </p>

      {/* Table */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-100 bg-gray-50">
              <th className="text-left px-5 py-3 font-semibold text-gray-500 text-xs uppercase tracking-wide">
                Company
              </th>
              <th className="text-left px-5 py-3 font-semibold text-gray-500 text-xs uppercase tracking-wide">
                Role
              </th>
              <th className="text-left px-5 py-3 font-semibold text-gray-500 text-xs uppercase tracking-wide hidden md:table-cell">
                Location
              </th>
              <th className="text-left px-5 py-3 font-semibold text-gray-500 text-xs uppercase tracking-wide hidden lg:table-cell">
                Source
              </th>
              <th className="text-left px-5 py-3 font-semibold text-gray-500 text-xs uppercase tracking-wide hidden lg:table-cell">
                Added
              </th>
              <th className="text-right px-5 py-3 font-semibold text-gray-500 text-xs uppercase tracking-wide">
                Action
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {isLoading
              ? Array.from({ length: 8 }).map((_, i) => (
                  <tr key={i}>
                    {Array.from({ length: 6 }).map((_, j) => (
                      <td key={j} className="px-5 py-4">
                        <div className="h-4 bg-gray-100 rounded animate-pulse" />
                      </td>
                    ))}
                  </tr>
                ))
              : jobs.map((job) => (
                  <JobRow
                    key={job.id}
                    job={job}
                    isSaved={savedIds.has(job.id)}
                    onSave={() => saveMutation.mutate(job)}
                    isSaving={
                      saveMutation.isPending &&
                      saveMutation.variables?.id === job.id
                    }
                  />
                ))}
          </tbody>
        </table>

        {!isLoading && jobs.length === 0 && (
          <div className="text-center py-16 text-gray-400">
            <p className="text-3xl mb-2">🔍</p>
            <p>No jobs match your search. Try different filters.</p>
          </div>
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <p className="text-sm text-gray-400">
            Page {page + 1} of {totalPages}
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg disabled:opacity-40 hover:bg-gray-50 transition-colors"
            >
              ← Previous
            </button>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg disabled:opacity-40 hover:bg-gray-50 transition-colors"
            >
              Next →
            </button>
          </div>
        </div>
      )}
    </Layout>
  )
}

interface JobRowProps {
  job: JobListing
  isSaved: boolean
  onSave: () => void
  isSaving: boolean
}

function JobRow({ job, isSaved, onSave, isSaving }: JobRowProps) {
  return (
    <tr className="hover:bg-gray-50 transition-colors">
      <td className="px-5 py-4">
        <span className="font-semibold text-gray-900">{job.company}</span>
      </td>
      <td className="px-5 py-4">
        <div>
          <span className="text-gray-800">{job.role}</span>
          {job.salary && (
            <span className="ml-2 text-xs text-gray-400">{job.salary}</span>
          )}
          <div className="flex flex-wrap gap-1 mt-1">
            {job.tags.slice(0, 3).map((tag) => (
              <span
                key={tag}
                className="text-xs bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded"
              >
                {tag}
              </span>
            ))}
          </div>
        </div>
      </td>
      <td className="px-5 py-4 hidden md:table-cell text-gray-500">
        {job.location}
      </td>
      <td className="px-5 py-4 hidden lg:table-cell">
        <SourceBadge source={job.source} />
      </td>
      <td className="px-5 py-4 hidden lg:table-cell text-gray-400 text-xs">
        {job.dateAdded}
      </td>
      <td className="px-5 py-4 text-right">
        {isSaved ? (
          <span className="text-xs text-green-600 font-medium">✓ Saved</span>
        ) : (
          <button
            onClick={onSave}
            disabled={isSaving}
            className="text-xs bg-blue-600 hover:bg-blue-700 text-white font-medium px-3 py-1.5 rounded-lg transition-colors disabled:opacity-60"
          >
            {isSaving ? '…' : 'Save'}
          </button>
        )}
      </td>
    </tr>
  )
}

const SOURCE_COLORS: Record<JobListing['source'], string> = {
  LinkedIn:    'bg-blue-100 text-blue-700',
  'GitHub Jobs': 'bg-gray-900 text-white',
  Indeed:      'bg-blue-50 text-blue-800',
  Greenhouse:  'bg-green-100 text-green-700',
  Lever:       'bg-purple-100 text-purple-700',
}

function SourceBadge({ source }: { source: JobListing['source'] }) {
  return (
    <span
      className={`text-xs px-2 py-0.5 rounded-full font-medium ${SOURCE_COLORS[source]}`}
    >
      {source}
    </span>
  )
}
