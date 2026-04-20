interface Props {
  title: string
  value: string | number
  subtitle?: string
  icon?: string
  accent?: 'blue' | 'green' | 'yellow' | 'purple' | 'gray'
}

const ACCENT_CLASSES: Record<NonNullable<Props['accent']>, string> = {
  blue:   'bg-blue-50 text-blue-600',
  green:  'bg-green-50 text-green-600',
  yellow: 'bg-yellow-50 text-yellow-600',
  purple: 'bg-purple-50 text-purple-600',
  gray:   'bg-gray-100 text-gray-600',
}

export default function StatCard({
  title,
  value,
  subtitle,
  icon,
  accent = 'blue',
}: Props) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-5 flex items-start gap-4 shadow-sm">
      {icon && (
        <div
          className={`w-10 h-10 rounded-lg flex items-center justify-center text-lg flex-shrink-0 ${ACCENT_CLASSES[accent]}`}
        >
          {icon}
        </div>
      )}
      <div className="min-w-0">
        <p className="text-sm font-medium text-gray-500 truncate">{title}</p>
        <p className="mt-0.5 text-2xl font-bold text-gray-900 leading-none">
          {value}
        </p>
        {subtitle && (
          <p className="mt-1 text-xs text-gray-400 truncate">{subtitle}</p>
        )}
      </div>
    </div>
  )
}
