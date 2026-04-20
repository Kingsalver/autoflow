import axios from 'axios'

const BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081/api/v1'

const client = axios.create({
  baseURL: BASE_URL,
  withCredentials: true, // required — auth uses HttpOnly cookies
  headers: {
    'Content-Type': 'application/json',
  },
})

// Redirect to /login on any 401 response
client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Avoid redirect loop if already on login page
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

export default client
