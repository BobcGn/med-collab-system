const resolveBrowserHost = () => window.location.hostname || 'localhost'
const resolveHttpProtocol = () => window.location.protocol || 'http:'
const resolveWsProtocol = () => (resolveHttpProtocol() === 'https:' ? 'wss:' : 'ws:')

const buildOrigin = (protocol, port) => `${protocol}//${resolveBrowserHost()}:${port}`

const normalizeConfiguredOrigin = (origin, fallbackProtocol) => {
  if (!origin) {
    return null
  }

  if (origin.startsWith('http://') || origin.startsWith('https://') || origin.startsWith('ws://') || origin.startsWith('wss://')) {
    return origin
  }

  return `${fallbackProtocol}//${origin}`
}

export const getGatewayOrigin = () => {
  return normalizeConfiguredOrigin(import.meta.env.VITE_GATEWAY_ORIGIN, resolveHttpProtocol())
    || buildOrigin(resolveHttpProtocol(), import.meta.env.VITE_GATEWAY_PORT || 8088)
}

export const getAuthWsOrigin = () => {
  return normalizeConfiguredOrigin(import.meta.env.VITE_AUTH_WS_ORIGIN, resolveWsProtocol())
    || buildOrigin(resolveWsProtocol(), import.meta.env.VITE_AUTH_WS_PORT || 8081)
}

export const getMetricWsOrigin = () => {
  return normalizeConfiguredOrigin(import.meta.env.VITE_METRIC_WS_ORIGIN, resolveWsProtocol())
    || buildOrigin(resolveWsProtocol(), import.meta.env.VITE_METRIC_WS_PORT || 8083)
}
