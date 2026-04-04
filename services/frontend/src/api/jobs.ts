/**
 * Jobs & Applications API — mock data stubs.
 *
 * The real data layer (Postgres via worker proxy) will be wired in a future
 * phase. All functions here return realistic mock data so the UI is fully
 * exercisable without a backend.
 */

export type ApplicationStatus =
  | 'saved'
  | 'applied'
  | 'interview'
  | 'offer'
  | 'rejected'

export interface JobListing {
  id: string
  company: string
  role: string
  location: string
  source: 'LinkedIn' | 'GitHub Jobs' | 'Indeed' | 'Greenhouse' | 'Lever'
  url: string
  salary?: string
  dateAdded: string
  tags: string[]
}

export interface Application {
  id: string
  jobId: string
  company: string
  role: string
  location: string
  status: ApplicationStatus
  appliedDate?: string
  notes: string
  salary?: string
  contactName?: string
  contactEmail?: string
  createdAt: string
  updatedAt: string
}

// ── Mock data ────────────────────────────────────────────────────────────────

const MOCK_JOBS: JobListing[] = [
  {
    id: 'job-1',
    company: 'Stripe',
    role: 'Senior Software Engineer',
    location: 'San Francisco, CA',
    source: 'Greenhouse',
    url: 'https://stripe.com/jobs',
    salary: '$180k–$220k',
    dateAdded: '2026-04-01',
    tags: ['TypeScript', 'Go', 'Distributed Systems'],
  },
  {
    id: 'job-2',
    company: 'Vercel',
    role: 'Staff Engineer — Runtime',
    location: 'Remote',
    source: 'Lever',
    url: 'https://vercel.com/careers',
    salary: '$200k–$250k',
    dateAdded: '2026-04-01',
    tags: ['Rust', 'Node.js', 'Edge Computing'],
  },
  {
    id: 'job-3',
    company: 'Linear',
    role: 'Full-Stack Engineer',
    location: 'Remote',
    source: 'LinkedIn',
    url: 'https://linear.app/careers',
    salary: '$160k–$200k',
    dateAdded: '2026-04-02',
    tags: ['React', 'TypeScript', 'GraphQL'],
  },
  {
    id: 'job-4',
    company: 'PlanetScale',
    role: 'Backend Engineer',
    location: 'Remote',
    source: 'GitHub Jobs',
    url: 'https://planetscale.com/careers',
    salary: '$150k–$185k',
    dateAdded: '2026-04-02',
    tags: ['Go', 'MySQL', 'Kubernetes'],
  },
  {
    id: 'job-5',
    company: 'Figma',
    role: 'Software Engineer — Infrastructure',
    location: 'New York, NY',
    source: 'Greenhouse',
    url: 'https://figma.com/careers',
    salary: '$175k–$215k',
    dateAdded: '2026-04-02',
    tags: ['C++', 'TypeScript', 'WebAssembly'],
  },
  {
    id: 'job-6',
    company: 'Notion',
    role: 'Product Engineer',
    location: 'San Francisco, CA',
    source: 'Lever',
    url: 'https://notion.so/careers',
    salary: '$160k–$200k',
    dateAdded: '2026-04-03',
    tags: ['React', 'TypeScript', 'Postgres'],
  },
  {
    id: 'job-7',
    company: 'Retool',
    role: 'Senior Frontend Engineer',
    location: 'San Francisco, CA',
    source: 'LinkedIn',
    url: 'https://retool.com/careers',
    salary: '$165k–$205k',
    dateAdded: '2026-04-03',
    tags: ['React', 'TypeScript', 'Redux'],
  },
  {
    id: 'job-8',
    company: 'Turso',
    role: 'Developer Advocate',
    location: 'Remote',
    source: 'GitHub Jobs',
    url: 'https://turso.tech/careers',
    salary: '$130k–$160k',
    dateAdded: '2026-04-03',
    tags: ['SQLite', 'Rust', 'Edge'],
  },
  {
    id: 'job-9',
    company: 'Railway',
    role: 'Platform Engineer',
    location: 'Remote',
    source: 'LinkedIn',
    url: 'https://railway.app/careers',
    salary: '$140k–$175k',
    dateAdded: '2026-04-03',
    tags: ['Rust', 'Docker', 'Kubernetes'],
  },
  {
    id: 'job-10',
    company: 'Supabase',
    role: 'Software Engineer — Auth',
    location: 'Remote',
    source: 'GitHub Jobs',
    url: 'https://supabase.com/careers',
    salary: '$145k–$180k',
    dateAdded: '2026-04-04',
    tags: ['Go', 'PostgreSQL', 'Security'],
  },
  {
    id: 'job-11',
    company: 'Fly.io',
    role: 'Senior Backend Engineer',
    location: 'Remote',
    source: 'LinkedIn',
    url: 'https://fly.io/jobs',
    salary: '$160k–$200k',
    dateAdded: '2026-04-04',
    tags: ['Go', 'Elixir', 'Distributed Systems'],
  },
  {
    id: 'job-12',
    company: 'Clerk',
    role: 'Software Engineer — SDK',
    location: 'Remote',
    source: 'Lever',
    url: 'https://clerk.com/careers',
    salary: '$140k–$175k',
    dateAdded: '2026-04-04',
    tags: ['TypeScript', 'React', 'Auth'],
  },
]

const MOCK_APPLICATIONS: Application[] = [
  {
    id: 'app-1',
    jobId: 'job-1',
    company: 'Stripe',
    role: 'Senior Software Engineer',
    location: 'San Francisco, CA',
    status: 'interview',
    appliedDate: '2026-03-20',
    notes: 'Had a great first screen with the hiring manager. Technical loop scheduled for next week.',
    salary: '$180k–$220k',
    contactName: 'Alex Kim',
    contactEmail: 'alex@stripe.com',
    createdAt: '2026-03-15',
    updatedAt: '2026-03-28',
  },
  {
    id: 'app-2',
    jobId: 'job-3',
    company: 'Linear',
    role: 'Full-Stack Engineer',
    location: 'Remote',
    status: 'applied',
    appliedDate: '2026-03-25',
    notes: 'Applied through their website. Strong product-market fit with my background.',
    salary: '$160k–$200k',
    createdAt: '2026-03-25',
    updatedAt: '2026-03-25',
  },
  {
    id: 'app-3',
    jobId: 'job-5',
    company: 'Figma',
    role: 'Software Engineer — Infrastructure',
    location: 'New York, NY',
    status: 'saved',
    notes: 'Interesting infra role. Research the team more before applying.',
    salary: '$175k–$215k',
    createdAt: '2026-04-02',
    updatedAt: '2026-04-02',
  },
  {
    id: 'app-4',
    jobId: 'job-2',
    company: 'Vercel',
    role: 'Staff Engineer — Runtime',
    location: 'Remote',
    status: 'offer',
    appliedDate: '2026-03-10',
    notes: 'Received offer: $230k base + equity. Deadline April 10.',
    salary: '$200k–$250k',
    contactName: 'Jordan Lee',
    createdAt: '2026-03-10',
    updatedAt: '2026-04-01',
  },
  {
    id: 'app-5',
    jobId: 'job-6',
    company: 'Notion',
    role: 'Product Engineer',
    location: 'San Francisco, CA',
    status: 'rejected',
    appliedDate: '2026-03-05',
    notes: 'Got a rejection after the take-home. Feedback: looking for more product experience.',
    salary: '$160k–$200k',
    createdAt: '2026-03-05',
    updatedAt: '2026-03-18',
  },
  {
    id: 'app-6',
    jobId: 'job-10',
    company: 'Supabase',
    role: 'Software Engineer — Auth',
    location: 'Remote',
    status: 'saved',
    notes: 'Great overlap with my current auth work on Autoflow.',
    salary: '$145k–$180k',
    createdAt: '2026-04-04',
    updatedAt: '2026-04-04',
  },
]

// ── API stubs ────────────────────────────────────────────────────────────────

export interface JobsPage {
  items: JobListing[]
  total: number
  page: number
  pageSize: number
}

export async function listJobs(
  page = 0,
  pageSize = 20,
  search = '',
  location = '',
): Promise<JobsPage> {
  await new Promise((r) => setTimeout(r, 120)) // simulate network latency

  let filtered = MOCK_JOBS

  if (search.trim()) {
    const q = search.toLowerCase()
    filtered = filtered.filter(
      (j) =>
        j.company.toLowerCase().includes(q) ||
        j.role.toLowerCase().includes(q) ||
        j.tags.some((t) => t.toLowerCase().includes(q)),
    )
  }

  if (location.trim()) {
    const l = location.toLowerCase()
    filtered = filtered.filter((j) => j.location.toLowerCase().includes(l))
  }

  const start = page * pageSize
  return {
    items: filtered.slice(start, start + pageSize),
    total: filtered.length,
    page,
    pageSize,
  }
}

export async function listApplications(): Promise<Application[]> {
  await new Promise((r) => setTimeout(r, 80))
  return [...MOCK_APPLICATIONS]
}

// In-memory store for demo mutations
let applicationStore: Application[] = [...MOCK_APPLICATIONS]

export async function saveJobAsApplication(
  job: JobListing,
): Promise<Application> {
  await new Promise((r) => setTimeout(r, 100))

  // Check if already saved
  const existing = applicationStore.find((a) => a.jobId === job.id)
  if (existing) return existing

  const newApp: Application = {
    id: `app-${Date.now()}`,
    jobId: job.id,
    company: job.company,
    role: job.role,
    location: job.location,
    status: 'saved',
    notes: '',
    salary: job.salary,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }
  applicationStore = [newApp, ...applicationStore]
  return newApp
}

export async function updateApplicationStatus(
  id: string,
  status: ApplicationStatus,
): Promise<Application> {
  await new Promise((r) => setTimeout(r, 80))
  applicationStore = applicationStore.map((a) =>
    a.id === id
      ? {
          ...a,
          status,
          appliedDate:
            status === 'applied' && !a.appliedDate
              ? new Date().toISOString().split('T')[0]
              : a.appliedDate,
          updatedAt: new Date().toISOString(),
        }
      : a,
  )
  return applicationStore.find((a) => a.id === id)!
}

export async function updateApplicationNotes(
  id: string,
  notes: string,
): Promise<Application> {
  await new Promise((r) => setTimeout(r, 80))
  applicationStore = applicationStore.map((a) =>
    a.id === id ? { ...a, notes, updatedAt: new Date().toISOString() } : a,
  )
  return applicationStore.find((a) => a.id === id)!
}

export async function getApplications(): Promise<Application[]> {
  await new Promise((r) => setTimeout(r, 80))
  return [...applicationStore]
}
