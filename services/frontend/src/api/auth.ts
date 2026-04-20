import client from './client'

export interface User {
  id: string
  email: string
  displayName: string
}

/**
 * Returns the authenticated user or throws on 401.
 */
export async function getMe(): Promise<User> {
  const { data } = await client.get<User>('/auth/me')
  return data
}

/**
 * Clears the auth cookies server-side.
 */
export async function logout(): Promise<void> {
  await client.post('/auth/logout')
}
