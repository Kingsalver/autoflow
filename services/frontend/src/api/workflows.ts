import client from './client'

export interface Workflow {
  id: string
  name: string
  description?: string
  triggerType: string
  triggerConfig: Record<string, unknown>
  actionType?: string
  actionConfig?: Record<string, unknown>
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface WorkflowExecution {
  id: string
  workflowId: string
  workflowName?: string
  status: 'SUCCESS' | 'FAILURE' | 'RUNNING' | 'PENDING'
  startedAt: string
  completedAt?: string
  errorMessage?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface CreateWorkflowRequest {
  name: string
  description?: string
  triggerType: string
  triggerConfig: Record<string, unknown>
  actionType?: string
  actionConfig?: Record<string, unknown>
  enabled?: boolean
}

// ── List ────────────────────────────────────────────────────────────────────

export async function listWorkflows(): Promise<Workflow[]> {
  const { data } = await client.get<Workflow[]>('/workflows')
  return data
}

// ── Get ─────────────────────────────────────────────────────────────────────

export async function getWorkflow(id: string): Promise<Workflow> {
  const { data } = await client.get<Workflow>(`/workflows/${id}`)
  return data
}

// ── Create ──────────────────────────────────────────────────────────────────

export async function createWorkflow(
  payload: CreateWorkflowRequest,
): Promise<Workflow> {
  const { data } = await client.post<Workflow>('/workflows', payload)
  return data
}

// ── Update ──────────────────────────────────────────────────────────────────

export async function updateWorkflow(
  id: string,
  payload: Partial<CreateWorkflowRequest>,
): Promise<Workflow> {
  const { data } = await client.put<Workflow>(`/workflows/${id}`, payload)
  return data
}

// ── Delete ──────────────────────────────────────────────────────────────────

export async function deleteWorkflow(id: string): Promise<void> {
  await client.delete(`/workflows/${id}`)
}

// ── Executions ──────────────────────────────────────────────────────────────

export async function getWorkflowExecutions(
  workflowId: string,
  page = 0,
  size = 20,
): Promise<Page<WorkflowExecution>> {
  const { data } = await client.get<Page<WorkflowExecution>>(
    `/workflows/${workflowId}/executions`,
    { params: { page, size } },
  )
  return data
}
