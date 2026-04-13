import { authStore } from './auth'
import { getAuthWsOrigin, getMetricWsOrigin } from './runtimeConfig'

const buildWsUrl = (origin, path) => {
  const token = authStore.getToken()
  const params = new URLSearchParams()

  if (token) {
    params.set('token', token)
  }

  const query = params.toString()
  return `${origin}${path}${query ? `?${query}` : ''}`
}

export const connectNotificationSocket = () => {
  return new WebSocket(buildWsUrl(getAuthWsOrigin(), '/ws/notifications'))
}

export const connectMetricAiSocket = () => {
  return new WebSocket(buildWsUrl(getMetricWsOrigin(), '/ws/ai-agent'))
}
