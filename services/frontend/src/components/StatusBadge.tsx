import type { ApplicationStatus } from '../api/jobs'

const CONFIG: Record<
  ApplicationStatus,
  { label: string; classes: string }
> = {
  saved:     { label: 'Saved',     classes: 'bg-gray-100 text-gray-700' },
  applied:   { label: 'Applied',   classes: 'bg-blue-100 text-blue-700' },
  interview: { label: 'Interview', classes: 'bg-yellow-100 text-yellow-700' },
  offer:     { label: 'Offer',     classes: 'bg-green-100 text-green-700' },
  rejected:  { label: 'Rejected',  classes: 'bg-red-100 text-red-700' },
}

interface Props {
  status: ApplicationStatus
  size?: 'sm' | 'md'
}

export default function StatusBadge({ status, size = 'sm' }: Props) {
  const { label, classes } = CONFIG[status]
  const sizeClasses = size === 'sm' ? 'text-xs px-2 py-0.5' : 'text-sm px-3 py-1'

  return (
    <span
      className={`inline-flex items-center font-medium rounded-full ${classes} ${sizeClasses}`}
    >
      {label}
    </span>
  )
}
