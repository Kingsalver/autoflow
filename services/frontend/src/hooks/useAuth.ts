import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { getMe, logout, type User } from '../api/auth'

export const AUTH_QUERY_KEY = ['auth', 'me'] as const

export function useAuth() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const query = useQuery<User, Error>({
    queryKey: AUTH_QUERY_KEY,
    queryFn: getMe,
    retry: false,          // don't retry 401s
    staleTime: 5 * 60_000, // treat user data fresh for 5 min
  })

  async function handleLogout() {
    try {
      await logout()
    } catch {
      // swallow — cookie may already be gone
    } finally {
      queryClient.clear()
      navigate('/login', { replace: true })
    }
  }

  return {
    user: query.data ?? null,
    isLoading: query.isLoading,
    isError: query.isError,
    isAuthenticated: query.isSuccess && !!query.data,
    logout: handleLogout,
  }
}
