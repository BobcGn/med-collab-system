import {
  createContext,
  startTransition,
  useContext,
  useDeferredValue,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
} from 'react'

import './app.css'
import { authApi, metricApi, patientApi } from './lib/api'
import { authStore } from './lib/auth'
import {
  getUnreadNotificationCount,
  loadSystemNotifications,
  markSystemNotificationsRead,
  mergeSystemNotification,
  replaceSystemNotifications,
  systemNotificationEvents,
} from './lib/systemNotifications'
import { connectMetricAiSocket, connectNotificationSocket } from './lib/ws'

const RouterContext = createContext(null)

const LOCATION_EVENT = 'medical-collab-popstate'

const ADMIN_ROLE = 'admin'
const PUBLIC_PATHS = new Set(['/login', '/register'])

const TOP_NAV_ITEMS = [
  { path: '/notifications', label: '消息通知' },
  { path: '/help', label: '帮助中心' },
  { path: '/profile', label: '个人中心' },
  { path: '/announcements', label: '系统公告' },
]

const FAQ_ITEMS = [
  {
    title: '如何开始创建患者档案？',
    content: '进入患者管理页面后点击“新增患者”，填写患者基础信息、就诊信息和病史摘要后提交即可。',
  },
  {
    title: 'AI 影像分析支持哪些类型？',
    content: '当前界面支持 X 射线、CT、MRI、超声和其他医学影像类型，上传后可直接与 AI 进行分析会话。',
  },
  {
    title: '系统公告和消息通知有什么区别？',
    content: '系统公告用于发布平台级信息，消息通知会实时同步公告状态并记录未读数量。',
  },
]

const HELP_RESOURCES = [
  {
    title: '患者协作流程',
    content: '从建档、影像分析、结果确认到报表落库，所有操作都围绕患者维度组织。',
  },
  {
    title: '账号权限说明',
    content: '管理员负责用户和医院科室管理，医生和护士主要使用患者、影像和报表相关能力。',
  },
  {
    title: '异常排查建议',
    content: '如果遇到接口 401 或 WebSocket 断开，先重新登录并确认网关与 metric 服务端口配置正确。',
  },
]

let cachedLocationSnapshot = null
let cachedAuthSnapshot = null

function normalizePath(pathname) {
  if (!pathname || pathname === '/') {
    return '/'
  }

  const trimmed = pathname.endsWith('/') && pathname.length > 1
    ? pathname.slice(0, -1)
    : pathname

  return trimmed || '/'
}

function getLocationSnapshot() {
  const nextSnapshot = {
    pathname: normalizePath(window.location.pathname),
    search: window.location.search,
    hash: window.location.hash,
  }

  if (
    cachedLocationSnapshot
    && cachedLocationSnapshot.pathname === nextSnapshot.pathname
    && cachedLocationSnapshot.search === nextSnapshot.search
    && cachedLocationSnapshot.hash === nextSnapshot.hash
  ) {
    return cachedLocationSnapshot
  }

  cachedLocationSnapshot = nextSnapshot
  return cachedLocationSnapshot
}

function subscribeLocation(listener) {
  const handleChange = () => listener()

  window.addEventListener('popstate', handleChange)
  window.addEventListener(LOCATION_EVENT, handleChange)

  return () => {
    window.removeEventListener('popstate', handleChange)
    window.removeEventListener(LOCATION_EVENT, handleChange)
  }
}

function RouterProvider({ children }) {
  const location = useSyncExternalStore(
    subscribeLocation,
    getLocationSnapshot,
    getLocationSnapshot,
  )

  const navigate = (to, options = {}) => {
    const nextUrl = new URL(to, window.location.origin)
    const nextPath = `${nextUrl.pathname}${nextUrl.search}${nextUrl.hash}`
    const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`

    if (nextPath === currentPath) {
      return
    }

    startTransition(() => {
      if (options.replace) {
        window.history.replaceState({}, '', nextPath)
      } else {
        window.history.pushState({}, '', nextPath)
      }
      window.dispatchEvent(new Event(LOCATION_EVENT))
    })
  }

  const goBack = () => {
    window.history.back()
  }

  return (
    <RouterContext.Provider value={{ location, navigate, goBack }}>
      {children}
    </RouterContext.Provider>
  )
}

function useRouter() {
  const context = useContext(RouterContext)

  if (!context) {
    throw new Error('Router context is unavailable.')
  }

  return context
}

function getAuthSnapshot() {
  const rawUser = window.localStorage.getItem('currentUser')
  const token = window.localStorage.getItem('token')

  if (
    cachedAuthSnapshot
    && cachedAuthSnapshot.rawUser === rawUser
    && cachedAuthSnapshot.token === token
  ) {
    return cachedAuthSnapshot.value
  }

  let user = null
  if (rawUser) {
    try {
      user = JSON.parse(rawUser)
    } catch (_error) {
      user = null
    }
  }

  cachedAuthSnapshot = {
    rawUser,
    token,
    value: {
      user,
      token,
      isAuthenticated: Boolean(token),
    },
  }

  return cachedAuthSnapshot.value
}

function subscribeAuth(listener) {
  return authStore.subscribe(listener)
}

function useAuthState() {
  return useSyncExternalStore(subscribeAuth, getAuthSnapshot, getAuthSnapshot)
}

function roleLabel(role) {
  const labels = {
    admin: '管理员',
    doctor: '医生',
    nurse: '护士',
    receptionist: '前台',
  }

  return labels[role] || role || '未设置'
}

function formatGender(gender) {
  const labels = { M: '男', F: '女' }
  return labels[gender] || gender || '-'
}

function clampNumber(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function roundSvgValue(value) {
  return Number(value.toFixed(2))
}

function svgPoint(x, y) {
  return `${roundSvgValue(x)},${roundSvgValue(y)}`
}

function normalizeContourPoints(contour) {
  if (!Array.isArray(contour)) {
    return []
  }

  return contour
    .map((point) => {
      const x = Number(point?.xPercent)
      const y = Number(point?.yPercent)
      if (!Number.isFinite(x) || !Number.isFinite(y)) {
        return null
      }
      return {
        x: clampNumber(x, 0, 100),
        y: clampNumber(y, 0, 100),
      }
    })
    .filter(Boolean)
}

function buildSmoothClosedPath(points) {
  if (!points.length) {
    return ''
  }
  if (points.length < 3) {
    return `M ${points.map((point) => svgPoint(point.x, point.y)).join(' L ')} Z`
  }

  const closedPoints = points[0].x === points[points.length - 1].x && points[0].y === points[points.length - 1].y
    ? points.slice(0, -1)
    : points

  if (closedPoints.length < 3) {
    return `M ${closedPoints.map((point) => svgPoint(point.x, point.y)).join(' L ')} Z`
  }

  const midPoint = (first, second) => ({
    x: (first.x + second.x) / 2,
    y: (first.y + second.y) / 2,
  })

  const firstMid = midPoint(closedPoints[0], closedPoints[1])
  const commands = [`M ${svgPoint(firstMid.x, firstMid.y)}`]

  for (let index = 0; index < closedPoints.length; index += 1) {
    const current = closedPoints[index]
    const next = closedPoints[(index + 1) % closedPoints.length]
    const nextMid = midPoint(current, next)
    commands.push(`Q ${svgPoint(current.x, current.y)} ${svgPoint(nextMid.x, nextMid.y)}`)
  }

  commands.push('Z')
  return commands.join(' ')
}

function buildEllipsePath(left, top, width, height) {
  const right = left + width
  const bottom = top + height
  const centerX = left + (width / 2)
  const centerY = top + (height / 2)
  const controlX = width * 0.55
  const controlY = height * 0.55

  return [
    `M ${svgPoint(centerX, top)}`,
    `C ${svgPoint(centerX + controlX, top)} ${svgPoint(right, centerY - controlY)} ${svgPoint(right, centerY)}`,
    `C ${svgPoint(right, centerY + controlY)} ${svgPoint(centerX + controlX, bottom)} ${svgPoint(centerX, bottom)}`,
    `C ${svgPoint(centerX - controlX, bottom)} ${svgPoint(left, centerY + controlY)} ${svgPoint(left, centerY)}`,
    `C ${svgPoint(left, centerY - controlY)} ${svgPoint(centerX - controlX, top)} ${svgPoint(centerX, top)}`,
    'Z',
  ].join(' ')
}

function buildPillPath(left, top, width, height) {
  const right = left + width
  const bottom = top + height
  const radius = Math.min(width, height) / 2

  return [
    `M ${svgPoint(left + radius, top)}`,
    `L ${svgPoint(right - radius, top)}`,
    `Q ${svgPoint(right, top)} ${svgPoint(right, top + radius)}`,
    `L ${svgPoint(right, bottom - radius)}`,
    `Q ${svgPoint(right, bottom)} ${svgPoint(right - radius, bottom)}`,
    `L ${svgPoint(left + radius, bottom)}`,
    `Q ${svgPoint(left, bottom)} ${svgPoint(left, bottom - radius)}`,
    `L ${svgPoint(left, top + radius)}`,
    `Q ${svgPoint(left, top)} ${svgPoint(left + radius, top)}`,
    'Z',
  ].join(' ')
}

function buildBlobPath(left, top, width, height, variant = 0) {
  const right = left + width
  const bottom = top + height
  const centerX = left + (width / 2)
  const centerY = top + (height / 2)
  const wobbleX = width * (0.07 + ((variant % 3) * 0.015))
  const wobbleY = height * (0.08 + ((variant % 2) * 0.02))

  return [
    `M ${svgPoint(centerX, top + (height * 0.05))}`,
    `C ${svgPoint(right - (width * 0.12), top - wobbleY)} ${svgPoint(right + wobbleX, top + (height * 0.2))} ${svgPoint(right - (width * 0.04), centerY - (height * 0.08))}`,
    `C ${svgPoint(right + wobbleX, centerY + (height * 0.1))} ${svgPoint(right - (width * 0.08), bottom + wobbleY)} ${svgPoint(centerX + (width * 0.08), bottom - (height * 0.04))}`,
    `C ${svgPoint(centerX - (width * 0.12), bottom + wobbleY)} ${svgPoint(left - wobbleX, bottom - (height * 0.12))} ${svgPoint(left + (width * 0.08), centerY + (height * 0.12))}`,
    `C ${svgPoint(left - wobbleX, centerY - (height * 0.1))} ${svgPoint(left + (width * 0.1), top - wobbleY)} ${svgPoint(centerX, top + (height * 0.05))}`,
    'Z',
  ].join(' ')
}

function insetBounds(left, top, width, height, insetRatio = 0.12) {
  const insetX = width * insetRatio
  const insetY = height * insetRatio
  return {
    left: left + insetX,
    top: top + insetY,
    width: Math.max(width - (insetX * 2), width * 0.4),
    height: Math.max(height - (insetY * 2), height * 0.4),
  }
}

function buildRegionPath(shape, left, top, width, height, variant = 0) {
  if (shape === 'ellipse') {
    return buildEllipsePath(left, top, width, height)
  }
  if (shape === 'pill') {
    return buildPillPath(left, top, width, height)
  }
  return buildBlobPath(left, top, width, height, variant)
}

function buildRegionVisual(region, index) {
  const left = clampNumber(region.boundingBox.leftPercent, 1, 92)
  const top = clampNumber(region.boundingBox.topPercent, 1, 92)
  const width = clampNumber(region.boundingBox.widthPercent, 6, 42)
  const height = clampNumber(region.boundingBox.heightPercent, 6, 42)
  const centerX = left + (width / 2)
  const centerY = top + (height / 2)
  const innerBounds = insetBounds(left, top, width, height, 0.16)
  const shape = typeof region.shape === 'string' ? region.shape.toLowerCase() : 'blob'
  const preferRight = centerX <= 58
  const labelWidth = clampNumber(16 + Math.max(region.annotationTitle?.length || 0, `${region.colorName}${region.label}`.length) * 0.7, 18, 26)
  const labelHeight = 10.5
  const labelX = clampNumber(
    preferRight ? left + width + 4.5 : left - labelWidth - 4.5,
    2.5,
    100 - labelWidth - 2.5,
  )
  const labelY = clampNumber(centerY - (labelHeight / 2) + ((index % 3) - 1) * 2.2, 4, 100 - labelHeight - 3)
  const calloutEdgeX = preferRight ? labelX : labelX + labelWidth
  const calloutAnchorX = preferRight ? left + width - 0.3 : left + 0.3
  const calloutAnchorY = clampNumber(centerY, 4, 96)
  const bendX = preferRight ? calloutAnchorX + 3.4 : calloutAnchorX - 3.4
  const bendY = clampNumber(calloutAnchorY + ((index % 2 === 0) ? -2 : 2), 4, 96)
  const contour = normalizeContourPoints(region.contour)
  const hasContour = contour.length >= 4
  const outerPath = contour.length >= 4
    ? buildSmoothClosedPath(contour)
    : buildRegionPath(shape, left, top, width, height, index)
  const innerContour = contour.length >= 4
    ? contour.map((point) => ({
      x: centerX + ((point.x - centerX) * 0.84),
      y: centerY + ((point.y - centerY) * 0.84),
    }))
    : []

  return {
    centerX,
    centerY,
    outerPath,
    innerPath: innerContour.length >= 4
      ? buildSmoothClosedPath(innerContour)
      : buildRegionPath(shape, innerBounds.left, innerBounds.top, innerBounds.width, innerBounds.height, index + 1),
    rotationDegrees: hasContour ? 0 : (Number.isFinite(Number(region.rotationDegrees)) ? Number(region.rotationDegrees) : 0),
    label: {
      x: labelX,
      y: labelY,
      width: labelWidth,
      height: labelHeight,
      linePoints: [
        svgPoint(calloutAnchorX, calloutAnchorY),
        svgPoint(bendX, bendY),
        svgPoint(calloutEdgeX, labelY + (labelHeight / 2)),
      ].join(' '),
      title: `${region.colorName}${region.label}`,
      subtitle: region.annotationTitle || region.location || '高亮标注',
    },
  }
}

function formatBloodType(value) {
  const labels = {
    A: 'A型',
    B: 'B型',
    O: 'O型',
    AB: 'AB型',
    Unknown: '未知',
  }

  return labels[value] || value || '-'
}

function formatPatientStatus(value) {
  const labels = {
    Active: '激活',
    Discharged: '出院',
    Deceased: '死亡',
  }

  return labels[value] || value || '-'
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString('zh-CN', { hour12: false })
}

function formatDateOnly(value) {
  if (!value) {
    return '-'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleDateString('zh-CN')
}

function trimToEmpty(value) {
  return `${value || ''}`.trim()
}

function buildLoginUsername({ role, userType, hospitalId, deptCode, serialNumber }) {
  const normalizedSerialNumber = trimToEmpty(serialNumber)
  if (!normalizedSerialNumber) {
    return ''
  }

  if (role === 'admin') {
    return `ADMIN-${normalizedSerialNumber}`
  }

  const normalizedHospitalId = trimToEmpty(hospitalId)
  const normalizedDeptCode = trimToEmpty(deptCode)
  if (!normalizedHospitalId || !normalizedDeptCode) {
    return ''
  }

  const prefix = userType === 'nurse' ? 'NR' : userType === 'receptionist' ? 'RC' : 'DR'
  return `${prefix}-${normalizedHospitalId}-${normalizedDeptCode}-${normalizedSerialNumber}`
}

function getLoginErrorMessage(error) {
  if (error?.code === 1001 || error?.status === 404) {
    return '账号不存在。请确认医院、科室和序列号与注册结果一致，或先完成注册。'
  }

  if (error?.code === 2001 || error?.status === 401) {
    return '密码错误。请输入注册时设置的密码。'
  }

  if (error?.code === 6002) {
    return '账户已被冻结，请联系管理员处理。'
  }

  if (error?.code === 6001 || error?.status === 403) {
    return '账户当前不可用，请联系管理员。'
  }

  return error?.message || '登录失败，请检查输入信息。'
}

function buildLoginPrefillSearch(account) {
  const params = new URLSearchParams()
  const role = account?.role === 'admin' ? 'admin' : 'user'

  params.set('role', role)
  if (role !== 'admin') {
    if (account?.hospitalId) {
      params.set('hospitalId', account.hospitalId)
    }
    if (account?.deptCode) {
      params.set('deptCode', account.deptCode)
    }
    if (account?.role && !['admin', 'user'].includes(account.role)) {
      params.set('userType', account.role)
    }
  }
  if (account?.serialNumber) {
    params.set('serialNumber', account.serialNumber)
  }

  return params.toString()
}

function parseCollectionResponse(result, collectionKey, pageSize) {
  if (Array.isArray(result)) {
    const total = result.length
    return {
      items: result,
      total,
      totalPages: Math.max(1, Math.ceil(total / Math.max(pageSize, 1))),
    }
  }

  if (result && typeof result === 'object') {
    const rawItems = result[collectionKey] || result.content
    const items = Array.isArray(rawItems) ? rawItems : []
    const total = result.totalElements || result.total || items.length

    return {
      items,
      total,
      totalPages: Math.max(1, Math.ceil(total / Math.max(pageSize, 1))),
    }
  }

  return {
    items: [],
    total: 0,
    totalPages: 1,
  }
}

function formatPhoneNumber(value) {
  const cleaned = `${value || ''}`.replace(/\D/g, '').slice(0, 11)
  if (cleaned.length <= 3) {
    return cleaned
  }
  if (cleaned.length <= 7) {
    return `${cleaned.slice(0, 3)} ${cleaned.slice(3)}`
  }
  return `${cleaned.slice(0, 3)} ${cleaned.slice(3, 7)} ${cleaned.slice(7)}`
}

function validatePhoneNumber(value) {
  return /^1\d{10}$/.test(`${value || ''}`.replace(/\D/g, ''))
}

function formatIdCard(value) {
  const cleaned = `${value || ''}`.replace(/[^0-9Xx]/g, '').slice(0, 18)
  if (cleaned.length <= 6) {
    return cleaned
  }
  if (cleaned.length <= 10) {
    return `${cleaned.slice(0, 6)} ${cleaned.slice(6)}`
  }
  if (cleaned.length <= 14) {
    return `${cleaned.slice(0, 6)} ${cleaned.slice(6, 10)} ${cleaned.slice(10)}`
  }
  return `${cleaned.slice(0, 6)} ${cleaned.slice(6, 10)} ${cleaned.slice(10, 14)} ${cleaned.slice(14)}`
}

function deriveBirthDateFromIdCard(value) {
  const cleaned = `${value || ''}`.replace(/[^0-9Xx]/g, '')
  if (cleaned.length === 18) {
    return `${cleaned.slice(6, 10)}/${cleaned.slice(10, 12)}/${cleaned.slice(12, 14)}`
  }
  if (cleaned.length === 15) {
    return `19${cleaned.slice(6, 8)}/${cleaned.slice(8, 10)}/${cleaned.slice(10, 12)}`
  }
  return ''
}

function formatBirthDateForBackend(value) {
  return value ? value.replace(/\//g, '-') : ''
}

function formatDateTimeForInput(value) {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  const hours = `${date.getHours()}`.padStart(2, '0')
  const minutes = `${date.getMinutes()}`.padStart(2, '0')

  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function formatDateTimeForBackend(value) {
  if (!value) {
    return ''
  }

  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value) ? `${value}:00` : value
}

function calculateAge(birthDate) {
  if (!birthDate) {
    return '-'
  }

  const birth = new Date(birthDate)
  if (Number.isNaN(birth.getTime())) {
    return '-'
  }

  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const monthDiff = today.getMonth() - birth.getMonth()

  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) {
    age -= 1
  }

  return age >= 0 ? age : '-'
}

function statusClassName(value) {
  return `status-pill ${value || 'unknown'}`
}

// ============ 名称解析 Hook（ID → 名称缓存） ============
function useNameResolver() {
  const [cache] = useState(() => new Map())

  const resolve = async (type, id) => {
    if (!id) return id || '-'
    const cacheKey = `${type}:${id}`
    if (cache.has(cacheKey)) return cache.get(cacheKey)

    try {
      let name = id
      if (type === 'hospital') {
        const hospital = await authApi.getHospitalById(id)
        name = hospital?.name || id
      } else if (type === 'doctor') {
        const user = await authApi.getUserById(id)
        name = user?.fullName || user?.username || id
      } else if (type === 'department') {
        const dept = await authApi.getDepartmentById(id)
        name = dept?.name || id
      }
      cache.set(cacheKey, name)
      return name
    } catch {
      return id
    }
  }

  const resolveHospitalName = (hospitalId) => resolve('hospital', hospitalId)
  const resolveDoctorName = (doctorId) => resolve('doctor', doctorId)
  const resolveDepartmentName = (deptCode) => resolve('department', deptCode)

  return { resolveHospitalName, resolveDoctorName, resolveDepartmentName }
}

function useNotificationsSnapshot() {
  const [notifications, setNotifications] = useState(loadSystemNotifications())
  const [connectionStatus, setConnectionStatus] = useState('connecting')
  const socketRef = useRef(null)

  useEffect(() => {
    const syncNotifications = () => {
      setNotifications(loadSystemNotifications())
    }

    const socket = connectNotificationSocket()
    socketRef.current = socket
    setConnectionStatus('connecting')

    socket.onopen = () => {
      setConnectionStatus('connected')
    }

    socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data)
        if (payload.type === 'snapshot') {
          setNotifications(replaceSystemNotifications(payload.notifications || []))
        } else if (payload.notification) {
          // 处理所有含 notification 的消息类型（announcement, patient_registration 等）
          setNotifications(mergeSystemNotification(payload.notification))
        }
      } catch (_error) {
        setConnectionStatus('error')
      }
    }

    socket.onclose = () => {
      setConnectionStatus('disconnected')
    }

    socket.onerror = () => {
      setConnectionStatus('error')
    }

    window.addEventListener(systemNotificationEvents.update, syncNotifications)

    return () => {
      window.removeEventListener(systemNotificationEvents.update, syncNotifications)
      socket.close()
    }
  }, [])

  return {
    notifications,
    connectionStatus,
    unreadCount: getUnreadNotificationCount(),
    socketRef,
  }
}

function AppLink({ to, className = '', children, ...props }) {
  const { navigate } = useRouter()

  return (
    <a
      href={to}
      className={className}
      onClick={(event) => {
        if (
          event.defaultPrevented ||
          event.button !== 0 ||
          event.metaKey ||
          event.ctrlKey ||
          event.shiftKey ||
          event.altKey
        ) {
          return
        }

        event.preventDefault()
        navigate(to)
      }}
      {...props}
    >
      {children}
    </a>
  )
}

function Modal({ open, title, children, onClose, wide = false }) {
  if (!open) {
    return null
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className={`modal-card${wide ? ' modal-card-wide' : ''}`}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal-header">
          <h3>{title}</h3>
          <button type="button" className="ghost-button" onClick={onClose}>
            关闭
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

function ToolbarButton({ to, onClick, children, className = '', type = 'button', disabled = false }) {
  if (to) {
    return (
      <AppLink to={to} className={`button ${className}`.trim()}>
        {children}
      </AppLink>
    )
  }

  return (
    <button type={type} className={`button ${className}`.trim()} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  )
}

function LoadingBlock({ label = '加载中...' }) {
  return <div className="state-block">{label}</div>
}

function ErrorBlock({ message }) {
  return <div className="state-block error-block">{message}</div>
}

function EmptyBlock({ title, hint }) {
  return (
    <div className="empty-block">
      <h4>{title}</h4>
      {hint ? <p>{hint}</p> : null}
    </div>
  )
}

function PaginationBar({ page, totalPages, total, onPageChange }) {
  return (
    <div className="pagination-bar">
      <button type="button" className="button button-secondary" onClick={() => onPageChange(page - 1)} disabled={page <= 0}>
        上一页
      </button>
      <span>第 {page + 1} / {Math.max(totalPages, 1)} 页</span>
      <span>共 {total} 条</span>
      <button
        type="button"
        className="button button-secondary"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
      >
        下一页
      </button>
    </div>
  )
}

function TableActions({ children }) {
  return <div className="table-actions">{children}</div>
}

function App() {
  return (
    <RouterProvider>
      <AppRoot />
    </RouterProvider>
  )
}

function AppRoot() {
  const { location, navigate } = useRouter()
  const auth = useAuthState()
  const pathname = normalizePath(location.pathname)
  const searchParams = new URLSearchParams(location.search)
  const fullPath = `${pathname}${location.search}`
  const isPublicPath = PUBLIC_PATHS.has(pathname)

  let redirectTarget = ''
  if (pathname === '/home') {
    redirectTarget = '/dashboard'
  } else if (pathname === '/users') {
    redirectTarget = '/manage/users'
  } else if (!PUBLIC_PATHS.has(pathname) && pathname === '/') {
    redirectTarget = '/dashboard'
  }

  const route = resolveRoute(pathname)

  if (!redirectTarget && !isPublicPath && route.requiresAuth && !auth.isAuthenticated) {
    redirectTarget = `/login?redirect=${encodeURIComponent(fullPath)}`
  }

  if (!redirectTarget && isPublicPath && auth.isAuthenticated) {
    redirectTarget = '/dashboard'
  }

  if (!redirectTarget && route.requiredRole && auth.user?.role !== route.requiredRole) {
    redirectTarget = '/dashboard'
  }

  useEffect(() => {
    if (redirectTarget) {
      navigate(redirectTarget, { replace: true })
    }
  }, [navigate, redirectTarget])

  if (redirectTarget) {
    return null
  }

  if (isPublicPath) {
    if (pathname === '/login') {
      return <LoginPage redirectTo={searchParams.get('redirect') || ''} />
    }

    return <RegisterPage />
  }

  const PageComponent = route.component

  return (
    <AppShell routeTitle={route.title}>
      <PageComponent key={fullPath} />
    </AppShell>
  )
}

function resolveRoute(pathname) {
  const routes = {
    '/login': { title: '登录', component: LoginPage, requiresAuth: false },
    '/register': { title: '注册', component: RegisterPage, requiresAuth: false },
    '/': { title: '工作台', component: DashboardPage, requiresAuth: true },
    '/dashboard': { title: '工作台', component: DashboardPage, requiresAuth: true },
    '/profile': { title: '个人中心', component: UserProfilePage, requiresAuth: true },
    '/notifications': { title: '消息通知', component: NotificationsPage, requiresAuth: true },
    '/help': { title: '帮助中心', component: HelpCenterPage, requiresAuth: true },
    '/announcements': { title: '系统公告', component: AnnouncementsPage, requiresAuth: true },
    '/manage/users': { title: '用户管理', component: UserManagePage, requiresAuth: true, requiredRole: ADMIN_ROLE },
    '/manage/hospitals': { title: '医院科室管理', component: HospitalManagePage, requiresAuth: true, requiredRole: ADMIN_ROLE },
    '/patients': { title: '患者管理', component: PatientManagePage, requiresAuth: true },
    '/patients/list': { title: '患者列表', component: PatientListPage, requiresAuth: true },
    '/registration': { title: '患者登记', component: RegistrationPage, requiresAuth: true },
    '/reports': { title: '报表管理', component: ReportManagePage, requiresAuth: true },
    '/metric/images': { title: '医学影像管理', component: MedicalImageManagePage, requiresAuth: true },
    '/metric/images/chat': { title: '医学影像分析', component: MedicalImageChatPage, requiresAuth: true },
    '/metric/analyses': { title: '分析结果管理', component: AnalysisResultManagePage, requiresAuth: true },
    '/metric/analyses/detail': { title: '分析结果详情', component: AnalysisResultDetailPage, requiresAuth: true },
    '/metric/reports': { title: 'Metric 报表管理', component: MetricReportManagePage, requiresAuth: true },
    '/metric/reports/list': { title: '报表列表', component: ReportListPage, requiresAuth: true },
  }

  return routes[pathname] || {
    title: '未找到页面',
    component: NotFoundPage,
    requiresAuth: true,
  }
}

function AppShell({ routeTitle, children }) {
  const { location, navigate } = useRouter()
  const auth = useAuthState()
  const [currentTime, setCurrentTime] = useState('')
  const [notificationCount, setNotificationCount] = useState(getUnreadNotificationCount())
  const [userManagementExpanded, setUserManagementExpanded] = useState(false)
  const [patientManagementExpanded, setPatientManagementExpanded] = useState(false)
  const [metricManagementExpanded, setMetricManagementExpanded] = useState(false)

  useEffect(() => {
    const tick = () => {
      const now = new Date()
      const year = now.getFullYear()
      const month = `${now.getMonth() + 1}`.padStart(2, '0')
      const day = `${now.getDate()}`.padStart(2, '0')
      const hours = `${now.getHours()}`.padStart(2, '0')
      const minutes = `${now.getMinutes()}`.padStart(2, '0')
      const seconds = `${now.getSeconds()}`.padStart(2, '0')
      setCurrentTime(`${year}-${month}-${day} ${hours}:${minutes}:${seconds}`)
    }

    const syncNotificationCount = () => {
      setNotificationCount(getUnreadNotificationCount())
    }

    tick()
    syncNotificationCount()

    const timer = window.setInterval(tick, 1000)
    window.addEventListener(systemNotificationEvents.update, syncNotificationCount)
    window.addEventListener('storage', syncNotificationCount)

    return () => {
      window.clearInterval(timer)
      window.removeEventListener(systemNotificationEvents.update, syncNotificationCount)
      window.removeEventListener('storage', syncNotificationCount)
    }
  }, [])

  useEffect(() => {
    setUserManagementExpanded(location.pathname.startsWith('/manage/users'))
    setPatientManagementExpanded(location.pathname.startsWith('/patients'))
    setMetricManagementExpanded(location.pathname.startsWith('/metric'))
  }, [location.pathname])

  const currentUser = auth.user
  const isAdmin = currentUser?.role === ADMIN_ROLE
  const departmentInfo = currentUser
    ? currentUser.hospitalName || currentUser.deptName
      ? `${currentUser.hospitalName || ''}${currentUser.hospitalName && currentUser.deptName ? ' - ' : ''}${currentUser.deptName || ''}`
      : currentUser.hospitalId || currentUser.deptCode
        ? `${currentUser.hospitalId || ''}${currentUser.hospitalId && currentUser.deptCode ? ' - ' : ''}${currentUser.deptCode || ''}`
        : currentUser.role === ADMIN_ROLE
          ? '系统管理员'
          : '未分配部门'
    : '未登录'

  return (
    <div className="app-shell">
      <header className="shell-header">
        <div className="shell-header-main">
          <div className="header-brand">
            <h1>医工协同创新平台</h1>
          </div>

          <nav className="top-nav" aria-label="顶部导航">
            {TOP_NAV_ITEMS.map((item) => {
              const active = normalizePath(location.pathname) === item.path
              return (
                <button
                  key={item.path}
                  type="button"
                  className={`top-nav-item${active ? ' active' : ''}`}
                  onClick={() => navigate(item.path)}
                >
                  <span>{item.label}</span>
                  {item.path === '/notifications' && notificationCount > 0 ? (
                    <span className="top-nav-badge">{notificationCount}</span>
                  ) : null}
                </button>
              )
            })}
          </nav>

          <div className="header-user">
            <div className="user-info">
              <strong>{currentUser?.fullName || '用户'}</strong>
              <span className="user-role-pill">{roleLabel(currentUser?.role)}</span>
            </div>
            <button type="button" className="logout-button" onClick={() => {
              authStore.clearAuth()
              navigate('/login', { replace: true })
            }}>
              退出
            </button>
          </div>
        </div>

        <div className="shell-header-subbar">
          <div className="header-subinfo">
            <span className="header-subinfo-label">部门</span>
            <strong>{departmentInfo}</strong>
          </div>
          <div className="header-subinfo">
            <span className="header-subinfo-label">时间</span>
            <strong>{currentTime}</strong>
          </div>
        </div>
      </header>

      <div className="shell-body">
        <aside className="shell-sidebar">
          <div className="sidebar-section">
            <SidebarButton label="工作台" active={location.pathname === '/dashboard'} onClick={() => navigate('/dashboard')} />

            {isAdmin ? (
              <>
                <SidebarGroup
                  label="用户管理"
                  active={location.pathname.startsWith('/manage/users')}
                  expanded={userManagementExpanded}
                  onToggle={() => setUserManagementExpanded((value) => !value)}
                >
                  <SidebarSubButton
                    label="用户管理"
                    active={location.pathname === '/manage/users'}
                    onClick={() => navigate('/manage/users')}
                  />
                </SidebarGroup>
                <SidebarButton
                  label="医院科室管理"
                  active={location.pathname === '/manage/hospitals'}
                  onClick={() => navigate('/manage/hospitals')}
                />
              </>
            ) : (
              <>
                {(auth.user?.role === 'receptionist' || auth.user?.role === 'nurse') ? (
                  <SidebarButton
                    label="患者登记"
                    active={location.pathname === '/registration'}
                    onClick={() => navigate('/registration')}
                  />
                ) : null}
                <SidebarGroup
                  label="患者管理"
                  active={location.pathname.startsWith('/patients')}
                  expanded={patientManagementExpanded}
                  onToggle={() => setPatientManagementExpanded((value) => !value)}
                >
                  <SidebarSubButton
                    label="患者管理"
                    active={location.pathname === '/patients'}
                    onClick={() => navigate('/patients')}
                  />
                  <SidebarSubButton
                    label="患者列表"
                    active={location.pathname === '/patients/list'}
                    onClick={() => navigate('/patients/list')}
                  />
                </SidebarGroup>
                <SidebarButton
                  label="报表管理"
                  active={location.pathname === '/reports'}
                  onClick={() => navigate('/reports')}
                />
                <SidebarGroup
                  label="Metric 管理"
                  active={location.pathname.startsWith('/metric')}
                  expanded={metricManagementExpanded}
                  onToggle={() => setMetricManagementExpanded((value) => !value)}
                >
                  <SidebarSubButton
                    label="医学影像管理"
                    active={location.pathname === '/metric/images'}
                    onClick={() => navigate('/metric/images')}
                  />
                  <SidebarSubButton
                    label="分析结果管理"
                    active={location.pathname === '/metric/analyses'}
                    onClick={() => navigate('/metric/analyses')}
                  />
                  <SidebarSubButton
                    label="Metric 报表管理"
                    active={location.pathname === '/metric/reports'}
                    onClick={() => navigate('/metric/reports')}
                  />
                </SidebarGroup>
              </>
            )}
          </div>
        </aside>

        <main className="shell-main">
          <div className="page-content">
            {children}
          </div>
        </main>
      </div>
    </div>
  )
}

function SidebarButton({ label, active, onClick }) {
  return (
    <button type="button" className={`sidebar-button${active ? ' active' : ''}`} onClick={onClick}>
      {label}
    </button>
  )
}

function SidebarGroup({ label, active, expanded, onToggle, children }) {
  return (
    <div className={`sidebar-group${active ? ' active' : ''}`}>
      <button type="button" className={`sidebar-button${active ? ' active' : ''}`} onClick={onToggle}>
        <span>{label}</span>
        <span className="sidebar-chevron">{expanded ? '▾' : '▸'}</span>
      </button>
      {expanded ? <div className="sidebar-group-body">{children}</div> : null}
    </div>
  )
}

function SidebarSubButton({ label, active, onClick }) {
  return (
    <button type="button" className={`sidebar-sub-button${active ? ' active' : ''}`} onClick={onClick}>
      {label}
    </button>
  )
}

function LoginPage({ redirectTo }) {
  const { location, navigate } = useRouter()
  const [formData, setFormData] = useState({
    role: 'user',
    userType: 'doctor',
    hospitalId: '',
    deptCode: '',
    serialNumber: '',
    password: '',
  })
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [hospitals, setHospitals] = useState([])
  const [departments, setDepartments] = useState([])

  useEffect(() => {
    const params = new URLSearchParams(location.search)
    const role = params.get('role') === 'admin' ? 'admin' : 'user'
    const userType = params.get('userType') || 'doctor'
    const serialNumber = trimToEmpty(params.get('serialNumber'))
    const hospitalId = trimToEmpty(params.get('hospitalId'))
    const deptCode = trimToEmpty(params.get('deptCode'))

    if (!params.has('role') && !serialNumber && !hospitalId && !deptCode) {
      return
    }

    setFormData((current) => ({
      ...current,
      role,
      userType: role === 'admin' ? '' : userType,
      hospitalId: role === 'admin' ? '' : hospitalId,
      deptCode: role === 'admin' ? '' : deptCode,
      serialNumber,
    }))
  }, [location.search])

  useEffect(() => {
    let active = true

    authApi.getAllHospitals(true)
      .then((result) => {
        if (active) {
          setHospitals(Array.isArray(result) ? result : [])
        }
      })
      .catch((error) => {
        console.error('加载医院列表失败:', error)
      })

    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    if (!formData.hospitalId) {
      setDepartments([])
      return
    }

    let active = true
    authApi.getHospitalDepartments(formData.hospitalId, true)
      .then((result) => {
        if (active) {
          setDepartments(Array.isArray(result) ? result : [])
        }
      })
      .catch((error) => {
        console.error('加载科室列表失败:', error)
      })

    return () => {
      active = false
    }
  }, [formData.hospitalId])

  const handleSubmit = async (event) => {
    event.preventDefault()
    setLoading(true)
    setErrorMessage('')

    try {
      const username = buildLoginUsername(formData)

      const result = await authApi.login({
        username,
        password: formData.password,
      })

      authStore.setToken(result.token)
      authStore.setCurrentUser(result.user)

      // 根据角色跳转到不同首页
      const userRole = result.user?.role
      const redirectMap = {
        admin: redirectTo || '/dashboard',
        doctor: redirectTo || '/patients/list',
        nurse: redirectTo || '/patients/list',
        receptionist: redirectTo || '/registration',
      }
      navigate(redirectMap[userRole] || redirectTo || '/dashboard', { replace: true })
    } catch (error) {
      setErrorMessage(getLoginErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  const loginUsernamePreview = buildLoginUsername(formData)

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <div className="auth-header">
          <div className="shell-kicker">React Client</div>
          <h2>医工协同创新平台</h2>
          <p>请输入账号信息以继续访问平台。</p>
        </div>

        <div className="form-grid">
          <label className="field">
            <span>身份选择</span>
            <div className="segmented-control">
              <button
                type="button"
                className={formData.role === 'admin' ? 'active' : ''}
                onClick={() => setFormData((current) => ({ ...current, role: 'admin', hospitalId: '', deptCode: '' }))}
              >
                管理员
              </button>
              <button
                type="button"
                className={formData.role === 'user' ? 'active' : ''}
                onClick={() => setFormData((current) => ({ ...current, role: 'user' }))}
              >
                普通用户
              </button>
            </div>
          </label>

          {formData.role === 'user' ? (
            <>
              <label className="field">
                <span>所属医院</span>
                <select
                  value={formData.hospitalId}
                  onChange={(event) => {
                    const hospitalId = event.target.value
                    setFormData((current) => ({ ...current, hospitalId, deptCode: '' }))
                  }}
                  required
                >
                  <option value="">请选择医院</option>
                  {hospitals.map((hospital) => (
                    <option key={hospital.id} value={hospital.id}>
                      {hospital.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>所属科室</span>
                <select
                  value={formData.deptCode}
                  onChange={(event) => setFormData((current) => ({ ...current, deptCode: event.target.value }))}
                  required
                >
                  <option value="">请选择科室</option>
                  {departments.map((department) => (
                    <option key={department.id} value={department.id}>
                      {department.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>用户类型</span>
                <select
                  value={formData.userType}
                  onChange={(event) => setFormData((current) => ({ ...current, userType: event.target.value }))}
                  required
                >
                  <option value="doctor">医生</option>
                  <option value="nurse">护士</option>
                  <option value="receptionist">前台/挂号</option>
                </select>
              </label>
            </>
          ) : null}

          <label className="field">
            <span>{formData.role === 'admin' ? '管理员序列号' : '用户序列号'}</span>
            <input
              value={formData.serialNumber}
              onChange={(event) => setFormData((current) => ({ ...current, serialNumber: event.target.value }))}
              placeholder="请输入序列号"
              required
            />
          </label>
          <label className="field">
            <span>密码</span>
            <input
              type="password"
              value={formData.password}
              onChange={(event) => setFormData((current) => ({ ...current, password: event.target.value }))}
              placeholder="请输入密码"
              required
            />
          </label>
        </div>

        <div className="alert-banner info-banner">
          当前登录账号：
          <code className="inline-code">
            {loginUsernamePreview || '请先选择医院、科室并输入序列号'}
          </code>
        </div>

        {errorMessage ? <div className="alert-banner error-banner">{errorMessage}</div> : null}

        <div className="auth-actions">
          <button type="submit" className="button button-primary" disabled={loading}>
            {loading ? '登录中...' : '登录'}
          </button>
          <AppLink to="/register" className="text-link">
            还没有账号？立即注册
          </AppLink>
        </div>
      </form>
    </div>
  )
}

function RegisterPage() {
  const { navigate } = useRouter()
  const [formData, setFormData] = useState({
    role: 'doctor',
    hospitalId: '',
    deptCode: '',
    fullName: '',
    password: '',
  })
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [registerSuccess, setRegisterSuccess] = useState(false)
  const [registeredAccount, setRegisteredAccount] = useState(null)
  const [copySuccess, setCopySuccess] = useState(false)
  const [hospitals, setHospitals] = useState([])
  const [departments, setDepartments] = useState([])

  useEffect(() => {
    let active = true
    authApi.getAllHospitals()
      .then((result) => {
        if (active) {
          setHospitals(Array.isArray(result) ? result : [])
        }
      })
      .catch((error) => console.error('加载医院列表失败:', error))

    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    if (!formData.hospitalId || formData.role === 'admin') {
      setDepartments([])
      return
    }

    let active = true
    authApi.getHospitalDepartments(formData.hospitalId)
      .then((result) => {
        if (active) {
          setDepartments(Array.isArray(result) ? result : [])
        }
      })
      .catch((error) => console.error('加载科室列表失败:', error))

    return () => {
      active = false
    }
  }, [formData.hospitalId, formData.role])

  const handleSubmit = async (event) => {
    event.preventDefault()
    setLoading(true)
    setErrorMessage('')

    try {
      if (formData.password !== confirmPassword) {
        throw new Error('两次输入的密码不一致')
      }

      if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/.test(formData.password)) {
        throw new Error('密码强度不足，请至少输入 8 位并包含大小写字母和数字。')
      }

      const response = await authApi.register({
        role: formData.role,
        hospitalId: formData.role === 'admin' ? null : trimToEmpty(formData.hospitalId),
        deptCode: formData.role === 'admin' ? null : trimToEmpty(formData.deptCode),
        fullName: trimToEmpty(formData.fullName),
        password: formData.password,
      })

      const username = response?.user?.username || ''
      const serialNumber = username.startsWith('ADMIN-')
        ? username.slice('ADMIN-'.length)
        : username.split('-').pop() || username

      setRegisteredAccount({
        role: response?.user?.role || formData.role,
        username,
        serialNumber,
        hospitalId: response?.user?.hospitalId || trimToEmpty(formData.hospitalId),
        deptCode: response?.user?.deptCode || trimToEmpty(formData.deptCode),
      })
      setRegisterSuccess(true)
    } catch (error) {
      setErrorMessage(error.message || '注册失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }

  const copySerialNumber = async () => {
    try {
      await navigator.clipboard.writeText(registeredAccount?.serialNumber || '')
      setCopySuccess(true)
      window.setTimeout(() => setCopySuccess(false), 1500)
    } catch (error) {
      console.error('复制失败:', error)
    }
  }

  const navigateToLoginWithAccount = () => {
    const query = buildLoginPrefillSearch(registeredAccount)
    navigate(`/login${query ? `?${query}` : ''}`, { replace: true })
  }

  if (registerSuccess) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <div className="auth-header">
            <div className="shell-kicker">Registration Complete</div>
            <h2>账号已创建</h2>
            <p>请记录以下登录信息。返回登录页后将自动为你填入可登录的账号信息。</p>
          </div>
          <div className="serial-display">{registeredAccount?.serialNumber}</div>
          <div className="detail-grid">
            <div className="profile-field">
              <span>完整账号</span>
              <strong>{registeredAccount?.username || '-'}</strong>
            </div>
            <div className="profile-field">
              <span>角色</span>
              <strong>{roleLabel(registeredAccount?.role)}</strong>
            </div>
            <div className="profile-field">
              <span>所属医院</span>
              <strong>{registeredAccount?.hospitalId || '-'}</strong>
            </div>
            <div className="profile-field">
              <span>所属科室</span>
              <strong>{registeredAccount?.deptCode || '-'}</strong>
            </div>
          </div>
          <div className="auth-actions">
            <button type="button" className="button button-secondary" onClick={copySerialNumber}>
              {copySuccess ? '已复制' : '复制序列号'}
            </button>
            <button type="button" className="button button-primary" onClick={navigateToLoginWithAccount}>
              前往登录
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-page">
      <form className="auth-card auth-card-wide" onSubmit={handleSubmit}>
        <div className="auth-header">
          <div className="shell-kicker">Create Account</div>
          <h2>创建平台账号</h2>
          <p>管理员和医护用户都可以通过当前表单注册。</p>
        </div>

        <div className="form-grid two-column">
          <label className="field">
            <span>角色</span>
            <select value={formData.role} onChange={(event) => setFormData((current) => ({ ...current, role: event.target.value }))}>
              <option value="admin">管理员</option>
              <option value="doctor">医生</option>
              <option value="nurse">护士</option>
              <option value="receptionist">前台</option>
            </select>
          </label>

          <label className="field">
            <span>姓名</span>
            <input
              value={formData.fullName}
              onChange={(event) => setFormData((current) => ({ ...current, fullName: event.target.value }))}
              placeholder="请输入姓名"
              required
            />
          </label>

          {formData.role !== 'admin' ? (
            <>
              <label className="field">
                <span>所属医院</span>
                <select
                  value={formData.hospitalId}
                  onChange={(event) => setFormData((current) => ({ ...current, hospitalId: event.target.value, deptCode: '' }))}
                  required
                >
                  <option value="">请选择医院</option>
                  {hospitals.map((hospital) => (
                    <option key={hospital.id} value={hospital.id}>
                      {hospital.name}
                    </option>
                  ))}
                </select>
              </label>

              <label className="field">
                <span>所属科室</span>
                <select
                  value={formData.deptCode}
                  onChange={(event) => setFormData((current) => ({ ...current, deptCode: event.target.value }))}
                  required
                >
                  <option value="">请选择科室</option>
                  {departments.map((department) => (
                    <option key={department.id} value={department.id}>
                      {department.name}
                    </option>
                  ))}
                </select>
              </label>
            </>
          ) : null}

          <label className="field">
            <span>密码</span>
            <input
              type="password"
              value={formData.password}
              onChange={(event) => setFormData((current) => ({ ...current, password: event.target.value }))}
              placeholder="至少 8 位，包含大小写字母和数字"
              required
            />
          </label>

          <label className="field">
            <span>确认密码</span>
            <input
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              placeholder="再次输入密码"
              required
            />
          </label>
        </div>

        {errorMessage ? <div className="alert-banner error-banner">{errorMessage}</div> : null}

        <div className="auth-actions">
          <button type="submit" className="button button-primary" disabled={loading}>
            {loading ? '提交中...' : '注册'}
          </button>
          <AppLink to="/login" className="text-link">
            返回登录
          </AppLink>
        </div>
      </form>
    </div>
  )
}

function RegistrationPage() {
  const { navigate } = useRouter()
  const auth = useAuthState()
  const user = auth.user
  const [hospitals, setHospitals] = useState([])
  const [departments, setDepartments] = useState([])
  const [doctors, setDoctors] = useState([])
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState(null)
  const [error, setError] = useState('')
  const [form, setForm] = useState({
    name: '',
    gender: 'M',
    birthDate: '',
    phone: '',
    idCard: '',
    hospitalId: user?.hospitalId || '',
    department: user?.deptCode || '',
    doctorId: '',
  })

  // 加载医院列表
  useEffect(() => {
    authApi.getAllHospitals()
      .then(setHospitals)
      .catch(() => setError('加载医院列表失败'))
  }, [])

  // 选择医院后加载科室
  useEffect(() => {
    if (!form.hospitalId) {
      setDepartments([])
      setForm((prev) => ({ ...prev, department: '', doctorId: '' }))
      return
    }
    authApi.getHospitalDepartments(form.hospitalId)
      .then((depts) => {
        setDepartments(depts)
        // 如果用户已有科室且在科室列表中，自动选中
        if (form.department && depts.some((d) => d.deptCode === form.department || d.id === form.department)) {
          // keep current department
        }
      })
      .catch(() => setError('加载科室列表失败'))
  }, [form.hospitalId])

  // 选择科室后加载医生
  useEffect(() => {
    if (!form.hospitalId || !form.department) {
      setDoctors([])
      setForm((prev) => ({ ...prev, doctorId: '' }))
      return
    }
    authApi.getDepartmentDoctors(form.hospitalId, form.department)
      .then(setDoctors)
      .catch(() => setError('加载医生列表失败'))
  }, [form.hospitalId, form.department])

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      if (!form.name.trim()) throw new Error('请输入患者姓名')
      if (!form.doctorId) throw new Error('请选择主治医生')

      // 生成病历号
      const now = new Date()
      const dateStr = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
      const randomStr = String(Math.floor(Math.random() * 10000)).padStart(4, '0')
      const patientId = `MRN-${dateStr}-${randomStr}`

      const payload = {
        hospitalId: form.hospitalId,
        patientId,
        name: form.name.trim(),
        gender: form.gender,
        birthDate: form.birthDate || null,
        phone: form.phone.replace(/\D/g, '') || null,
        idCard: form.idCard.replace(/\s/g, '') || null,
        department: form.department,
        attendingDoctorId: form.doctorId,
      }

      const createdPatient = await patientApi.createPatient(payload)

      // 通知医生
      const doctor = doctors.find((d) => d.id === form.doctorId || d.userId === form.doctorId)
      const doctorUserId = doctor?.userId || form.doctorId

      authApi.sendNotification({
        userId: doctorUserId,
        title: '新患者登记',
        content: `前台 ${user?.fullName || ''} 为您登记了新患者：${form.name}（病历号：${patientId}）`,
        type: 'patient_registration',
      }).catch((notifyErr) => {
        console.warn('通知医生失败（不影响登记）:', notifyErr)
      })

      setSuccess({
        patientId,
        name: form.name.trim(),
        doctorName: doctor?.fullName || doctor?.name || '已选医生',
      })
    } catch (err) {
      setError(err.message || '登记失败，请重试')
    } finally {
      setSubmitting(false)
    }
  }

  if (success) {
    return (
      <div className="page-container">
        <div className="card">
          <div className="card-header">
            <h2>登记成功</h2>
          </div>
          <div className="card-body">
            <div className="alert-banner success-banner">
              患者 <strong>{success.name}</strong>（病历号：{success.patientId}）已成功登记，
              已通知医生 <strong>{success.doctorName}</strong>。
            </div>
            <div className="form-actions" style={{ marginTop: '16px' }}>
              <button type="button" className="button button-primary" onClick={() => { setSuccess(null); setForm({ name: '', gender: 'M', birthDate: '', phone: '', idCard: '', hospitalId: user?.hospitalId || '', department: user?.deptCode || '', doctorId: '' }) }}>
                继续登记
              </button>
              <button type="button" className="button button-secondary" onClick={() => navigate('/patients/list')}>
                查看患者列表
              </button>
            </div>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="page-container">
      <div className="card">
        <div className="card-header">
          <h2>患者登记</h2>
          <p className="text-secondary">为就诊患者创建档案，登记后将通知对应医生</p>
        </div>
        <form className="card-body" onSubmit={handleSubmit}>
          {error ? <div className="alert-banner error-banner">{error}</div> : null}

          <div className="form-grid two-column">
            <label className="field">
              <span>患者姓名 <span className="required-mark">*</span></span>
              <input
                value={form.name}
                onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                placeholder="请输入患者姓名"
                required
              />
            </label>

            <label className="field">
              <span>性别 <span className="required-mark">*</span></span>
              <select
                value={form.gender}
                onChange={(e) => setForm((prev) => ({ ...prev, gender: e.target.value }))}
              >
                <option value="M">男</option>
                <option value="F">女</option>
              </select>
            </label>

            <label className="field">
              <span>出生日期</span>
              <input
                type="date"
                value={form.birthDate}
                onChange={(e) => setForm((prev) => ({ ...prev, birthDate: e.target.value }))}
              />
            </label>

            <label className="field">
              <span>联系电话</span>
              <input
                value={form.phone}
                onChange={(e) => setForm((prev) => ({ ...prev, phone: e.target.value }))}
                placeholder="请输入11位手机号"
              />
            </label>

            <label className="field">
              <span>身份证号</span>
              <input
                value={form.idCard}
                onChange={(e) => setForm((prev) => ({ ...prev, idCard: e.target.value }))}
                placeholder="请输入身份证号（选填）"
              />
            </label>

            <label className="field">
              <span>所属医院 <span className="required-mark">*</span></span>
              <select
                value={form.hospitalId}
                onChange={(e) => setForm((prev) => ({ ...prev, hospitalId: e.target.value, department: '', doctorId: '' }))}
                required
              >
                <option value="">请选择医院</option>
                {hospitals.map((h) => (
                  <option key={h.id} value={h.id}>{h.name}</option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>所属科室 <span className="required-mark">*</span></span>
              <select
                value={form.department}
                onChange={(e) => setForm((prev) => ({ ...prev, department: e.target.value, doctorId: '' }))}
                required
                disabled={!form.hospitalId}
              >
                <option value="">{form.hospitalId ? '请选择科室' : '请先选择医院'}</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.deptCode || d.id}>{d.name}</option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>主治医生 <span className="required-mark">*</span></span>
              <select
                value={form.doctorId}
                onChange={(e) => setForm((prev) => ({ ...prev, doctorId: e.target.value }))}
                required
                disabled={!form.department}
              >
                <option value="">{form.department ? '请选择医生' : '请先选择科室'}</option>
                {doctors.map((d) => (
                  <option key={d.id} value={d.userId || d.id}>{d.fullName || d.name}</option>
                ))}
              </select>
            </label>
          </div>

          <div className="form-actions">
            <button type="submit" className="button button-primary" disabled={submitting}>
              {submitting ? '提交中...' : '确认登记'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function DashboardPage() {
  const { navigate } = useRouter()
  const auth = useAuthState()
  const isAdmin = auth.user?.role === ADMIN_ROLE

  const adminStats = [
    { label: '总用户数', value: '156', hint: '+5.2% 较上周' },
    { label: '医院数量', value: '12', hint: '+2 本月新增' },
    { label: '科室数量', value: '45', hint: '平均每院 3.8 个' },
    { label: '待处理事项', value: '3', hint: '需完成审批' },
  ]

  const userStats = [
    { label: '今日患者', value: '28', hint: '较昨日 +12.5%' },
    { label: '今日报表', value: '15', hint: '较昨日 +8.3%' },
    { label: '待处理任务', value: '7', hint: '仍需跟进' },
    { label: '已完成任务', value: '23', hint: '完成率 76.7%' },
  ]

  const recentActivities = [
    '新增用户：张医生',
    '新增医院：市中心医院',
    '生成报表：月度统计报告',
    '系统更新通知',
    '用户角色变更：李医生',
  ]

  const adminQuickActions = [
    { label: '用户管理', path: '/manage/users' },
    { label: '医院科室管理', path: '/manage/hospitals' },
    { label: '消息通知', path: '/notifications' },
    { label: '系统公告', path: '/announcements' },
  ]

  const userQuickActions = [
    { label: '患者管理', path: '/patients' },
    { label: '患者列表', path: '/patients/list' },
    { label: '医学影像管理', path: '/metric/images' },
    { label: 'Metric 报表管理', path: '/metric/reports' },
  ]

  return (
    <div className="page-grid">
      <section className="panel">
        <div className="section-header">
          <div>
            <h2>{isAdmin ? '管理员工作台' : `${roleLabel(auth.user?.role)}工作台`}</h2>
            <p>平台运行状态、近期活动和快捷操作的概览。</p>
          </div>
        </div>

        <div className="stats-grid">
          {(isAdmin ? adminStats : userStats).map((item) => (
            <article key={item.label} className="stat-card">
              <span>{item.label}</span>
              <strong>{item.value}</strong>
              <small>{item.hint}</small>
            </article>
          ))}
        </div>
      </section>

      <section className="panel two-column-layout">
        <div>
          <div className="section-header">
            <div>
              <h3>最近活动</h3>
              <p>展示最近的系统事件和协作轨迹。</p>
            </div>
          </div>
          <ul className="activity-list">
            {recentActivities.map((activity) => (
              <li key={activity}>{activity}</li>
            ))}
          </ul>
        </div>

        <div>
          <div className="section-header">
            <div>
              <h3>快捷操作</h3>
              <p>{isAdmin ? '快速进入核心平台管理功能。' : '快速进入高频业务场景。'}</p>
            </div>
          </div>
          <div className="quick-actions">
            {(isAdmin ? adminQuickActions : userQuickActions).map((item) => (
              <button
                key={item.path}
                type="button"
                className="quick-action-card"
                onClick={() => navigate(item.path)}
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>
      </section>
    </div>
  )
}

function HelpCenterPage() {
  return (
    <div className="page-grid">
      <section className="panel">
        <div className="section-header">
          <div>
            <h2>帮助中心</h2>
            <p>汇总高频问题、平台流程和排障建议。</p>
          </div>
        </div>
        <div className="resource-grid">
          {HELP_RESOURCES.map((item) => (
            <article key={item.title} className="resource-card">
              <h3>{item.title}</h3>
              <p>{item.content}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="panel">
        <div className="section-header">
          <div>
            <h3>常见问题</h3>
            <p>围绕患者、影像、报表和权限管理的核心问题。</p>
          </div>
        </div>
        <div className="faq-list">
          {FAQ_ITEMS.map((item) => (
            <article key={item.title} className="faq-item">
              <h4>{item.title}</h4>
              <p>{item.content}</p>
            </article>
          ))}
        </div>
      </section>
    </div>
  )
}

function NotificationsPage() {
  const { notifications, connectionStatus, unreadCount } = useNotificationsSnapshot()

  useEffect(() => {
    markSystemNotificationsRead()
  }, [])

  return (
    <div className="page-grid">
      <section className="panel">
        <div className="section-header">
          <div>
            <h2>消息通知</h2>
            <p>展示来自公告通道的实时消息，打开页面后自动标记为已读。</p>
          </div>
          <span className={`connection-pill ${connectionStatus}`}>{connectionStatus}</span>
        </div>

        <div className="stats-grid compact">
          <article className="stat-card">
            <span>总通知数</span>
            <strong>{notifications.length}</strong>
          </article>
          <article className="stat-card">
            <span>未读数</span>
            <strong>{unreadCount}</strong>
          </article>
        </div>
      </section>

      <section className="panel">
        {notifications.length === 0 ? (
          <EmptyBlock title="当前没有系统通知" hint="新的公告或通知到达后会实时显示在这里。" />
        ) : (
          <div className="notification-list">
            {notifications.map((item) => (
              <article key={item.id} className="notification-card">
                <div className="notification-top">
                  <span className={`priority-pill ${item.priority || 'normal'}`}>{item.priority || 'normal'}</span>
                  <span>{formatDateTime(item.createdAt)}</span>
                </div>
                <h3>{item.title}</h3>
                <p>{item.content}</p>
                <small>发送人：{item.senderName || '系统'}</small>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}

function AnnouncementsPage() {
  const { notifications, connectionStatus, socketRef } = useNotificationsSnapshot()
  const auth = useAuthState()
  const [draft, setDraft] = useState({
    title: '',
    content: '',
    priority: 'normal',
  })

  const canPublish = auth.user?.role === ADMIN_ROLE &&
    connectionStatus === 'connected' &&
    draft.title.trim() &&
    draft.content.trim()

  const publishAnnouncement = () => {
    if (!canPublish || !socketRef.current) {
      return
    }

    socketRef.current.send(JSON.stringify({
      type: 'publish',
      title: draft.title,
      content: draft.content,
      priority: draft.priority,
    }))

    setDraft({
      title: '',
      content: '',
      priority: 'normal',
    })
  }

  return (
    <div className="page-grid">
      <section className="panel">
        <div className="section-header">
          <div>
            <h2>系统公告</h2>
            <p>所有系统级消息都通过公告通道广播，同时同步到通知中心。</p>
          </div>
          <span className={`connection-pill ${connectionStatus}`}>{connectionStatus}</span>
        </div>

        {auth.user?.role === ADMIN_ROLE ? (
          <div className="form-grid two-column">
            <label className="field">
              <span>公告标题</span>
              <input value={draft.title} onChange={(event) => setDraft((current) => ({ ...current, title: event.target.value }))} />
            </label>
            <label className="field">
              <span>优先级</span>
              <select value={draft.priority} onChange={(event) => setDraft((current) => ({ ...current, priority: event.target.value }))}>
                <option value="normal">普通</option>
                <option value="important">重要</option>
                <option value="urgent">紧急</option>
              </select>
            </label>
            <label className="field field-span-2">
              <span>公告内容</span>
              <textarea value={draft.content} onChange={(event) => setDraft((current) => ({ ...current, content: event.target.value }))} rows="4" />
            </label>
            <div className="field-actions field-span-2">
              <button type="button" className="button button-primary" disabled={!canPublish} onClick={publishAnnouncement}>
                发布公告
              </button>
            </div>
          </div>
        ) : null}
      </section>

      <section className="panel">
        {notifications.length === 0 ? (
          <EmptyBlock title="当前没有公告" hint="新公告会实时推送到这个页面。" />
        ) : (
          <div className="notification-list">
            {notifications.map((item) => (
              <article key={item.id} className="notification-card">
                <div className="notification-top">
                  <span className={`priority-pill ${item.priority || 'normal'}`}>{item.priority || 'normal'}</span>
                  <span>{formatDateTime(item.createdAt)}</span>
                </div>
                <h3>{item.title}</h3>
                <p>{item.content}</p>
                <small>发送人：{item.senderName || '系统'}</small>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}

function UserProfilePage() {
  const auth = useAuthState()
  const [user, setUser] = useState(auth.user)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [changingPassword, setChangingPassword] = useState(false)
  const [changingUsername, setChangingUsername] = useState(false)
  const [formLoading, setFormLoading] = useState(false)
  const [passwordForm, setPasswordForm] = useState({
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
  })
  const [usernameForm, setUsernameForm] = useState({ newUsername: '' })

  const fetchCurrentUser = async () => {
    setLoading(true)
    setError('')

    try {
      const userData = await authApi.getCurrentUser()
      setUser(userData)
      authStore.setCurrentUser(userData)
    } catch (fetchError) {
      setError(fetchError.message || '获取用户信息失败。')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchCurrentUser()
  }, [])

  const closeModals = () => {
    setChangingPassword(false)
    setChangingUsername(false)
    setFormError('')
    setPasswordForm({
      oldPassword: '',
      newPassword: '',
      confirmPassword: '',
    })
    setUsernameForm({ newUsername: '' })
  }

  const submitPasswordChange = async (event) => {
    event.preventDefault()
    setFormError('')

    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setFormError('两次输入的密码不一致。')
      return
    }

    if (passwordForm.newPassword.length < 6) {
      setFormError('密码长度至少为 6 位。')
      return
    }

    setFormLoading(true)
    try {
      await authApi.changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword,
      })
      closeModals()
      window.alert('密码修改成功。')
    } catch (submitError) {
      setFormError(submitError.message || '密码修改失败。')
    } finally {
      setFormLoading(false)
    }
  }

  const submitUsernameChange = async (event) => {
    event.preventDefault()
    setFormError('')
    setFormLoading(true)
    try {
      await authApi.changeUsername(usernameForm.newUsername)
      await fetchCurrentUser()
      closeModals()
      window.alert('用户名修改成功。')
    } catch (submitError) {
      setFormError(submitError.message || '用户名修改失败。')
    } finally {
      setFormLoading(false)
    }
  }

  if (loading) {
    return <LoadingBlock />
  }

  if (error) {
    return <ErrorBlock message={error} />
  }

  return (
    <>
      <section className="panel">
        <div className="section-header">
          <div>
            <h2>个人中心</h2>
            <p>查看账号信息、修改密码，以及管理员用户名维护。</p>
          </div>
          <div className="header-actions">
            <button type="button" className="button button-secondary" onClick={() => setChangingPassword(true)}>
              修改密码
            </button>
            {user?.role === ADMIN_ROLE ? (
              <button type="button" className="button button-primary" onClick={() => setChangingUsername(true)}>
                修改用户名
              </button>
            ) : null}
          </div>
        </div>

        <div className="detail-grid">
          <ProfileField label="姓名" value={user?.fullName} />
          <ProfileField label="用户名" value={user?.username} />
          <ProfileField label="角色" value={roleLabel(user?.role)} />
          <ProfileField label="医院" value={user?.hospitalName || user?.hospitalId} />
          <ProfileField label="科室" value={user?.deptName || user?.deptCode} />
          <ProfileField label="创建时间" value={formatDateTime(user?.createdAt)} />
        </div>
      </section>

      <Modal open={changingPassword} title="修改密码" onClose={closeModals}>
        <form className="form-grid" onSubmit={submitPasswordChange}>
          <label className="field">
            <span>旧密码</span>
            <input
              type="password"
              value={passwordForm.oldPassword}
              onChange={(event) => setPasswordForm((current) => ({ ...current, oldPassword: event.target.value }))}
              required
            />
          </label>
          <label className="field">
            <span>新密码</span>
            <input
              type="password"
              value={passwordForm.newPassword}
              onChange={(event) => setPasswordForm((current) => ({ ...current, newPassword: event.target.value }))}
              required
            />
          </label>
          <label className="field">
            <span>确认新密码</span>
            <input
              type="password"
              value={passwordForm.confirmPassword}
              onChange={(event) => setPasswordForm((current) => ({ ...current, confirmPassword: event.target.value }))}
              required
            />
          </label>
          {formError ? <div className="alert-banner error-banner">{formError}</div> : null}
          <div className="field-actions">
            <button type="submit" className="button button-primary" disabled={formLoading}>
              {formLoading ? '提交中...' : '保存'}
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={changingUsername} title="修改管理员用户名" onClose={closeModals}>
        <form className="form-grid" onSubmit={submitUsernameChange}>
          <label className="field">
            <span>新用户名</span>
            <input
              value={usernameForm.newUsername}
              onChange={(event) => setUsernameForm({ newUsername: event.target.value })}
              required
            />
          </label>
          {formError ? <div className="alert-banner error-banner">{formError}</div> : null}
          <div className="field-actions">
            <button type="submit" className="button button-primary" disabled={formLoading}>
              {formLoading ? '提交中...' : '保存'}
            </button>
          </div>
        </form>
      </Modal>
    </>
  )
}

function ProfileField({ label, value }) {
  return (
    <div className="profile-field">
      <span>{label}</span>
      <strong>{value || '-'}</strong>
    </div>
  )
}

function UserManagePage() {
  const deferredKeyword = useDeferredValue('')
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [formLoading, setFormLoading] = useState(false)
  const [formError, setFormError] = useState('')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [pagination, setPagination] = useState({
    page: 0,
    size: 10,
    total: 0,
    totalPages: 1,
  })
  const [hospitals, setHospitals] = useState([])
  const [departments, setDepartments] = useState([])
  const [modal, setModal] = useState({ type: '', payload: null })
  const [userForm, setUserForm] = useState(createEmptyUserForm())
  const [resetPasswordForm, setResetPasswordForm] = useState({
    newPassword: '',
    confirmPassword: '',
  })

  useEffect(() => {
    authApi.getAllHospitals()
      .then((result) => setHospitals(Array.isArray(result) ? result : []))
      .catch((fetchError) => console.error('加载医院列表失败:', fetchError))
  }, [])

  useEffect(() => {
    if (!userForm.hospitalId) {
      setDepartments([])
      return
    }

    authApi.getHospitalDepartments(userForm.hospitalId)
      .then((result) => setDepartments(Array.isArray(result) ? result : []))
      .catch((fetchError) => console.error('加载科室列表失败:', fetchError))
  }, [userForm.hospitalId])

  const loadUsers = async (page = pagination.page) => {
    setLoading(true)
    setError('')
    try {
      const params = {
        page,
        size: pagination.size,
      }
      if (roleFilter) {
        params.role = roleFilter
      }

      const result = await authApi.getAllUsers(params)
      const parsed = parseCollectionResponse(result, 'users', pagination.size)
      setUsers(parsed.items)
      setPagination((current) => ({
        ...current,
        page,
        total: parsed.total,
        totalPages: parsed.totalPages,
      }))
    } catch (fetchError) {
      setError(fetchError.message || '获取用户列表失败。')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadUsers(0)
  }, [roleFilter])

  useEffect(() => {
    if (!deferredKeyword) {
      return
    }
  }, [deferredKeyword])

  const executeSearch = async () => {
    if (!searchKeyword.trim()) {
      loadUsers(0)
      return
    }

    setLoading(true)
    setError('')
    try {
      const result = await authApi.searchUsers(searchKeyword.trim())
      const parsed = parseCollectionResponse(result, 'users', pagination.size)
      setUsers(parsed.items)
      setPagination((current) => ({
        ...current,
        page: 0,
        total: parsed.total,
        totalPages: parsed.totalPages,
      }))
    } catch (fetchError) {
      setError(fetchError.message || '搜索失败。')
    } finally {
      setLoading(false)
    }
  }

  const openCreateModal = () => {
    setFormError('')
    setUserForm(createEmptyUserForm())
    setModal({ type: 'create', payload: null })
  }

  const openEditModal = (user) => {
    setFormError('')
    setUserForm({
      hospitalId: user.hospitalId || '',
      deptCode: user.deptCode || '',
      fullName: user.fullName || '',
      password: '',
      role: user.role || 'doctor',
      username: user.username || '',
      userSeq: user.userSeq || '',
    })
    setModal({ type: 'edit', payload: user })
  }

  const closeModal = () => {
    setModal({ type: '', payload: null })
    setFormError('')
    setResetPasswordForm({ newPassword: '', confirmPassword: '' })
    setUserForm(createEmptyUserForm())
  }

  const submitUser = async (event) => {
    event.preventDefault()
    setFormLoading(true)
    setFormError('')
    try {
      if (modal.type === 'edit') {
        await authApi.updateUser(modal.payload.id, {
          fullName: userForm.fullName,
          role: userForm.role,
        })
      } else {
        await authApi.register({
          hospitalId: userForm.hospitalId,
          deptCode: userForm.deptCode,
          fullName: userForm.fullName,
          password: userForm.password,
          role: userForm.role,
        })
      }

      await loadUsers(0)
      closeModal()
    } catch (submitError) {
      setFormError(submitError.message || '保存用户失败。')
    } finally {
      setFormLoading(false)
    }
  }

  const submitResetPassword = async (event) => {
    event.preventDefault()
    setFormError('')

    if (resetPasswordForm.newPassword !== resetPasswordForm.confirmPassword) {
      setFormError('两次输入的密码不一致。')
      return
    }

    setFormLoading(true)
    try {
      await authApi.resetPassword(modal.payload.id, resetPasswordForm.newPassword)
      closeModal()
      window.alert('密码重置成功。')
    } catch (submitError) {
      setFormError(submitError.message || '重置密码失败。')
    } finally {
      setFormLoading(false)
    }
  }

  const deleteUser = async () => {
    setFormLoading(true)
    setFormError('')
    try {
      await authApi.deleteUser(modal.payload.id)
      await loadUsers(0)
      closeModal()
    } catch (submitError) {
      setFormError(submitError.message || '删除用户失败。')
    } finally {
      setFormLoading(false)
    }
  }

  return (
    <>
      <section className="panel">
        <div className="section-header">
          <div>
            <h2>用户管理</h2>
            <p>支持按角色筛选、按关键字搜索，以及用户创建、编辑、重置密码和删除。</p>
          </div>
          <button type="button" className="button button-primary" onClick={openCreateModal}>
            新增用户
          </button>
        </div>

        <div className="toolbar-row">
          <input
            value={searchKeyword}
            onChange={(event) => setSearchKeyword(event.target.value)}
            placeholder="搜索姓名或用户名"
          />
          <select value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)}>
            <option value="">全部角色</option>
            <option value="doctor">医生</option>
            <option value="nurse">护士</option>
            <option value="receptionist">前台</option>
            <option value="admin">管理员</option>
          </select>
          <button type="button" className="button button-secondary" onClick={executeSearch}>
            搜索
          </button>
          <button type="button" className="button button-secondary" onClick={() => loadUsers(0)}>
            刷新
          </button>
        </div>

        {loading ? <LoadingBlock /> : null}
        {error ? <ErrorBlock message={error} /> : null}

        {!loading && !error ? (
          <>
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>姓名</th>
                    <th>用户名</th>
                    <th>角色</th>
                    <th>医院</th>
                    <th>科室</th>
                    <th>创建时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {users.length === 0 ? (
                    <tr>
                      <td colSpan="7">
                        <EmptyBlock title="暂无用户数据" hint="可通过右上角按钮新增用户。" />
                      </td>
                    </tr>
                  ) : (
                    users.map((user) => (
                      <tr key={user.id}>
                        <td>{user.fullName || '-'}</td>
                        <td>{user.username || '-'}</td>
                        <td>{roleLabel(user.role)}</td>
                        <td>{user.hospitalName || user.hospitalId || '-'}</td>
                        <td>{user.deptName || user.deptCode || '-'}</td>
                        <td>{formatDateTime(user.createdAt)}</td>
                        <td>
                          <TableActions>
                            <button type="button" className="button button-secondary small" onClick={() => setModal({ type: 'view', payload: user })}>
                              查看
                            </button>
                            <button type="button" className="button button-secondary small" onClick={() => openEditModal(user)}>
                              编辑
                            </button>
                            <button type="button" className="button button-secondary small" onClick={() => setModal({ type: 'reset-password', payload: user })}>
                              重置密码
                            </button>
                            <button type="button" className="button button-danger small" onClick={() => setModal({ type: 'delete', payload: user })}>
                              删除
                            </button>
                          </TableActions>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            <PaginationBar
              page={pagination.page}
              totalPages={pagination.totalPages}
              total={pagination.total}
              onPageChange={(page) => loadUsers(page)}
            />
          </>
        ) : null}
      </section>

      <Modal open={modal.type === 'create' || modal.type === 'edit'} title={modal.type === 'edit' ? '编辑用户' : '新增用户'} onClose={closeModal}>
        <form className="form-grid" onSubmit={submitUser}>
          <label className="field">
            <span>医院</span>
            <select
              value={userForm.hospitalId}
              disabled={modal.type === 'edit'}
              onChange={(event) => setUserForm((current) => ({ ...current, hospitalId: event.target.value, deptCode: '' }))}
              required
            >
              <option value="">请选择医院</option>
              {hospitals.map((hospital) => (
                <option key={hospital.id} value={hospital.id}>
                  {hospital.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>科室</span>
            <select
              value={userForm.deptCode}
              disabled={modal.type === 'edit'}
              onChange={(event) => setUserForm((current) => ({ ...current, deptCode: event.target.value }))}
              required
            >
              <option value="">请选择科室</option>
              {departments.map((department) => (
                <option key={department.id} value={department.id}>
                  {department.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>姓名</span>
            <input value={userForm.fullName} onChange={(event) => setUserForm((current) => ({ ...current, fullName: event.target.value }))} required />
          </label>
          <label className="field">
            <span>角色</span>
            <select value={userForm.role} onChange={(event) => setUserForm((current) => ({ ...current, role: event.target.value }))}>
              <option value="doctor">医生</option>
              <option value="nurse">护士</option>
              <option value="receptionist">前台</option>
              <option value="admin">管理员</option>
            </select>
          </label>
          {modal.type === 'create' ? (
            <label className="field field-span-2">
              <span>初始密码</span>
              <input
                type="password"
                value={userForm.password}
                onChange={(event) => setUserForm((current) => ({ ...current, password: event.target.value }))}
                required
              />
            </label>
          ) : null}
          {formError ? <div className="alert-banner error-banner">{formError}</div> : null}
          <div className="field-actions">
            <button type="submit" className="button button-primary" disabled={formLoading}>
              {formLoading ? '提交中...' : '保存'}
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={modal.type === 'view'} title="用户详情" onClose={closeModal}>
        {modal.payload ? (
          <div className="detail-grid">
            <ProfileField label="姓名" value={modal.payload.fullName} />
            <ProfileField label="用户名" value={modal.payload.username} />
            <ProfileField label="角色" value={roleLabel(modal.payload.role)} />
            <ProfileField label="医院" value={modal.payload.hospitalName || modal.payload.hospitalId} />
            <ProfileField label="科室" value={modal.payload.deptName || modal.payload.deptCode} />
            <ProfileField label="创建时间" value={formatDateTime(modal.payload.createdAt)} />
          </div>
        ) : null}
      </Modal>

      <Modal open={modal.type === 'reset-password'} title="重置密码" onClose={closeModal}>
        <form className="form-grid" onSubmit={submitResetPassword}>
          <label className="field">
            <span>新密码</span>
            <input
              type="password"
              value={resetPasswordForm.newPassword}
              onChange={(event) => setResetPasswordForm((current) => ({ ...current, newPassword: event.target.value }))}
              required
            />
          </label>
          <label className="field">
            <span>确认新密码</span>
            <input
              type="password"
              value={resetPasswordForm.confirmPassword}
              onChange={(event) => setResetPasswordForm((current) => ({ ...current, confirmPassword: event.target.value }))}
              required
            />
          </label>
          {formError ? <div className="alert-banner error-banner">{formError}</div> : null}
          <div className="field-actions">
            <button type="submit" className="button button-primary" disabled={formLoading}>
              {formLoading ? '提交中...' : '确认重置'}
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={modal.type === 'delete'} title="删除用户" onClose={closeModal}>
        <div className="confirm-stack">
          <p>确定要删除用户 “{modal.payload?.fullName || ''}” 吗？</p>
          {formError ? <div className="alert-banner error-banner">{formError}</div> : null}
          <div className="field-actions">
            <button type="button" className="button button-danger" disabled={formLoading} onClick={deleteUser}>
              {formLoading ? '删除中...' : '确认删除'}
            </button>
          </div>
        </div>
      </Modal>
    </>
  )
}

function createEmptyUserForm() {
  return {
    hospitalId: '',
    deptCode: '',
    fullName: '',
    password: '',
    role: 'doctor',
    username: '',
    userSeq: '',
  }
}

function HospitalManagePage() {
  const [activeTab, setActiveTab] = useState('hospitals')
  const [hospitals, setHospitals] = useState([])
  const [departments, setDepartments] = useState([])
  const [hospitalSearch, setHospitalSearch] = useState('')
  const [departmentSearch, setDepartmentSearch] = useState('')
  const [departmentHospitalId, setDepartmentHospitalId] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [formLoading, setFormLoading] = useState(false)
  const [formError, setFormError] = useState('')
  const [modal, setModal] = useState({ type: '', payload: null })
  const [hospitalForm, setHospitalForm] = useState({ id: '', name: '' })
  const [departmentForm, setDepartmentForm] = useState({ id: '', hospitalId: '', name: '' })

  const loadHospitals = async () => {
    setLoading(true)
    setError('')
    try {
      const result = await authApi.getAllHospitals()
      setHospitals(Array.isArray(result) ? result : [])
    } catch (fetchError) {
      setError(fetchError.message || '获取医院列表失败。')
    } finally {
      setLoading(false)
    }
  }

  const loadDepartments = async (hospitalId = departmentHospitalId) => {
    if (!hospitalId) {
      setDepartments([])
      return
    }

    setLoading(true)
    setError('')
    try {
      const result = await authApi.getHospitalDepartments(hospitalId)
      setDepartments(Array.isArray(result) ? result : [])
    } catch (fetchError) {
      setError(fetchError.message || '获取科室列表失败。')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadHospitals()
  }, [])

  const filteredHospitals = hospitals.filter((hospital) => {
    if (!hospitalSearch.trim()) {
      return true
    }
    return hospital.name?.includes(hospitalSearch.trim()) || hospital.id?.includes(hospitalSearch.trim())
  })

  const filteredDepartments = departments.filter((department) => {
    if (!departmentSearch.trim()) {
      return true
    }
    return department.name?.includes(departmentSearch.trim()) || department.id?.includes(departmentSearch.trim())
  })

  const closeModal = () => {
    setModal({ type: '', payload: null })
    setFormError('')
    setHospitalForm({ id: '', name: '' })
    setDepartmentForm({ id: '', hospitalId: '', name: '' })
  }

  const submitHospital = async (event) => {
    event.preventDefault()
    setFormLoading(true)
    setFormError('')
    try {
      if (modal.type === 'edit-hospital') {
        await authApi.updateHospital(modal.payload.id, { name: hospitalForm.name })
      } else {
        await authApi.createHospital(hospitalForm)
      }
      await loadHospitals()
      closeModal()
    } catch (submitError) {
      setFormError(submitError.message || '保存医院失败。')
    } finally {
      setFormLoading(false)
    }
  }

  const submitDepartment = async (event) => {
    event.preventDefault()
    setFormLoading(true)
    setFormError('')
    try {
      if (modal.type === 'edit-department') {
        await authApi.updateDepartment(modal.payload.id, { name: departmentForm.name })
      } else {
        await authApi.createDepartment(departmentForm)
      }
      await loadDepartments(departmentHospitalId || departmentForm.hospitalId)
      closeModal()
    } catch (submitError) {
      setFormError(submitError.message || '保存科室失败。')
    } finally {
      setFormLoading(false)
    }
  }

  const deleteCurrentItem = async () => {
    setFormLoading(true)
    setFormError('')
    try {
      if (modal.type === 'delete-hospital') {
        await authApi.deleteHospital(modal.payload.id)
        await loadHospitals()
      } else {
        await authApi.deleteDepartment(modal.payload.id)
        await loadDepartments()
      }
      closeModal()
    } catch (submitError) {
      setFormError(submitError.message || '删除失败。')
    } finally {
      setFormLoading(false)
    }
  }

  return (
    <>
      <section className="panel">
        <div className="section-header">
          <div>
            <h2>医院与科室管理</h2>
            <p>管理员可维护医院主数据和对应科室信息。</p>
          </div>
        </div>

        <div className="tab-bar">
          <button type="button" className={activeTab === 'hospitals' ? 'active' : ''} onClick={() => setActiveTab('hospitals')}>
            医院管理
          </button>
          <button type="button" className={activeTab === 'departments' ? 'active' : ''} onClick={() => setActiveTab('departments')}>
            科室管理
          </button>
        </div>

        {activeTab === 'hospitals' ? (
          <>
            <div className="toolbar-row">
              <input value={hospitalSearch} onChange={(event) => setHospitalSearch(event.target.value)} placeholder="搜索医院名称或 ID" />
              <button type="button" className="button button-secondary" onClick={loadHospitals}>
                刷新
              </button>
              <button type="button" className="button button-primary" onClick={() => setModal({ type: 'create-hospital', payload: null })}>
                新增医院
              </button>
            </div>
            {loading ? <LoadingBlock /> : null}
            {error ? <ErrorBlock message={error} /> : null}
            {!loading && !error ? (
              <div className="table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>名称</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredHospitals.length === 0 ? (
                      <tr>
                        <td colSpan="3">
                          <EmptyBlock title="暂无医院数据" />
                        </td>
                      </tr>
                    ) : (
                      filteredHospitals.map((hospital) => (
                        <tr key={hospital.id}>
                          <td>{hospital.id}</td>
                          <td>{hospital.name}</td>
                          <td>
                            <TableActions>
                              <button type="button" className="button button-secondary small" onClick={() => setModal({ type: 'view-hospital', payload: hospital })}>
                                查看
                              </button>
                              <button
                                type="button"
                                className="button button-secondary small"
                                onClick={() => {
                                  setHospitalForm({ id: hospital.id, name: hospital.name })
                                  setModal({ type: 'edit-hospital', payload: hospital })
                                }}
                              >
                                编辑
                              </button>
                              <button type="button" className="button button-danger small" onClick={() => setModal({ type: 'delete-hospital', payload: hospital })}>
                                删除
                              </button>
                            </TableActions>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            ) : null}
          </>
        ) : (
          <>
            <div className="toolbar-row">
              <select
                value={departmentHospitalId}
                onChange={(event) => {
                  const hospitalId = event.target.value
                  setDepartmentHospitalId(hospitalId)
                  loadDepartments(hospitalId)
                }}
              >
                <option value="">请选择医院</option>
                {hospitals.map((hospital) => (
                  <option key={hospital.id} value={hospital.id}>
                    {hospital.name}
                  </option>
                ))}
              </select>
              <input value={departmentSearch} onChange={(event) => setDepartmentSearch(event.target.value)} placeholder="搜索科室名称或 ID" />
              <button type="button" className="button button-secondary" onClick={() => loadDepartments()}>
                刷新
              </button>
              <button
                type="button"
                className="button button-primary"
                disabled={!departmentHospitalId}
                onClick={() => {
                  setDepartmentForm({ id: '', hospitalId: departmentHospitalId, name: '' })
                  setModal({ type: 'create-department', payload: null })
                }}
              >
                新增科室
              </button>
            </div>
            {loading ? <LoadingBlock /> : null}
            {error ? <ErrorBlock message={error} /> : null}
            {!loading && !error ? (
              <div className="table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>医院</th>
                      <th>名称</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {!departmentHospitalId ? (
                      <tr>
                        <td colSpan="4">
                          <EmptyBlock title="请先选择医院" />
                        </td>
                      </tr>
                    ) : filteredDepartments.length === 0 ? (
                      <tr>
                        <td colSpan="4">
                          <EmptyBlock title="暂无科室数据" />
                        </td>
                      </tr>
                    ) : (
                      filteredDepartments.map((department) => (
                        <tr key={department.id}>
                          <td>{department.id}</td>
                          <td>{hospitals.find((hospital) => hospital.id === department.hospitalId)?.name || department.hospitalId}</td>
                          <td>{department.name}</td>
                          <td>
                            <TableActions>
                              <button type="button" className="button button-secondary small" onClick={() => setModal({ type: 'view-department', payload: department })}>
                                查看
                              </button>
                              <button
                                type="button"
                                className="button button-secondary small"
                                onClick={() => {
                                  setDepartmentForm({ id: department.id, hospitalId: department.hospitalId, name: department.name })
                                  setModal({ type: 'edit-department', payload: department })
                                }}
                              >
                                编辑
                              </button>
                              <button type="button" className="button button-danger small" onClick={() => setModal({ type: 'delete-department', payload: department })}>
                                删除
                              </button>
                            </TableActions>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            ) : null}
          </>
        )}
      </section>

      <Modal
        open={modal.type === 'create-hospital' || modal.type === 'edit-hospital'}
        title={modal.type === 'edit-hospital' ? '编辑医院' : '新增医院'}
        onClose={closeModal}
      >
        <form className="form-grid" onSubmit={submitHospital}>
          {modal.type === 'create-hospital' ? (
            <label className="field">
              <span>医院 ID</span>
              <input value={hospitalForm.id} onChange={(event) => setHospitalForm((current) => ({ ...current, id: event.target.value }))} required />
            </label>
          ) : null}
          <label className="field">
            <span>医院名称</span>
            <input value={hospitalForm.name} onChange={(event) => setHospitalForm((current) => ({ ...current, name: event.target.value }))} required />
          </label>
          {formError ? <div className="alert-banner error-banner">{formError}</div> : null}
          <div className="field-actions">
            <button type="submit" className="button button-primary" disabled={formLoading}>
              {formLoading ? '提交中...' : '保存'}
            </button>
          </div>
        </form>
      </Modal>

      <Modal
        open={modal.type === 'create-department' || modal.type === 'edit-department'}
        title={modal.type === 'edit-department' ? '编辑科室' : '新增科室'}
        onClose={closeModal}
      >
        <form className="form-grid" onSubmit={submitDepartment}>
          {modal.type === 'create-department' ? (
            <label className="field">
              <span>科室 ID</span>
              <input value={departmentForm.id} onChange={(event) => setDepartmentForm((current) => ({ ...current, id: event.target.value }))} required />
            </label>
          ) : null}
          <label className="field">
            <span>所属医院</span>
            <select
              value={departmentForm.hospitalId}
              onChange={(event) => setDepartmentForm((current) => ({ ...current, hospitalId: event.target.value }))}
              required
            >
              <option value="">请选择医院</option>
              {hospitals.map((hospital) => (
                <option key={hospital.id} value={hospital.id}>
                  {hospital.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>科室名称</span>
            <input value={departmentForm.name} onChange={(event) => setDepartmentForm((current) => ({ ...current, name: event.target.value }))} required />
          </label>
          {formError ? <div className="alert-banner error-banner">{formError}</div> : null}
          <div className="field-actions">
            <button type="submit" className="button button-primary" disabled={formLoading}>
              {formLoading ? '提交中...' : '保存'}
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={modal.type === 'view-hospital'} title="医院详情" onClose={closeModal}>
        {modal.payload ? (
          <div className="detail-grid">
            <ProfileField label="ID" value={modal.payload.id} />
            <ProfileField label="名称" value={modal.payload.name} />
          </div>
        ) : null}
      </Modal>

      <Modal open={modal.type === 'view-department'} title="科室详情" onClose={closeModal}>
        {modal.payload ? (
          <div className="detail-grid">
            <ProfileField label="ID" value={modal.payload.id} />
            <ProfileField label="所属医院" value={hospitals.find((hospital) => hospital.id === modal.payload.hospitalId)?.name || modal.payload.hospitalId} />
            <ProfileField label="名称" value={modal.payload.name} />
          </div>
        ) : null}
      </Modal>

      <Modal open={modal.type === 'delete-hospital' || modal.type === 'delete-department'} title="确认删除" onClose={closeModal}>
        <div className="confirm-stack">
          <p>确定要删除 “{modal.payload?.name || ''}” 吗？</p>
          {formError ? <div className="alert-banner error-banner">{formError}</div> : null}
          <div className="field-actions">
            <button type="button" className="button button-danger" disabled={formLoading} onClick={deleteCurrentItem}>
              {formLoading ? '删除中...' : '确认删除'}
            </button>
          </div>
        </div>
      </Modal>
    </>
  )
}

function createEmptyPatientForm() {
  return {
    name: '',
    gender: 'M',
    birthDate: '',
    bloodType: 'Unknown',
    phone: '',
    idCard: '',
    doctorId: '',
    hospitalId: '',
    patientId: '',
    department: '',
    attendingDoctorId: '',
    attendingDoctorName: '',
    chiefComplaint: '',
    heightCm: '',
    weightKg: '',
    allergies: '',
    medicalHistory: '',
    familyHistory: '',
    firstVisitDate: '',
  }
}

function useCurrentMedicalContext() {
  const [currentHospital, setCurrentHospital] = useState(null)
  const [currentDoctor, setCurrentDoctor] = useState(null)

  const loadCurrentUserInfo = async () => {
    const currentUser = authStore.getCurrentUser()
    if (!currentUser) {
      setCurrentDoctor(null)
      setCurrentHospital(null)
      return createEmptyPatientForm()
    }

    const doctor = {
      ...currentUser,
      realName: currentUser.fullName || currentUser.realName || currentUser.name || currentUser.userName || '医生',
    }
    setCurrentDoctor(doctor)

    if (currentUser.hospitalId) {
      try {
        const hospital = await authApi.getHospitalById(currentUser.hospitalId)
        setCurrentHospital(hospital)
      } catch (_error) {
        setCurrentHospital({
          id: currentUser.hospitalId,
          name: `医院 ${currentUser.hospitalId}`,
        })
      }
    } else {
      setCurrentHospital({
        id: '',
        name: '未知医院',
      })
    }

    return {
      ...createEmptyPatientForm(),
      doctorId: currentUser.id || '',
      attendingDoctorId: currentUser.id || '',
      attendingDoctorName: doctor.realName,
      hospitalId: currentUser.hospitalId || '',
      department: currentUser.deptCode || '',
    }
  }

  return {
    currentHospital,
    currentDoctor,
    loadCurrentUserInfo,
  }
}

function PatientManagePage() {
  const { location } = useRouter()
  const searchParams = new URLSearchParams(location.search)
  const auth = useAuthState()
  const isAdmin = auth.user?.role === ADMIN_ROLE
  const isDoctor = auth.user?.role === 'doctor'
  const canModifyOrCreate = isAdmin || auth.user?.role === 'receptionist'
  const {
    currentHospital,
    currentDoctor,
    loadCurrentUserInfo,
  } = useCurrentMedicalContext()
  const [patients, setPatients] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [formLoading, setFormLoading] = useState(false)
  const [formError, setFormError] = useState('')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [pagination, setPagination] = useState({
    page: 0,
    size: 10,
    total: 0,
    totalPages: 1,
  })
  const [modal, setModal] = useState({ type: '', payload: null })
  const [patientForm, setPatientForm] = useState(createEmptyPatientForm())
  const [newStatus, setNewStatus] = useState('Active')

  const loadPatients = async (page = pagination.page) => {
    setLoading(true)
    setError('')
    try {
      const params = { page, size: pagination.size }
      if (statusFilter) {
        params.status = statusFilter
      }

      const result = await patientApi.getPatients(params)
      const parsed = parseCollectionResponse(result, 'patients', pagination.size)
      setPatients(parsed.items)
      setPagination((current) => ({
        ...current,
        page,
        total: parsed.total,
        totalPages: parsed.totalPages,
      }))
    } catch (fetchError) {
      setError(fetchError.message || '获取患者列表失败。')
      setPatients([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadCurrentUserInfo().then((form) => setPatientForm(form))
    loadPatients(0)
  }, [])

  useEffect(() => {
    loadPatients(0)
  }, [statusFilter])

  useEffect(() => {
    const action = searchParams.get('action')
    const id = searchParams.get('id')
    if (action !== 'edit' || !id) {
      return
    }

    patientApi.getPatientById(id)
      .then((patient) => openEditPatient(patient))
      .catch((fetchError) => {
        setError(fetchError.message || '加载患者信息失败。')
      })
  }, [location.search])

  const executeSearch = async () => {
    if (!searchKeyword.trim()) {
      loadPatients(0)
      return
    }

    setLoading(true)
    setError('')
    try {
      const result = await patientApi.searchPatients({
        keyword: searchKeyword.trim(),
        page: 0,
        size: 20,
        ...(statusFilter ? { status: statusFilter } : {}),
      })
      const parsed = parseCollectionResponse(result, 'patients', pagination.size)
      setPatients(parsed.items)
      setPagination((current) => ({
        ...current,
        page: 0,
        total: parsed.total,
        totalPages: parsed.totalPages,
      }))
    } catch (fetchError) {
      setError(fetchError.message || '搜索失败。')
      setPatients([])
    } finally {
      setLoading(false)
    }
  }

  const openCreatePatient = async () => {
    setFormError('')
    const baseForm = await loadCurrentUserInfo()
    setPatientForm(baseForm)
    setModal({ type: 'create', payload: null })
  }

  const openEditPatient = async (patient) => {
    const baseForm = await loadCurrentUserInfo()
    setPatientForm({
      ...baseForm,
      name: patient.name || '',
      gender: patient.gender || 'M',
      birthDate: patient.birthDate ? patient.birthDate.replace(/-/g, '/') : '',
      bloodType: patient.bloodType || 'Unknown',
      phone: patient.phone || '',
      idCard: patient.idCard || '',
      doctorId: patient.doctorId || baseForm.doctorId,
      hospitalId: patient.hospitalId || baseForm.hospitalId,
      patientId: patient.patientId || '',
      department: patient.department || baseForm.department,
      attendingDoctorId: patient.attendingDoctorId || patient.doctorId || baseForm.attendingDoctorId,
      attendingDoctorName: patient.attendingDoctorName || baseForm.attendingDoctorName,
      chiefComplaint: patient.chiefComplaint || '',
      heightCm: patient.heightCm ? `${patient.heightCm}` : '',
      weightKg: patient.weightKg ? `${patient.weightKg}` : '',
      allergies: patient.allergies || '',
      medicalHistory: patient.medicalHistory || '',
      familyHistory: patient.familyHistory || '',
      firstVisitDate: patient.firstVisitDate ? formatDateTimeForInput(patient.firstVisitDate) : '',
    })
    setModal({ type: 'edit', payload: patient })
  }

  const closeModal = async () => {
    setModal({ type: '', payload: null })
    setFormError('')
    setNewStatus('Active')
    const baseForm = await loadCurrentUserInfo()
    setPatientForm(baseForm)
  }

  const submitPatient = async (event) => {
    event.preventDefault()
    setFormLoading(true)
    setFormError('')

    try {
      if (!validatePhoneNumber(patientForm.phone)) {
        throw new Error('请输入有效的 11 位手机号。')
      }
      if (!patientForm.patientId.trim()) {
        throw new Error('请输入患者编号。')
      }
      if (!patientForm.name.trim()) {
        throw new Error('请输入患者姓名。')
      }

      const payload = {
        hospitalId: (patientForm.hospitalId || currentHospital?.id || '').trim(),
        patientId: patientForm.patientId.trim(),
        name: patientForm.name.trim(),
        gender: patientForm.gender,
        birthDate: patientForm.birthDate ? formatBirthDateForBackend(patientForm.birthDate) : null,
        phone: `${patientForm.phone || ''}`.replace(/\D/g, ''),
        idCard: patientForm.idCard ? patientForm.idCard.replace(/\s/g, '') : null,
        department: (patientForm.department || currentDoctor?.deptCode || '').trim(),
        attendingDoctorId: (patientForm.attendingDoctorId || patientForm.doctorId || currentDoctor?.id || '').trim(),
        chiefComplaint: patientForm.chiefComplaint.trim() || null,
        heightCm: patientForm.heightCm ? Number(patientForm.heightCm) : null,
        weightKg: patientForm.weightKg ? Number(patientForm.weightKg) : null,
        bloodType: patientForm.bloodType || 'Unknown',
        status: 'Active',
        allergies: patientForm.allergies.trim() || null,
        medicalHistory: patientForm.medicalHistory.trim() || null,
        familyHistory: patientForm.familyHistory.trim() || null,
        firstVisitDate: patientForm.firstVisitDate ? formatDateTimeForBackend(patientForm.firstVisitDate) : null,
      }

      if (modal.type === 'edit') {
        await patientApi.updatePatient(modal.payload.id, payload)
      } else {
        await patientApi.createPatient(payload)
      }

      await loadPatients(0)
      await closeModal()
    } catch (submitError) {
      setFormError(submitError.message || '保存患者失败。')
    } finally {
      setFormLoading(false)
    }
  }

  const submitStatus = async (event) => {
    event.preventDefault()
    setFormLoading(true)
    setFormError('')
    try {
      await patientApi.updateStatus(modal.payload.id, newStatus)
      await loadPatients(pagination.page)
      await closeModal()
    } catch (submitError) {
      setFormError(submitError.message || '更新状态失败。')
    } finally {
      setFormLoading(false)
    }
  }

  const deletePatient = async () => {
    setFormLoading(true)
    setFormError('')
    try {
      await patientApi.deletePatient(modal.payload.id)
      await loadPatients(0)
      await closeModal()
    } catch (submitError) {
      setFormError(submitError.message || '删除患者失败。')
    } finally {
      setFormLoading(false)
    }
  }

  return (
    <>
      <section className="panel">
        <div className="section-header">
          <div>
            <h2>患者管理</h2>
            <p>维护患者基础信息、就诊信息、状态和病史摘要。</p>
          </div>
          {canModifyOrCreate ? (
            <button type="button" className="button button-primary" onClick={openCreatePatient}>
              新增患者
            </button>
          ) : null}
        </div>

        <div className="toolbar-row">
          <input value={searchKeyword} onChange={(event) => setSearchKeyword(event.target.value)} placeholder="搜索患者姓名或患者编号" />
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
            <option value="">全部状态</option>
            <option value="Active">激活</option>
            <option value="Discharged">出院</option>
            <option value="Deceased">死亡</option>
          </select>
          <button type="button" className="button button-secondary" onClick={executeSearch}>
            搜索
          </button>
          <button type="button" className="button button-secondary" onClick={() => loadPatients(0)}>
            刷新
          </button>
        </div>

        {loading ? <LoadingBlock /> : null}
        {error ? <ErrorBlock message={error} /> : null}

        {!loading && !error ? (
          <>
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>患者姓名</th>
                    <th>性别</th>
                    <th>年龄</th>
                    <th>血型</th>
                    <th>主治医生</th>
                    <th>状态</th>
                    <th>最近就诊</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {patients.length === 0 ? (
                    <tr>
                      <td colSpan="8">
                        <EmptyBlock title="暂无患者数据" hint="可点击上方按钮创建患者档案。" />
                      </td>
                    </tr>
                  ) : (
                    patients.map((patient) => (
                      <tr key={patient.id}>
                        <td>{patient.name}</td>
                        <td>{formatGender(patient.gender)}</td>
                        <td>{patient.age || calculateAge(patient.birthDate)}</td>
                        <td>{formatBloodType(patient.bloodType)}</td>
                        <td>{patient.attendingDoctorName || patient.doctorName || patient.attendingDoctorId || patient.doctorId || '-'}</td>
                        <td><span className={statusClassName(patient.status)}>{formatPatientStatus(patient.status)}</span></td>
                        <td>{formatDateTime(patient.lastVisitDate)}</td>
                        <td>
                          <TableActions>
                            <button type="button" className="button button-secondary small" onClick={() => setModal({ type: 'view', payload: patient })}>
                              查看
                            </button>
                            {canModifyOrCreate ? (
                              <button type="button" className="button button-secondary small" onClick={() => openEditPatient(patient)}>
                                编辑
                              </button>
                            ) : null}
                            {canModifyOrCreate ? (
                              <button type="button" className="button button-secondary small" onClick={() => {
                                setNewStatus(patient.status || 'Active')
                                setModal({ type: 'status', payload: patient })
                              }}>
                                状态
                              </button>
                            ) : null}
                            {canModifyOrCreate ? (
                              <button type="button" className="button button-danger small" onClick={() => setModal({ type: 'delete', payload: patient })}>
                                删除
                              </button>
                            ) : null}
                          </TableActions>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <PaginationBar
              page={pagination.page}
              totalPages={pagination.totalPages}
              total={pagination.total}
              onPageChange={(page) => loadPatients(page)}
            />
          </>
        ) : null}
      </section>

      <Modal open={modal.type === 'create' || modal.type === 'edit'} title={modal.type === 'edit' ? '编辑患者' : '新增患者'} onClose={() => closeModal()} wide>
        <form className="form-grid two-column" onSubmit={submitPatient}>
          <label className="field">
            <span>患者姓名</span>
            <input value={patientForm.name} onChange={(event) => setPatientForm((current) => ({ ...current, name: event.target.value }))} required />
          </label>
          <label className="field">
            <span>性别</span>
            <select value={patientForm.gender} onChange={(event) => setPatientForm((current) => ({ ...current, gender: event.target.value }))}>
              <option value="M">男</option>
              <option value="F">女</option>
            </select>
          </label>
          <label className="field">
            <span>出生日期</span>
            <input value={patientForm.birthDate} readOnly placeholder="由身份证号自动生成" />
          </label>
          <label className="field">
            <span>身份证号</span>
            <input
              value={patientForm.idCard}
              disabled={modal.type === 'edit'}
              onChange={(event) => {
                const idCard = formatIdCard(event.target.value)
                setPatientForm((current) => ({
                  ...current,
                  idCard,
                  birthDate: deriveBirthDateFromIdCard(idCard),
                }))
              }}
            />
          </label>
          <label className="field">
            <span>联系电话</span>
            <input
              value={patientForm.phone}
              onChange={(event) => setPatientForm((current) => ({ ...current, phone: formatPhoneNumber(event.target.value) }))}
              required
            />
          </label>
          <label className="field">
            <span>血型</span>
            <select
              value={patientForm.bloodType}
              disabled={modal.type === 'edit'}
              onChange={(event) => setPatientForm((current) => ({ ...current, bloodType: event.target.value }))}
            >
              <option value="Unknown">未知</option>
              <option value="A">A型</option>
              <option value="B">B型</option>
              <option value="O">O型</option>
              <option value="AB">AB型</option>
            </select>
          </label>
          <label className="field">
            <span>患者编号</span>
            <input
              value={patientForm.patientId}
              disabled={modal.type === 'edit'}
              onChange={(event) => setPatientForm((current) => ({ ...current, patientId: event.target.value }))}
              required
            />
          </label>
          <label className="field">
            <span>所属医院</span>
            <input value={currentHospital?.name || patientForm.hospitalId || ''} readOnly />
          </label>
          <label className="field">
            <span>科室</span>
            <input value={currentDoctor?.deptName || currentDoctor?.deptCode || patientForm.department || ''} readOnly />
          </label>
          <label className="field">
            <span>主治医生</span>
            <input value={currentDoctor?.realName || patientForm.attendingDoctorName || '医生'} readOnly />
          </label>
          <label className="field field-span-2">
            <span>主诉</span>
            <textarea value={patientForm.chiefComplaint} onChange={(event) => setPatientForm((current) => ({ ...current, chiefComplaint: event.target.value }))} rows="3" />
          </label>
          <label className="field">
            <span>首次就诊时间</span>
            <input
              type="datetime-local"
              value={patientForm.firstVisitDate}
              onChange={(event) => setPatientForm((current) => ({ ...current, firstVisitDate: event.target.value }))}
            />
          </label>
          <label className="field">
            <span>身高 (cm)</span>
            <input type="number" value={patientForm.heightCm} onChange={(event) => setPatientForm((current) => ({ ...current, heightCm: event.target.value }))} />
          </label>
          <label className="field">
            <span>体重 (kg)</span>
            <input type="number" value={patientForm.weightKg} onChange={(event) => setPatientForm((current) => ({ ...current, weightKg: event.target.value }))} />
          </label>
          <label className="field field-span-2">
            <span>过敏史</span>
            <textarea value={patientForm.allergies} onChange={(event) => setPatientForm((current) => ({ ...current, allergies: event.target.value }))} rows="2" />
          </label>
          <label className="field field-span-2">
            <span>既往病史</span>
            <textarea value={patientForm.medicalHistory} onChange={(event) => setPatientForm((current) => ({ ...current, medicalHistory: event.target.value }))} rows="3" />
          </label>
          <label className="field field-span-2">
            <span>家族病史</span>
            <textarea value={patientForm.familyHistory} onChange={(event) => setPatientForm((current) => ({ ...current, familyHistory: event.target.value }))} rows="3" />
          </label>
          {formError ? <div className="alert-banner error-banner field-span-2">{formError}</div> : null}
          <div className="field-actions field-span-2">
            <button type="submit" className="button button-primary" disabled={formLoading}>
              {formLoading ? '提交中...' : '保存'}
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={modal.type === 'view'} title="患者详情" onClose={() => closeModal()}>
        {modal.payload ? (
          <div className="detail-grid">
            <ProfileField label="病历号" value={modal.payload.patientId || modal.payload.id} />
            <ProfileField label="患者姓名" value={modal.payload.name} />
            <ProfileField label="性别" value={formatGender(modal.payload.gender)} />
            <ProfileField label="年龄" value={modal.payload.age || calculateAge(modal.payload.birthDate)} />
            <ProfileField label="血型" value={formatBloodType(modal.payload.bloodType)} />
            <ProfileField label="联系电话" value={modal.payload.phone} />
            <ProfileField label="主治医生" value={modal.payload.attendingDoctorName || modal.payload.attendingDoctorId || modal.payload.doctorId} />
            <ProfileField label="所属医院" value={modal.payload.hospitalName || modal.payload.hospitalId} />
            <ProfileField label="状态" value={formatPatientStatus(modal.payload.status)} />
            <ProfileField label="入院时间" value={formatDateTime(modal.payload.admissionDate)} />
            <ProfileField label="更新时间" value={formatDateTime(modal.payload.updatedAt)} />
          </div>
        ) : null}
      </Modal>

      <Modal open={modal.type === 'status'} title="更改患者状态" onClose={() => closeModal()}>
        <form className="form-grid" onSubmit={submitStatus}>
          <label className="field">
            <span>新状态</span>
            <select value={newStatus} onChange={(event) => setNewStatus(event.target.value)}>
              <option value="Active">激活</option>
              <option value="Discharged">出院</option>
              <option value="Deceased">死亡</option>
            </select>
          </label>
          {formError ? <div className="alert-banner error-banner">{formError}</div> : null}
          <div className="field-actions">
            <button type="submit" className="button button-primary" disabled={formLoading}>
              {formLoading ? '提交中...' : '确认更改'}
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={modal.type === 'delete'} title="删除患者" onClose={() => closeModal()}>
        <div className="confirm-stack">
          <p>确定要删除患者 “{modal.payload?.name || ''}” 吗？</p>
          {formError ? <div className="alert-banner error-banner">{formError}</div> : null}
          <div className="field-actions">
            <button type="button" className="button button-danger" disabled={formLoading} onClick={deletePatient}>
              {formLoading ? '删除中...' : '确认删除'}
            </button>
          </div>
        </div>
      </Modal>
    </>
  )
}

function PatientListPage() {
  const { navigate } = useRouter()
  const auth = useAuthState()
  const canModifyOrCreate = auth.user?.role === ADMIN_ROLE || auth.user?.role === 'receptionist'
  const [patients, setPatients] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [formLoading, setFormLoading] = useState(false)
  const [formError, setFormError] = useState('')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [pagination, setPagination] = useState({
    page: 0,
    size: 10,
    total: 0,
    totalPages: 1,
  })
  const [modal, setModal] = useState({ type: '', payload: null })
  const [newStatus, setNewStatus] = useState('Active')

  const loadPatients = async (page = pagination.page) => {
    setLoading(true)
    setError('')
    try {
      const params = { page, size: pagination.size }
      if (statusFilter) {
        params.status = statusFilter
      }

      const result = await patientApi.getPatients(params)
      const parsed = parseCollectionResponse(result, 'patients', pagination.size)
      setPatients(parsed.items)
      setPagination((current) => ({
        ...current,
        page,
        total: parsed.total,
        totalPages: parsed.totalPages,
      }))
    } catch (fetchError) {
      setError(fetchError.message || '获取患者列表失败。')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadPatients(0)
  }, [statusFilter])

  const executeSearch = async () => {
    if (!searchKeyword.trim()) {
      loadPatients(0)
      return
    }

    setLoading(true)
    setError('')
    try {
      const result = await patientApi.searchPatients({
        keyword: searchKeyword.trim(),
        page: 0,
        size: 20,
        ...(statusFilter ? { status: statusFilter } : {}),
      })
      const parsed = parseCollectionResponse(result, 'patients', pagination.size)
      setPatients(parsed.items)
      setPagination((current) => ({
        ...current,
        page: 0,
        total: parsed.total,
        totalPages: parsed.totalPages,
      }))
    } catch (fetchError) {
      setError(fetchError.message || '搜索失败。')
    } finally {
      setLoading(false)
    }
  }

  const closeModal = () => {
    setModal({ type: '', payload: null })
    setFormError('')
    setNewStatus('Active')
  }

  const submitStatus = async (event) => {
    event.preventDefault()
    setFormLoading(true)
    try {
      await patientApi.updateStatus(modal.payload.id, newStatus)
      await loadPatients(pagination.page)
      closeModal()
    } catch (submitError) {
      setFormError(submitError.message || '更新状态失败。')
    } finally {
      setFormLoading(false)
    }
  }

  const deletePatient = async () => {
    setFormLoading(true)
    try {
      await patientApi.deletePatient(modal.payload.id)
      await loadPatients(0)
      closeModal()
    } catch (submitError) {
      setFormError(submitError.message || '删除患者失败。')
    } finally {
      setFormLoading(false)
    }
  }

  return (
    <>
      <section className="panel">
        <div className="section-header">
          <div>
            <h2>患者列表</h2>
            <p>按条件查看患者清单，支持详情查看、状态更新和跳转到患者管理页编辑。</p>
          </div>
          <AppLink to="/patients" className="button button-primary">
            管理患者
          </AppLink>
        </div>

        <div className="toolbar-row">
          <input value={searchKeyword} onChange={(event) => setSearchKeyword(event.target.value)} placeholder="搜索患者..." />
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
            <option value="">全部状态</option>
            <option value="Active">激活</option>
            <option value="Discharged">出院</option>
            <option value="Deceased">死亡</option>
          </select>
          <button type="button" className="button button-secondary" onClick={executeSearch}>
            搜索
          </button>
          <button type="button" className="button button-secondary" onClick={() => loadPatients(0)}>
            刷新
          </button>
        </div>

        {loading ? <LoadingBlock /> : null}
        {error ? <ErrorBlock message={error} /> : null}

        {!loading && !error ? (
          <>
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>患者姓名</th>
                    <th>性别</th>
                    <th>年龄</th>
                    <th>血型</th>
                    <th>主治医生</th>
                    <th>状态</th>
                    <th>最近就诊</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {patients.length === 0 ? (
                    <tr>
                      <td colSpan="8">
                        <EmptyBlock title="暂无患者数据" hint="进入患者管理页可以创建新的患者档案。" />
                      </td>
                    </tr>
                  ) : (
                    patients.map((patient) => (
                      <tr key={patient.id}>
                        <td>{patient.name}</td>
                        <td>{formatGender(patient.gender)}</td>
                        <td>{patient.age || calculateAge(patient.birthDate)}</td>
                        <td>{formatBloodType(patient.bloodType)}</td>
                        <td>{patient.attendingDoctorName || patient.attendingDoctorId || patient.doctorId || '-'}</td>
                        <td><span className={statusClassName(patient.status)}>{formatPatientStatus(patient.status)}</span></td>
                        <td>{formatDateTime(patient.lastVisitDate)}</td>
                        <td>
                          <TableActions>
                            <button type="button" className="button button-secondary small" onClick={() => setModal({ type: 'view', payload: patient })}>
                              查看
                            </button>
                            {canModifyOrCreate ? (
                              <button type="button" className="button button-secondary small" onClick={() => navigate(`/patients?action=edit&id=${encodeURIComponent(patient.id)}`)}>
                                编辑
                              </button>
                            ) : null}
                            {canModifyOrCreate ? (
                              <button type="button" className="button button-secondary small" onClick={() => {
                                setNewStatus(patient.status || 'Active')
                                setModal({ type: 'status', payload: patient })
                              }}>
                                状态
                              </button>
                            ) : null}
                            {canModifyOrCreate ? (
                              <button type="button" className="button button-danger small" onClick={() => setModal({ type: 'delete', payload: patient })}>
                                删除
                              </button>
                            ) : null}
                          </TableActions>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <PaginationBar page={pagination.page} totalPages={pagination.totalPages} total={pagination.total} onPageChange={(page) => loadPatients(page)} />
          </>
        ) : null}
      </section>

      <Modal open={modal.type === 'view'} title="患者详情" onClose={closeModal}>
        {modal.payload ? (
          <div className="detail-grid">
            <ProfileField label="病历号" value={modal.payload.patientId || modal.payload.id} />
            <ProfileField label="患者姓名" value={modal.payload.name} />
            <ProfileField label="性别" value={formatGender(modal.payload.gender)} />
            <ProfileField label="年龄" value={modal.payload.age || calculateAge(modal.payload.birthDate)} />
            <ProfileField label="血型" value={formatBloodType(modal.payload.bloodType)} />
            <ProfileField label="联系电话" value={modal.payload.phone} />
            <ProfileField label="主治医生" value={modal.payload.attendingDoctorName || modal.payload.attendingDoctorId || modal.payload.doctorId} />
            <ProfileField label="状态" value={formatPatientStatus(modal.payload.status)} />
            <ProfileField label="创建时间" value={formatDateTime(modal.payload.createdAt)} />
          </div>
        ) : null}
      </Modal>

      <Modal open={modal.type === 'status'} title="更改状态" onClose={closeModal}>
        <form className="form-grid" onSubmit={submitStatus}>
          <label className="field">
            <span>新状态</span>
            <select value={newStatus} onChange={(event) => setNewStatus(event.target.value)}>
              <option value="Active">激活</option>
              <option value="Discharged">出院</option>
              <option value="Deceased">死亡</option>
            </select>
          </label>
          {formError ? <div className="alert-banner error-banner">{formError}</div> : null}
          <div className="field-actions">
            <button type="submit" className="button button-primary" disabled={formLoading}>
              {formLoading ? '处理中...' : '确认更改'}
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={modal.type === 'delete'} title="删除患者" onClose={closeModal}>
        <div className="confirm-stack">
          <p>确定要删除患者 “{modal.payload?.name || ''}” 吗？</p>
          {formError ? <div className="alert-banner error-banner">{formError}</div> : null}
          <div className="field-actions">
            <button type="button" className="button button-danger" disabled={formLoading} onClick={deletePatient}>
              {formLoading ? '删除中...' : '确认删除'}
            </button>
          </div>
        </div>
      </Modal>
    </>
  )
}

function ReportManagePage() {
  const { navigate } = useRouter()

  return (
    <section className="panel">
      <div className="section-header">
        <div>
          <h2>报表管理</h2>
          <p>当前报表由影像分析结果驱动，建议从 Metric 报表管理进入患者维度的报表流程。</p>
        </div>
      </div>
      <div className="resource-grid">
        <article className="resource-card">
          <h3>进入 Metric 报表管理</h3>
          <p>按患者选择后查看或下载已有报表。</p>
          <button type="button" className="button button-primary" onClick={() => navigate('/metric/reports')}>
            前往
          </button>
        </article>
        <article className="resource-card">
          <h3>影像分析与报表生成</h3>
          <p>从医学影像管理进入 AI 分析页，确认结果后可直接生成报表。</p>
          <button type="button" className="button button-secondary" onClick={() => navigate('/metric/images')}>
            打开影像管理
          </button>
        </article>
      </div>
    </section>
  )
}

function MetricPatientSelectorPage({ title, onPatientClick }) {
  const [patients, setPatients] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [pagination, setPagination] = useState({
    page: 0,
    size: 10,
    total: 0,
    totalPages: 1,
  })

  const loadPatients = async (page = pagination.page) => {
    setLoading(true)
    setError('')
    try {
      const result = await patientApi.getPatients({
        page,
        size: pagination.size,
        ...(statusFilter ? { status: statusFilter } : {}),
      })
      const parsed = parseCollectionResponse(result, 'patients', pagination.size)
      setPatients(parsed.items)
      setPagination((current) => ({
        ...current,
        page,
        total: parsed.total,
        totalPages: parsed.totalPages,
      }))
    } catch (fetchError) {
      setError(fetchError.message || '获取患者列表失败。')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadPatients(0)
  }, [statusFilter])

  const executeSearch = async () => {
    if (!searchKeyword.trim()) {
      loadPatients(0)
      return
    }
    setLoading(true)
    setError('')
    try {
      const result = await patientApi.searchPatients({
        keyword: searchKeyword.trim(),
        page: 0,
        size: 20,
        ...(statusFilter ? { status: statusFilter } : {}),
      })
      const parsed = parseCollectionResponse(result, 'patients', pagination.size)
      setPatients(parsed.items)
      setPagination((current) => ({
        ...current,
        page: 0,
        total: parsed.total,
        totalPages: parsed.totalPages,
      }))
    } catch (fetchError) {
      setError(fetchError.message || '搜索失败。')
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="panel">
      <div className="section-header">
        <div>
          <h2>{title}</h2>
          <p>选择患者后进入对应的医学影像、分析结果或报表视图。</p>
        </div>
      </div>

      <div className="toolbar-row">
        <input value={searchKeyword} onChange={(event) => setSearchKeyword(event.target.value)} placeholder="搜索患者..." />
        <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
          <option value="">全部状态</option>
          <option value="Active">激活</option>
          <option value="Discharged">出院</option>
          <option value="Deceased">死亡</option>
        </select>
        <button type="button" className="button button-secondary" onClick={executeSearch}>
          搜索
        </button>
        <button type="button" className="button button-secondary" onClick={() => loadPatients(0)}>
          刷新
        </button>
      </div>

      {loading ? <LoadingBlock /> : null}
      {error ? <ErrorBlock message={error} /> : null}

      {!loading && !error ? (
        <>
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>患者姓名</th>
                  <th>性别</th>
                  <th>年龄</th>
                  <th>血型</th>
                  <th>主治医生</th>
                  <th>状态</th>
                  <th>最近就诊</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {patients.length === 0 ? (
                  <tr>
                    <td colSpan="8">
                      <EmptyBlock title="暂无患者数据" />
                    </td>
                  </tr>
                ) : (
                  patients.map((patient) => (
                    <tr key={patient.id}>
                      <td>{patient.name}</td>
                      <td>{formatGender(patient.gender)}</td>
                      <td>{patient.age || calculateAge(patient.birthDate)}</td>
                      <td>{formatBloodType(patient.bloodType)}</td>
                      <td>{patient.attendingDoctorName || patient.attendingDoctorId || patient.doctorId || '-'}</td>
                      <td><span className={statusClassName(patient.status)}>{formatPatientStatus(patient.status)}</span></td>
                      <td>{formatDateTime(patient.lastVisitDate)}</td>
                      <td>
                        <button type="button" className="button button-primary small" onClick={() => onPatientClick(patient)}>
                          查看
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <PaginationBar page={pagination.page} totalPages={pagination.totalPages} total={pagination.total} onPageChange={(page) => loadPatients(page)} />
        </>
      ) : null}
    </section>
  )
}

function MedicalImageManagePage() {
  const { navigate } = useRouter()

  return (
    <MetricPatientSelectorPage
      title="医学影像管理"
      onPatientClick={(patient) => navigate(`/metric/images/chat?patientId=${encodeURIComponent(patient.id)}&patientName=${encodeURIComponent(patient.name)}`)}
    />
  )
}

function AnalysisResultManagePage() {
  const { navigate } = useRouter()

  return (
    <MetricPatientSelectorPage
      title="分析结果管理"
      onPatientClick={(patient) => navigate(`/metric/analyses/detail?patientId=${encodeURIComponent(patient.id)}&patientName=${encodeURIComponent(patient.name)}`)}
    />
  )
}

function MetricReportManagePage() {
  const { navigate } = useRouter()

  return (
    <MetricPatientSelectorPage
      title="Metric 报表管理"
      onPatientClick={(patient) => navigate(`/metric/reports/list?patientId=${encodeURIComponent(patient.id)}&patientName=${encodeURIComponent(patient.name)}`)}
    />
  )
}

function AnalysisResultDetailPage() {
  const { goBack, location } = useRouter()
  const query = new URLSearchParams(location.search)
  const patientId = query.get('patientId') || ''
  const patientName = query.get('patientName') || '未知患者'
  const [patient, setPatient] = useState(null)
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [analysisTypeFilter, setAnalysisTypeFilter] = useState('')
  const [page, setPage] = useState(0)
  const [expandedId, setExpandedId] = useState('')
  const pageSize = 5

  useEffect(() => {
    let active = true

    const loadData = async () => {
      setLoading(true)
      setError('')
      try {
        const [patientResult, analysisResult] = await Promise.all([
          patientId ? patientApi.getPatientById(patientId).catch(() => null) : Promise.resolve(null),
          metricApi.getAnalysisResultsByPatientName(patientName),
        ])

        if (!active) {
          return
        }

        setPatient(patientResult)
        const rawList = Array.isArray(analysisResult) ? analysisResult : analysisResult?.analyses || []
        setResults(rawList.map(normalizeAnalysisResult))
      } catch (fetchError) {
        if (active) {
          setError(fetchError.message || '加载分析结果失败。')
          setResults([])
        }
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    loadData()

    return () => {
      active = false
    }
  }, [patientId, patientName])

  const filteredResults = results.filter((item) => {
    const matchesType = analysisTypeFilter ? item.analysisType === analysisTypeFilter : true
    const keyword = searchKeyword.trim().toLowerCase()
    const matchesSearch = keyword
      ? `${item.result} ${item.doctorDiagnosis || ''} ${item.detailSearchText || ''}`.toLowerCase().includes(keyword)
      : true
    return matchesType && matchesSearch
  })

  const pagedResults = filteredResults.slice(page * pageSize, page * pageSize + pageSize)
  const totalPages = Math.max(1, Math.ceil(filteredResults.length / pageSize))

  return (
    <div className="page-grid">
      <section className="panel">
        <div className="section-header">
          <div>
            <h2>分析结果详情</h2>
            <p>患者：{patientName}</p>
          </div>
          <button type="button" className="button button-secondary" onClick={goBack}>
            返回
          </button>
        </div>
        <div className="detail-grid">
          <ProfileField label="病历号" value={patientId || patient?.patientId || patient?.id} />
          <ProfileField label="姓名" value={patient?.name || patientName} />
          <ProfileField label="性别" value={formatGender(patient?.gender)} />
          <ProfileField label="年龄" value={patient?.age || calculateAge(patient?.birthDate)} />
          <ProfileField label="血型" value={formatBloodType(patient?.bloodType)} />
          <ProfileField label="主治医生" value={patient?.attendingDoctorName || patient?.attendingDoctorId || patient?.doctorId} />
          <ProfileField label="状态" value={formatPatientStatus(patient?.status)} />
          <ProfileField label="最近就诊" value={formatDateTime(patient?.lastVisitDate)} />
        </div>
      </section>

      <section className="panel">
        <div className="toolbar-row">
          <input value={searchKeyword} onChange={(event) => {
            setSearchKeyword(event.target.value)
            setPage(0)
          }} placeholder="搜索分析摘要、诊断或指标" />
          <select value={analysisTypeFilter} onChange={(event) => {
            setAnalysisTypeFilter(event.target.value)
            setPage(0)
          }}>
            <option value="">全部类型</option>
            {[...new Set(results.map((item) => item.analysisType).filter(Boolean))].map((item) => (
              <option key={item} value={item}>{item}</option>
            ))}
          </select>
        </div>

        {loading ? <LoadingBlock /> : null}
        {error ? <ErrorBlock message={error} /> : null}

        {!loading && !error ? (
          filteredResults.length === 0 ? (
            <EmptyBlock title="暂无分析结果" />
          ) : (
            <>
              <div className="result-stack">
                {pagedResults.map((item) => (
                  <article key={item.id} className="result-card">
                    <div className="result-card-top">
                      <div>
                        <h3>{item.analysisType}</h3>
                        <p>{formatDateTime(item.analysisDate)}</p>
                      </div>
                      <span className={`result-status ${item.status}`}>{item.statusText}</span>
                    </div>
                    <p className="result-summary-text">{item.result}</p>
                    <div className="result-metadata">
                      <span>置信度：{item.confidence != null ? `${item.confidence}%` : '未提供'}</span>
                      <span>医生诊断：{item.doctorDiagnosis || '暂无'}</span>
                    </div>
                    <button
                      type="button"
                      className="button button-secondary small"
                      onClick={() => setExpandedId((current) => current === item.id ? '' : item.id)}
                    >
                      {expandedId === item.id ? '收起详情' : '展开详情'}
                    </button>
                    {expandedId === item.id ? (
                      <div className="result-detail-grid">
                        {item.detailItems.length > 0 ? (
                          item.detailItems.map((detail) => (
                            <div key={`${item.id}-${detail.label}-${detail.value}`} className="metric-card">
                              <span>{detail.label}</span>
                              <strong>{detail.value}</strong>
                            </div>
                          ))
                        ) : (
                          <pre className="report-preview">{item.rawMetricsText || '暂无结构化指标。'}</pre>
                        )}
                      </div>
                    ) : null}
                  </article>
                ))}
              </div>
              <PaginationBar page={page} totalPages={totalPages} total={filteredResults.length} onPageChange={setPage} />
            </>
          )
        ) : null}
      </section>
    </div>
  )
}

function normalizeAnalysisResult(result) {
  const metrics = result.metrics && typeof result.metrics === 'object' ? result.metrics : null
  const detailItems = metrics
    ? Object.entries(metrics)
      .filter(([key, value]) => key !== 'kind' && value != null && value !== '')
      .flatMap(([key, value]) => {
        if (Array.isArray(value)) {
          return [{ label: key, value: value.join('、') }]
        }
        if (value && typeof value === 'object') {
          return Object.entries(value).map(([nestedKey, nestedValue]) => ({
            label: `${key}.${nestedKey}`,
            value: nestedValue == null ? '-' : `${nestedValue}`,
          }))
        }
        return [{ label: key, value: `${value}` }]
      })
    : []

  const confidence = typeof metrics?.confidence === 'number'
    ? Math.round(metrics.confidence <= 1 ? metrics.confidence * 100 : metrics.confidence)
    : null

  const summary = result.summary
    || detailItems.slice(0, 3).map((item) => `${item.label}: ${item.value}`).join('，')
    || result.errorMessage
    || '暂无分析摘要'

  return {
    id: result.id,
    analysisType: result.imageType || metrics?.imageType || metrics?.type || '影像分析',
    analysisDate: result.completedAt || result.createdAt,
    result: summary,
    doctorDiagnosis: result.doctorDiagnosis || result.diagnosis || '',
    detailItems,
    rawMetricsText: result.metrics ? JSON.stringify(result.metrics, null, 2) : '',
    detailSearchText: detailItems.map((item) => `${item.label} ${item.value}`).join(' '),
    confidence,
    status: result.status || 'completed',
    statusText: {
      completed: '已完成',
      success: '已完成',
      pending: '待处理',
      running: '分析中',
      failed: '失败',
    }[result.status] || result.status || '已完成',
  }
}

function ReportListPage() {
  const { goBack, location } = useRouter()
  const query = new URLSearchParams(location.search)
  const patientName = query.get('patientName') || '未知患者'
  const [reports, setReports] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [reportTypeFilter, setReportTypeFilter] = useState('')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [page, setPage] = useState(0)
  const [activeReportId, setActiveReportId] = useState('')
  const pageSize = 5

  useEffect(() => {
    let active = true
    setLoading(true)
    metricApi.getReportsByPatientName(patientName)
      .then((result) => {
        if (!active) {
          return
        }
        const rawList = Array.isArray(result) ? result : result?.reports || []
        setReports(rawList.map((report) => ({
          id: report.id,
          reportTitle: `${report.patientName || patientName} ${report.reportType || '报表'}`,
          reportType: report.reportType || '未分类',
          reportDate: report.generatedAt || report.createdAt,
          reportContent: report.errorMessage || report.filePath || '暂无报表内容预览',
          status: report.status || 'unknown',
          statusText: {
            completed: '已完成',
            generated: '已生成',
            pending: '处理中',
            failed: '失败',
          }[report.status] || report.status || '未知状态',
          filePath: report.filePath || '',
          fileName: report.filePath ? report.filePath.split('/').pop() : '',
        })))
      })
      .catch((fetchError) => {
        if (active) {
          setError(fetchError.message || '加载报表失败。')
          setReports([])
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [patientName])

  const filteredReports = reports.filter((report) => {
    const matchesType = reportTypeFilter ? report.reportType === reportTypeFilter : true
    const keyword = searchKeyword.trim().toLowerCase()
    const matchesKeyword = keyword
      ? `${report.reportTitle} ${report.reportContent}`.toLowerCase().includes(keyword)
      : true
    return matchesType && matchesKeyword
  })

  const pagedReports = filteredReports.slice(page * pageSize, page * pageSize + pageSize)
  const totalPages = Math.max(1, Math.ceil(filteredReports.length / pageSize))

  const openReportFile = async (report, disposition) => {
    if (!report.filePath) {
      return
    }

    setActiveReportId(report.id)
    try {
      const { blob, fileName } = await metricApi.getReportFile(report.id, disposition)
      const blobUrl = URL.createObjectURL(blob)
      if (disposition === 'attachment') {
        const link = document.createElement('a')
        link.href = blobUrl
        link.download = fileName || report.fileName || `${report.reportTitle}.pdf`
        document.body.appendChild(link)
        link.click()
        link.remove()
      } else {
        window.open(blobUrl, '_blank', 'noopener,noreferrer')
      }
      window.setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000)
    } catch (fetchError) {
      window.alert(fetchError.message || '获取报表文件失败。')
    } finally {
      setActiveReportId('')
    }
  }

  return (
    <div className="page-grid">
      <section className="panel">
        <div className="section-header">
          <div>
            <h2>报表列表</h2>
            <p>患者：{patientName}</p>
          </div>
          <button type="button" className="button button-secondary" onClick={goBack}>
            返回
          </button>
        </div>

        <div className="toolbar-row">
          <input value={searchKeyword} onChange={(event) => {
            setSearchKeyword(event.target.value)
            setPage(0)
          }} placeholder="搜索报表标题或内容" />
          <select value={reportTypeFilter} onChange={(event) => {
            setReportTypeFilter(event.target.value)
            setPage(0)
          }}>
            <option value="">全部类型</option>
            {[...new Set(reports.map((report) => report.reportType).filter(Boolean))].map((reportType) => (
              <option key={reportType} value={reportType}>{reportType}</option>
            ))}
          </select>
        </div>

        {loading ? <LoadingBlock /> : null}
        {error ? <ErrorBlock message={error} /> : null}

        {!loading && !error ? (
          filteredReports.length === 0 ? (
            <EmptyBlock title="暂无报表数据" />
          ) : (
            <>
              <div className="result-stack">
                {pagedReports.map((report) => (
                  <article key={report.id} className="result-card">
                    <div className="result-card-top">
                      <div>
                        <h3>{report.reportTitle}</h3>
                        <p>{formatDateTime(report.reportDate)}</p>
                      </div>
                      <span className={`result-status ${report.status}`}>{report.statusText}</span>
                    </div>
                    <p className="result-summary-text">{report.reportContent}</p>
                    <div className="table-actions">
                      <button type="button" className="button button-secondary small" disabled={!report.filePath || activeReportId === report.id} onClick={() => openReportFile(report, 'inline')}>
                        预览
                      </button>
                      <button type="button" className="button button-primary small" disabled={!report.filePath || activeReportId === report.id} onClick={() => openReportFile(report, 'attachment')}>
                        下载
                      </button>
                    </div>
                  </article>
                ))}
              </div>
              <PaginationBar page={page} totalPages={totalPages} total={filteredReports.length} onPageChange={setPage} />
            </>
          )
        ) : null}
      </section>
    </div>
  )
}

function MedicalImageChatPage() {
  const { goBack, location, navigate } = useRouter()
  const query = new URLSearchParams(location.search)
  const patientId = query.get('patientId') || ''
  const patientName = query.get('patientName') || '未知患者'
  const [messages, setMessages] = useState([])
  const [inputMessage, setInputMessage] = useState('')
  const [loading, setLoading] = useState(false)
  const [imageType, setImageType] = useState('XRAY')
  const [socketStatus, setSocketStatus] = useState('connecting')
  const [confirmedResultMessageId, setConfirmedResultMessageId] = useState('')
  const [generatingReportMessageId, setGeneratingReportMessageId] = useState('')
  const [historyReady, setHistoryReady] = useState(false)
  const socketRef = useRef(null)
  const chatScrollRef = useRef(null)
  const uploadInputRef = useRef(null)
  const persistTimerRef = useRef(null)
  const suppressPersistenceRef = useRef(false)

  const currentUser = authStore.getCurrentUser()

  const createRequestId = () => `req-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
  const getHospitalId = () => currentUser?.hospitalId || 'unknown-hospital'
  const getConversationId = () => patientId || patientName || 'unknown-patient'
  const buildImageMessageId = (requestId) => `img-${requestId}`
  const buildResultMessageId = (requestId) => `result-${requestId}`
  const estimateDataUrlBytes = (value) => {
    if (typeof value !== 'string' || !value.startsWith('data:')) {
      return 0
    }
    const payload = value.slice(value.indexOf(',') + 1)
    return Math.max(0, Math.floor(payload.length * 3 / 4))
  }
  const loadImageElement = (source) => new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('图片预览解码失败'))
    image.src = source
  })
  const createPersistableImagePreview = async (source) => {
    if (typeof source !== 'string' || !source.startsWith('data:')) {
      return source
    }

    const sourceBytes = estimateDataUrlBytes(source)
    if (sourceBytes > 0 && sourceBytes <= 360 * 1024) {
      return source
    }

    try {
      const image = await loadImageElement(source)
      const width = image.naturalWidth || image.width
      const height = image.naturalHeight || image.height
      if (!width || !height) {
        return source
      }

      const attempts = [
        { maxEdge: 960, mimeType: 'image/jpeg', quality: 0.82 },
        { maxEdge: 768, mimeType: 'image/jpeg', quality: 0.72 },
        { maxEdge: 512, mimeType: 'image/jpeg', quality: 0.6 },
      ]

      let bestPreview = source
      for (const attempt of attempts) {
        const scale = Math.min(1, attempt.maxEdge / Math.max(width, height))
        const canvas = document.createElement('canvas')
        canvas.width = Math.max(1, Math.round(width * scale))
        canvas.height = Math.max(1, Math.round(height * scale))
        const context = canvas.getContext('2d')
        if (!context) {
          continue
        }
        context.drawImage(image, 0, 0, canvas.width, canvas.height)
        const preview = canvas.toDataURL(attempt.mimeType, attempt.quality)
        if (preview.length < bestPreview.length) {
          bestPreview = preview
        }
        if (estimateDataUrlBytes(preview) <= 220 * 1024) {
          return preview
        }
      }

      return bestPreview
    } catch (_error) {
      return source
    }
  }
  const sanitizeMessageForPersistence = async (message) => {
    const next = { ...message }
    const persistedContent = typeof next.persistedContent === 'string' && next.persistedContent
      ? next.persistedContent
      : await createPersistableImagePreview(next.content)
    const sourceImagePreview = typeof next.sourceImagePreview === 'string' && next.sourceImagePreview
      ? next.sourceImagePreview
      : await createPersistableImagePreview(next.sourceImage)
    delete next.saving
    delete next.persistedContent
    delete next.sourceImagePreview

    if (next.type === 'image' && persistedContent) {
      next.content = persistedContent
    }

    if (next.type === 'ai_result' && sourceImagePreview) {
      next.sourceImage = sourceImagePreview
    }

    return next
  }

  const scrollToBottom = () => {
    window.setTimeout(() => {
      if (chatScrollRef.current) {
        chatScrollRef.current.scrollTop = chatScrollRef.current.scrollHeight
      }
    }, 80)
  }

  const persistConversation = async () => {
    if (!historyReady || suppressPersistenceRef.current) {
      return
    }

    const serializableMessages = await Promise.all(
      messages
        .filter((message) => !message.ephemeral)
        .map(sanitizeMessageForPersistence),
    )

    await metricApi.saveConversationHistory(getConversationId(), {
      conversationId: getConversationId(),
      patientName,
      hospitalId: getHospitalId(),
      confirmedResultMessageId: confirmedResultMessageId || null,
      messages: serializableMessages,
      updatedAt: new Date().toISOString(),
    })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  useEffect(() => {
    if (!historyReady || suppressPersistenceRef.current) {
      return
    }

    if (persistTimerRef.current) {
      window.clearTimeout(persistTimerRef.current)
    }

    persistTimerRef.current = window.setTimeout(() => {
      persistConversation().catch((error) => {
        console.error('保存对话历史失败:', error)
      })
    }, 300)

    return () => {
      if (persistTimerRef.current) {
        window.clearTimeout(persistTimerRef.current)
      }
    }
  }, [messages, confirmedResultMessageId, historyReady])

  useEffect(() => {
    let active = true

    const normalizeConfidence = (value) => {
      if (typeof value !== 'number' || Number.isNaN(value) || value <= 0) {
        return null
      }
      const normalized = value <= 1 ? value * 100 : value
      return Math.round(normalized)
    }

    const isRecord = (value) => value != null && typeof value === 'object' && !Array.isArray(value)

    const formatValue = (value) => {
      if (typeof value === 'number') {
        return Number.isInteger(value) ? `${value}` : `${Number(value.toFixed(1))}`
      }
      if (Array.isArray(value)) {
        return value.map(formatValue).join('、')
      }
      return value == null ? '-' : `${value}`
    }

    const extractMetricIndicators = (metrics) => {
      if (!isRecord(metrics)) {
        return []
      }
      if (Array.isArray(metrics.metrics)) {
        return metrics.metrics.flatMap(extractMetricIndicators)
      }
      if (typeof metrics.name === 'string' && metrics.value != null) {
        return [{
          name: metrics.name,
          value: formatValue(metrics.value),
          unit: typeof metrics.unit === 'string' ? metrics.unit : '',
          interpretation: typeof metrics.referenceRange === 'string' ? metrics.referenceRange : '',
        }]
      }
      return Object.entries(metrics)
        .filter(([key, value]) => key !== 'kind' && key !== 'confidence' && value != null && value !== '')
        .map(([key, value]) => ({
          name: key,
          value: formatValue(value),
          unit: '',
          interpretation: '',
        }))
    }

    const normalizeTextList = (value) => Array.isArray(value)
      ? value.map((item) => (typeof item === 'string' ? item.trim() : '')).filter(Boolean)
      : []

    const normalizeIndicatorItems = (value) => Array.isArray(value)
      ? value.map((item) => {
        if (!isRecord(item) || typeof item.name !== 'string') {
          return null
        }
        return {
          name: item.name,
          value: typeof item.value === 'string' ? item.value : formatValue(item.value),
          unit: typeof item.unit === 'string' ? item.unit : '',
          interpretation: typeof item.interpretation === 'string' ? item.interpretation : '',
        }
      }).filter(Boolean)
      : []

    const normalizePercent = (value) => {
      const numeric = Number(value)
      if (!Number.isFinite(numeric)) {
        return 0
      }
      return Math.max(0, Math.min(100, numeric))
    }

    const normalizeHexColor = (value, fallback = '#2563EB') => {
      if (typeof value !== 'string') {
        return fallback
      }
      const normalized = value.trim()
      return /^#[\dA-Fa-f]{6}$/.test(normalized) ? normalized.toUpperCase() : fallback
    }

    const normalizeHighlightRegions = (value) => Array.isArray(value)
      ? value.map((region, index) => {
        if (!isRecord(region) || !isRecord(region.boundingBox)) {
          return null
        }
        return {
          id: typeof region.id === 'string' ? region.id : `lesion-${index + 1}`,
          label: typeof region.label === 'string' ? region.label : `L${index + 1}`,
          colorName: typeof region.colorName === 'string' ? region.colorName : `区域 ${index + 1}`,
          colorHex: normalizeHexColor(region.colorHex),
          priority: Number.isFinite(Number(region.priority)) ? Number(region.priority) : index + 1,
          annotationTitle: typeof region.annotationTitle === 'string' ? region.annotationTitle : '高亮区域',
          annotationMeaning: typeof region.annotationMeaning === 'string' ? region.annotationMeaning : '',
          location: typeof region.location === 'string' ? region.location : '未标注位置',
          severity: typeof region.severity === 'string' ? region.severity : '',
          confidence: normalizeConfidence(region.confidence),
          coveragePercent: normalizePercent(region.coveragePercent),
          estimatedSizeMm: typeof region.estimatedSizeMm === 'number'
            ? Number(region.estimatedSizeMm.toFixed(1))
            : null,
          shape: typeof region.shape === 'string' ? region.shape.toLowerCase() : 'blob',
          rotationDegrees: Number.isFinite(Number(region.rotationDegrees))
            ? Number(region.rotationDegrees)
            : 0,
          contour: Array.isArray(region.contour)
            ? region.contour.map((point) => {
              if (!isRecord(point)) {
                return null
              }
              return {
                xPercent: normalizePercent(point.xPercent),
                yPercent: normalizePercent(point.yPercent),
              }
            }).filter(Boolean)
            : [],
          note: typeof region.note === 'string' ? region.note : '',
          boundingBox: {
            leftPercent: normalizePercent(region.boundingBox.leftPercent),
            topPercent: normalizePercent(region.boundingBox.topPercent),
            widthPercent: normalizePercent(region.boundingBox.widthPercent),
            heightPercent: normalizePercent(region.boundingBox.heightPercent),
          },
        }
      }).filter(Boolean)
      : []

    const normalizeHighlightLegend = (value) => Array.isArray(value)
      ? value.map((item, index) => {
        if (!isRecord(item)) {
          return null
        }
        return {
          colorName: typeof item.colorName === 'string' ? item.colorName : `标记 ${index + 1}`,
          colorHex: normalizeHexColor(item.colorHex),
          meaning: typeof item.meaning === 'string' ? item.meaning : '',
        }
      }).filter(Boolean)
      : []

    const formatAnalysisModeLabel = (value) => {
      if (value === 'PIXEL') {
        return '像素级分析'
      }
      if (value === 'METADATA_ONLY') {
        return '元数据级分析'
      }
      return typeof value === 'string' ? value : ''
    }

    const parseStructuredPayload = (rawPayload) => {
      if (typeof rawPayload !== 'string' || !rawPayload.trim()) {
        return null
      }

      try {
        const parsed = JSON.parse(rawPayload)
        if (!isRecord(parsed)) {
          return null
        }

        if (typeof parsed.errorCode === 'string') {
          return {
            kind: 'error',
            analysis: null,
            report: null,
            summary: parsed.message || '分析失败',
            analysisModeLabel: '',
            keyIndicators: [],
            findings: [],
            recommendations: [],
            highlightRegions: [],
            highlightLegend: [],
            limitations: typeof parsed.detail === 'string' ? [parsed.detail] : [],
            conclusion: '',
            reportContent: '',
          }
        }

        const analysis = isRecord(parsed.analysis) ? parsed.analysis : null
        if (!analysis) {
          return null
        }

        const explicitIndicators = normalizeIndicatorItems(parsed.keyIndicators)
        const fallbackIndicators = extractMetricIndicators(analysis.metrics)
        const highlightRegions = normalizeHighlightRegions(parsed.highlightRegions)
        const explicitLegend = normalizeHighlightLegend(parsed.highlightLegend)
        const highlightLegend = explicitLegend.length
          ? explicitLegend
          : highlightRegions.map((region) => ({
            colorName: region.colorName,
            colorHex: region.colorHex,
            meaning: region.note || `第 ${region.priority} 优先级复核区域`,
          }))

        return {
          kind: isRecord(parsed.report) ? 'generated_report' : 'analysis',
          analysis,
          report: isRecord(parsed.report) ? parsed.report : null,
          summary: typeof parsed.summary === 'string'
            ? parsed.summary
            : (explicitIndicators.length ? explicitIndicators : fallbackIndicators).slice(0, 3).map((item) => `${item.name}: ${item.value}`).join('，'),
          analysisModeLabel: formatAnalysisModeLabel(parsed.analysisMode),
          keyIndicators: explicitIndicators.length ? explicitIndicators : fallbackIndicators,
          findings: normalizeTextList(parsed.findings),
          recommendations: normalizeTextList(parsed.recommendations),
          highlightRegions,
          highlightLegend,
          limitations: normalizeTextList(parsed.limitations),
          conclusion: typeof parsed.conclusion === 'string' ? parsed.conclusion : '',
          reportContent: typeof parsed.reportContent === 'string' ? parsed.reportContent : '',
        }
      } catch (_error) {
        return null
      }
    }

    const restoreHistoryMessage = (message) => {
      if (!isRecord(message) || typeof message.type !== 'string') {
        return null
      }
      const normalizedRequestId = typeof message.requestId === 'string' ? message.requestId : ''
      const normalizedId = (() => {
        if (message.type === 'image' && normalizedRequestId) {
          return buildImageMessageId(normalizedRequestId)
        }
        if (message.type === 'ai_result' && normalizedRequestId) {
          return buildResultMessageId(normalizedRequestId)
        }
        return typeof message.id === 'string' ? message.id : createRequestId()
      })()
      return {
        ...message,
        id: normalizedId,
        saving: false,
      }
    }

    const disconnectSocket = () => {
      if (socketRef.current) {
        socketRef.current.onopen = null
        socketRef.current.onmessage = null
        socketRef.current.onclose = null
        socketRef.current.onerror = null
        socketRef.current.close()
        socketRef.current = null
      }
    }

    const connectSocket = () => {
      disconnectSocket()
      const socket = connectMetricAiSocket()
      socketRef.current = socket
      setSocketStatus('connecting')

      socket.onopen = () => {
        setSocketStatus('connected')
        setMessages((current) => [
          ...current,
          {
            id: createRequestId(),
            role: 'system-message',
            type: 'text',
            content: 'AI 会话已连接，可继续上传医学影像或咨询分析结果。',
            ephemeral: true,
          },
        ])
      }

      socket.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data)

          if (payload.type === 'processing') {
            setLoading(true)
            return
          }

          if (payload.type === 'connected') {
            return
          }

          if (payload.type === 'error') {
            setLoading(false)
            setMessages((current) => [
              ...current,
              {
                id: createRequestId(),
                role: 'system-message',
                type: 'text',
                content: payload.message || 'AI 会话处理失败，请稍后重试。',
              },
            ])
            return
          }

          if (payload.type === 'ai_response') {
            setLoading(false)
            if (payload.analysisResult) {
              const structuredPayload = parseStructuredPayload(payload.analysisResult)
              const confidence = structuredPayload?.analysis
                ? normalizeConfidence(structuredPayload.analysis.metrics?.confidence || payload.confidence)
                : normalizeConfidence(payload.confidence)
              setMessages((current) => {
                const matchedImageMessage = current
                  .slice()
                  .reverse()
                  .find((item) => item.type === 'image' && item.requestId === (payload.requestId || ''))

                return [
                  ...current,
                  {
                    id: buildResultMessageId(payload.requestId || createRequestId()),
                    requestId: payload.requestId || '',
                    role: 'ai-message',
                    type: 'ai_result',
                    content: structuredPayload?.summary || payload.analysisResult || payload.message || 'AI 已完成分析。',
                    confidence,
                    structuredPayload,
                    sourceImage: matchedImageMessage?.content || null,
                    sourceImagePreview: matchedImageMessage?.persistedContent || matchedImageMessage?.content || null,
                    persistableAnalysis: Boolean(structuredPayload?.analysis),
                    confirmed: false,
                    saving: false,
                    savedAnalysisId: '',
                    reportSaved: false,
                  },
                ]
              })
            } else {
              setMessages((current) => [
                ...current,
                {
                  id: createRequestId(),
                  role: 'ai-message',
                  type: 'text',
                  content: payload.message || 'AI 已收到请求。',
                },
              ])
            }
          }
        } catch (_error) {
          setLoading(false)
          setMessages((current) => [
            ...current,
            {
              id: createRequestId(),
              role: 'system-message',
              type: 'text',
              content: '收到无法解析的服务端消息。',
            },
          ])
        }
      }

      socket.onclose = () => {
        setSocketStatus('disconnected')
      }

      socket.onerror = () => {
        setSocketStatus('error')
        setLoading(false)
      }
    }

    const syncConversationContext = async () => {
      setHistoryReady(false)
      try {
        const response = await metricApi.getConversationHistory(getConversationId(), {
          hospitalId: getHospitalId(),
          patientName,
        })
        if (!active) {
          return
        }
        const restoredMessages = Array.isArray(response?.messages)
          ? response.messages.map(restoreHistoryMessage).filter(Boolean)
          : []
        const rawConfirmedId = typeof response?.confirmedResultMessageId === 'string' ? response.confirmedResultMessageId : ''
        const normalizedConfirmedId = restoredMessages.find((message) => message.id === rawConfirmedId)?.id
          || restoredMessages.find((message) => message.type === 'ai_result' && message.requestId === rawConfirmedId)?.id
          || ''
        setMessages(restoredMessages)
        setConfirmedResultMessageId(normalizedConfirmedId)
      } catch (error) {
        console.error('加载对话历史失败:', error)
        if (active) {
          setMessages([])
          setConfirmedResultMessageId('')
        }
      } finally {
        if (active) {
          setHistoryReady(true)
          connectSocket()
          scrollToBottom()
        }
      }
    }

    syncConversationContext()

    return () => {
      active = false
      if (persistTimerRef.current) {
        window.clearTimeout(persistTimerRef.current)
      }
      persistConversation().catch((error) => {
        console.error('组件卸载时保存对话历史失败:', error)
      })
      disconnectSocket()
    }
  }, [patientId, patientName])

  const sendSocketPayload = (payload) => {
    if (!socketRef.current || socketRef.current.readyState !== WebSocket.OPEN) {
      setMessages((current) => [
        ...current,
        {
          id: createRequestId(),
          role: 'system-message',
          type: 'text',
          content: 'WebSocket 连接未建立，无法发送到 AI Agent。',
        },
      ])
      return false
    }

    setLoading(true)
    socketRef.current.send(JSON.stringify(payload))
    return true
  }

  const buildBasePayload = () => ({
    requestId: createRequestId(),
    patientId,
    patientName,
    hospitalId: getHospitalId(),
  })

  const hexToRgba = (hex, alpha = 0.18) => {
    if (typeof hex !== 'string' || !/^#[\dA-Fa-f]{6}$/.test(hex.trim())) {
      return `rgba(37, 99, 235, ${alpha})`
    }
    const normalized = hex.trim()
    const red = Number.parseInt(normalized.slice(1, 3), 16)
    const green = Number.parseInt(normalized.slice(3, 5), 16)
    const blue = Number.parseInt(normalized.slice(5, 7), 16)
    return `rgba(${red}, ${green}, ${blue}, ${alpha})`
  }

  const getHighlightLegendItems = (structuredPayload) => {
    if (Array.isArray(structuredPayload?.highlightLegend) && structuredPayload.highlightLegend.length) {
      return structuredPayload.highlightLegend
    }
    if (Array.isArray(structuredPayload?.highlightRegions)) {
      return structuredPayload.highlightRegions.map((region) => ({
        colorName: region.colorName,
        colorHex: region.colorHex,
        meaning: region.annotationMeaning || region.note || `第 ${region.priority || 1} 优先级复核区域`,
      }))
    }
    return []
  }

  const findReferenceImageForResult = (message) => {
    if (typeof message?.sourceImage === 'string' && message.sourceImage) {
      return message.sourceImage
    }
    if (!message?.requestId) {
      return null
    }
    const messageIndex = messages.findIndex((item) => item.id === message.id)
    const searchPool = messageIndex > 0 ? messages.slice(0, messageIndex).reverse() : [...messages].reverse()
    const matched = searchPool.find((item) => item.type === 'image' && item.requestId === message.requestId)
    return matched?.content || null
  }

  const handleImageUpload = (event) => {
    const files = event.target.files
    if (!files?.length) {
      return
    }

    Array.from(files).forEach((file) => {
      const requestId = createRequestId()
      const reader = new FileReader()
      reader.onload = async (loadEvent) => {
        const imageUrl = loadEvent.target?.result
        if (!imageUrl) {
          return
        }

        const persistedContent = await createPersistableImagePreview(imageUrl)

        setMessages((current) => [
          ...current,
          {
            id: buildImageMessageId(requestId),
            requestId,
            role: 'doctor-message',
            type: 'image',
            content: imageUrl,
            persistedContent,
            imageType,
            imageDate: new Date().toISOString().split('T')[0],
          },
        ])

        sendSocketPayload({
          ...buildBasePayload(),
          requestId,
          type: 'image',
          imageData: imageUrl,
          imageType,
        })
      }
      reader.readAsDataURL(file)
    })

    event.target.value = ''
  }

  const openUploadDialog = () => {
    uploadInputRef.current?.click()
  }

  const sendMessage = () => {
    const text = inputMessage.trim()
    if (!text) {
      return
    }

    setMessages((current) => [
      ...current,
      {
        id: createRequestId(),
        role: 'doctor-message',
        type: 'text',
        content: text,
      },
    ])
    setInputMessage('')
    sendSocketPayload({
      ...buildBasePayload(),
      type: 'chat',
      message: text,
    })
  }

  const getConfirmedResultMessage = () => {
    if (confirmedResultMessageId) {
      const matched = messages.find((message) => message.id === confirmedResultMessageId && message.confirmed)
      if (matched) {
        return matched
      }
    }
    return [...messages].reverse().find((message) => message.type === 'ai_result' && message.confirmed) || null
  }

  const buildPersistableAnalysis = (message) => {
    const analysis = message.structuredPayload?.analysis
    if (!analysis || typeof analysis !== 'object') {
      return null
    }

    const now = new Date().toISOString()
    return {
      ...analysis,
      hospitalId: analysis.hospitalId || getHospitalId(),
      patientId: analysis.patientId || patientId,
      patientName: analysis.patientName || patientName,
      imageId: analysis.imageId || `IMG-${patientId || Date.now()}`,
      status: analysis.status || 'completed',
      createdAt: analysis.createdAt || now,
      completedAt: analysis.completedAt || now,
      errorMessage: analysis.errorMessage || null,
    }
  }

  const buildPersistableReport = (message) => {
    const report = message.structuredPayload?.report
    if (!report || typeof report !== 'object') {
      return null
    }

    const analysisId = message.savedAnalysisId || buildPersistableAnalysis(message)?.id
    if (!analysisId) {
      return null
    }

    const now = new Date().toISOString()
    return {
      ...report,
      hospitalId: report.hospitalId || getHospitalId(),
      patientId: report.patientId || patientId,
      patientName: report.patientName || patientName,
      analysisIds: [analysisId],
      reportType: report.reportType || `${imageType}_AI_REPORT`,
      status: report.status || 'generated',
      createdAt: report.createdAt || now,
      generatedAt: report.generatedAt || now,
      errorMessage: report.errorMessage || null,
    }
  }

  const confirmResult = async (messageId) => {
    const targetMessage = messages.find((message) => message.id === messageId)
    if (!targetMessage || targetMessage.confirmed || targetMessage.saving) {
      return
    }

    const analysisPayload = buildPersistableAnalysis(targetMessage)
    if (!analysisPayload) {
      window.alert('当前结果不包含可保存的结构化分析数据。')
      return
    }

    setMessages((current) => current.map((message) => (
      message.id === messageId ? { ...message, saving: true } : message
    )))

    try {
      const response = await metricApi.createAnalysisResult(analysisPayload)
      const savedAnalysisId = response?.id || analysisPayload.id
      setMessages((current) => [
        ...current.map((message) => (
          message.id === messageId
            ? { ...message, confirmed: true, saving: false, savedAnalysisId }
            : message
        )),
        {
          id: createRequestId(),
          role: 'system-message',
          type: 'text',
          content: '分析结果已确认，并已同步到分析结果管理。',
        },
      ])
      setConfirmedResultMessageId(messageId)
      await persistConversation()
    } catch (error) {
      setMessages((current) => current.map((message) => (
        message.id === messageId ? { ...message, saving: false } : message
      )))
      window.alert(error.message || '保存分析结果失败。')
    }
  }

  const generateReport = async (messageId = '') => {
    const message = messageId
      ? messages.find((item) => item.id === messageId && item.type === 'ai_result')
      : getConfirmedResultMessage()
    if (!message) {
      return
    }

    if (!message.confirmed) {
      window.alert('请先确认当前分析结果，再生成报表。')
      return
    }

    if (message.reportSaved) {
      navigate(`/metric/reports/list?patientId=${encodeURIComponent(patientId)}&patientName=${encodeURIComponent(patientName)}&generate=true`)
      return
    }

    const reportPayload = buildPersistableReport(message)
    if (!reportPayload) {
      window.alert('当前结果不包含可保存的报表数据，请先完成一次结构化分析。')
      return
    }

    setGeneratingReportMessageId(message.id)
    try {
      await metricApi.createReport(reportPayload)
      setMessages((current) => [
        ...current.map((item) => (
          item.id === message.id ? { ...item, reportSaved: true } : item
        )),
        {
          id: createRequestId(),
          role: 'system-message',
          type: 'text',
          content: '报表已生成，并已同步到报表管理。',
        },
      ])
      await persistConversation()
      navigate(`/metric/reports/list?patientId=${encodeURIComponent(patientId)}&patientName=${encodeURIComponent(patientName)}&generate=true`)
    } catch (error) {
      window.alert(error.message || '生成报表失败。')
    } finally {
      setGeneratingReportMessageId('')
    }
  }

  const clearChat = async () => {
    if (!window.confirm('确定要清空聊天记录吗？')) {
      return
    }

    suppressPersistenceRef.current = true
    setMessages([])
    setLoading(false)
    setConfirmedResultMessageId('')
    setGeneratingReportMessageId('')

    try {
      await metricApi.clearConversationHistory(getConversationId(), getHospitalId())
    } catch (error) {
      console.error('清空对话历史失败:', error)
      window.alert(error.message || '已清空当前界面，但删除历史记录失败。')
    } finally {
      suppressPersistenceRef.current = false
    }
  }

  const hasConfirmedResult = Boolean(getConfirmedResultMessage())
  const isGeneratingAnyReport = Boolean(generatingReportMessageId)

  return (
    <section className="chat-page">
      <div className="chat-topbar">
        <button type="button" className="button button-secondary" onClick={goBack}>
          返回
        </button>
        <div>
          <h2>医学影像分析 - {patientName}</h2>
          <p>患者 ID: {patientId || '-'}</p>
        </div>
        <span className={`connection-pill ${socketStatus}`}>{socketStatus}</span>
      </div>

      <div className="chat-card">
        <div className="chat-scroll" ref={chatScrollRef}>
          <div className="message-row system">
            <div className="message-bubble system">
              欢迎使用医学影像 AI 分析系统。AI Agent 仅支持医疗影像分析、指标解读与报表生成，不提供闲聊或其他通用问答。
            </div>
          </div>

          {messages.map((message) => {
            const linkedImage = message.type === 'ai_result' ? findReferenceImageForResult(message) : null
            const highlightRegions = Array.isArray(message.structuredPayload?.highlightRegions)
              ? message.structuredPayload.highlightRegions
              : []
            const highlightLegend = getHighlightLegendItems(message.structuredPayload)
            const hasImagePreview = Boolean(linkedImage)
            const hasHighlightDetails = Boolean(highlightRegions.length || highlightLegend.length)
            const glowFilterId = `highlightGlow-${message.id}`

            return (
              <div key={message.id} className={`message-row ${message.role === 'doctor-message' ? 'doctor' : message.role === 'ai-message' ? 'ai' : 'system'}`}>
                <div className={`message-bubble ${message.type === 'ai_result' ? 'ai-result' : message.role === 'doctor-message' ? 'doctor' : message.role === 'ai-message' ? 'ai' : 'system'}`}>
                  {message.type === 'image' ? (
                    <div className="image-message">
                      <img src={message.content} alt="医学影像" />
                      <small>{message.imageType} · {message.imageDate}</small>
                    </div>
                  ) : message.type === 'ai_result' ? (
                    <div className="ai-result-card">
                      <div className="ai-result-header">
                        <div>
                          <h3>AI 分析结果</h3>
                          <p>{message.structuredPayload?.summary || message.content}</p>
                        </div>
                        <div className="ai-result-meta">
                          <span>{message.structuredPayload?.analysisModeLabel || '结构化分析'}</span>
                          <span>置信度 {message.confidence != null ? `${message.confidence}%` : '未提供'}</span>
                        </div>
                      </div>
                      {hasImagePreview || hasHighlightDetails ? (
                        <div className="highlight-visual-panel">
                          {linkedImage ? (
                            <div className="highlight-preview-shell">
                              <div className="highlight-preview-meta">
                                <strong>原图标注预览</strong>
                                <small>
                                  {highlightRegions.length
                                    ? '高亮颜色直接叠加在上传影像上，颜色越靠前表示复核优先级越高。'
                                    : '当前结果未生成可视高亮，但这里会保留原始上传影像用于对照。'}
                                </small>
                              </div>
                              <div className="highlight-preview">
                                <img src={linkedImage} alt="病灶高亮预览" />
                                {highlightRegions.length ? (
                                  <svg
                                    className="highlight-svg-layer"
                                    viewBox="0 0 100 100"
                                    preserveAspectRatio="none"
                                    aria-hidden="true"
                                  >
                                    <defs>
                                      <filter id={glowFilterId} x="-40%" y="-40%" width="180%" height="180%">
                                        <feGaussianBlur stdDeviation="1.35" result="softGlow" />
                                        <feMerge>
                                          <feMergeNode in="softGlow" />
                                          <feMergeNode in="SourceGraphic" />
                                        </feMerge>
                                      </filter>
                                    </defs>
                                    {highlightRegions.map((region, index) => {
                                      const visual = buildRegionVisual(region, index)
                                      return (
                                        <g key={`${message.id}-${region.id}`} className="highlight-svg-region">
                                          <g transform={`rotate(${visual.rotationDegrees} ${visual.centerX} ${visual.centerY})`} filter={`url(#${glowFilterId})`}>
                                            <path
                                              d={visual.outerPath}
                                              fill={hexToRgba(region.colorHex, 0.36)}
                                              stroke={hexToRgba(region.colorHex, 0.9)}
                                              strokeWidth="0.75"
                                              className="highlight-svg-shape"
                                            />
                                            <path
                                              d={visual.innerPath}
                                              fill={hexToRgba(region.colorHex, 0.22)}
                                              stroke={hexToRgba(region.colorHex, 0.48)}
                                              strokeWidth="0.42"
                                              className="highlight-svg-shape-inner"
                                            />
                                          </g>
                                          <polyline
                                            points={visual.label.linePoints}
                                            fill="none"
                                            stroke={hexToRgba(region.colorHex, 0.88)}
                                            strokeWidth="0.48"
                                            strokeLinecap="round"
                                            strokeLinejoin="round"
                                            className="highlight-svg-callout-line"
                                          />
                                          <rect
                                            x={visual.label.x}
                                            y={visual.label.y}
                                            width={visual.label.width}
                                            height={visual.label.height}
                                            rx="2.8"
                                            fill="rgba(2, 6, 23, 0.42)"
                                            stroke={hexToRgba(region.colorHex, 0.46)}
                                            strokeWidth="0.22"
                                            className="highlight-svg-label-box"
                                          />
                                          <text
                                            x={visual.label.x + 1.6}
                                            y={visual.label.y + 3.45}
                                            className="highlight-svg-label-text"
                                          >
                                            <tspan fill={region.colorHex}>{visual.label.title}</tspan>
                                            <tspan
                                              x={visual.label.x + 1.6}
                                              dy="3.05"
                                              fill="rgba(255,255,255,0.92)"
                                              className="highlight-svg-label-subtext"
                                            >
                                              {visual.label.subtitle}
                                            </tspan>
                                          </text>
                                        </g>
                                      )
                                    })}
                                  </svg>
                                ) : null}
                              </div>
                            </div>
                          ) : null}
                          <div className="highlight-detail-panel">
                            <div className="result-list-block compact">
                              <h4>颜色声明</h4>
                              {highlightLegend.length ? (
                                <div className="highlight-legend-list">
                                  {highlightLegend.map((item, index) => (
                                    <div key={`${message.id}-legend-${item.colorHex}-${index}`} className="highlight-legend-item">
                                      <span
                                        className="highlight-color-chip"
                                        style={{ backgroundColor: item.colorHex }}
                                        aria-hidden="true"
                                      />
                                      <div>
                                        <strong>{item.colorName}</strong>
                                        <p>{item.meaning}</p>
                                      </div>
                                    </div>
                                  ))}
                                </div>
                              ) : (
                                <p className="highlight-empty-note">当前结果未输出颜色图例。</p>
                              )}
                            </div>
                            <div className="result-list-block compact">
                              <h4>标注解释</h4>
                              {highlightRegions.length ? (
                                <div className="highlight-region-list">
                                  {highlightRegions.map((region) => (
                                    <article
                                      key={`${message.id}-${region.id}-detail`}
                                      className="highlight-region-card"
                                      style={{ borderColor: hexToRgba(region.colorHex, 0.3) }}
                                    >
                                      <div className="highlight-region-card-header">
                                        <span
                                          className="highlight-region-badge"
                                          style={{
                                            backgroundColor: hexToRgba(region.colorHex, 0.16),
                                            color: region.colorHex,
                                          }}
                                        >
                                          {region.colorName}{region.label}
                                        </span>
                                        <strong>{region.location}</strong>
                                      </div>
                                      <strong className="highlight-region-title">{region.annotationTitle}</strong>
                                      <p>{region.annotationMeaning || region.note || '已生成区域说明。'}</p>
                                      <small>
                                        {region.estimatedSizeMm != null ? `范围 ${region.estimatedSizeMm} mm` : '范围未提供'}
                                        {' · '}
                                        覆盖 {region.coveragePercent}%
                                        {region.severity ? ` · ${region.severity}` : ''}
                                        {region.confidence != null ? ` · 置信度 ${region.confidence}%` : ''}
                                      </small>
                                    </article>
                                  ))}
                                </div>
                              ) : (
                                <p className="highlight-empty-note">当前分析未识别出可直接高亮渲染的区域。</p>
                              )}
                            </div>
                          </div>
                        </div>
                      ) : null}
                      {message.structuredPayload?.keyIndicators?.length ? (
                        <div className="metric-grid">
                          {message.structuredPayload.keyIndicators.map((indicator, index) => (
                            <div key={`${message.id}-${indicator.name}-${index}`} className="metric-card">
                              <span>{indicator.name}</span>
                              <strong>{indicator.value}</strong>
                              {indicator.unit ? <small>{indicator.unit}</small> : null}
                              {indicator.interpretation ? <small>{indicator.interpretation}</small> : null}
                            </div>
                          ))}
                        </div>
                      ) : null}
                      {message.structuredPayload?.findings?.length ? (
                        <div className="result-list-block">
                          <h4>主要发现</h4>
                          <ul>
                            {message.structuredPayload.findings.map((item) => (
                              <li key={`${message.id}-${item}`}>{item}</li>
                            ))}
                          </ul>
                        </div>
                      ) : null}
                      {message.structuredPayload?.recommendations?.length ? (
                        <div className="result-list-block">
                          <h4>处理建议</h4>
                          <ul>
                            {message.structuredPayload.recommendations.map((item) => (
                              <li key={`${message.id}-${item}`}>{item}</li>
                            ))}
                          </ul>
                        </div>
                      ) : null}
                      {message.structuredPayload?.limitations?.length ? (
                        <div className="result-list-block">
                          <h4>结果声明</h4>
                          <ul>
                            {message.structuredPayload.limitations.map((item) => (
                              <li key={`${message.id}-${item}`}>{item}</li>
                            ))}
                          </ul>
                        </div>
                      ) : null}
                      {message.structuredPayload?.conclusion ? (
                        <div className="result-list-block">
                          <h4>综合结论</h4>
                          <p>{message.structuredPayload.conclusion}</p>
                        </div>
                      ) : null}
                      {message.structuredPayload?.reportContent ? (
                        <div className="result-list-block">
                          <div className="report-preview-header">
                            <h4>报表预览</h4>
                            <small>生成后的 PDF 会包含高亮附图、颜色说明和详细明细。</small>
                          </div>
                          <pre className="report-preview">{message.structuredPayload.reportContent}</pre>
                        </div>
                      ) : null}
                      <div className="result-card-actions">
                        <span>
                          {message.confirmed
                            ? '分析结果已保存，可继续上传复查影像，新的分析不会覆盖当前已确认结果。'
                            : message.persistableAnalysis
                              ? '可保存到分析结果管理'
                              : '当前结果不含可保存的结构化数据'}
                        </span>
                        <div className="result-card-action-buttons">
                          <button
                            type="button"
                            className="button button-primary small"
                            disabled={message.confirmed || message.saving || !message.persistableAnalysis}
                            onClick={() => confirmResult(message.id)}
                          >
                            {message.confirmed ? '已确认' : message.saving ? '保存中...' : '确认结果'}
                          </button>
                          <button
                            type="button"
                            className="button button-secondary small"
                            disabled={!message.confirmed || generatingReportMessageId === message.id || !message.structuredPayload?.report}
                            onClick={() => generateReport(message.id)}
                          >
                            {message.reportSaved
                              ? '查看报表'
                              : generatingReportMessageId === message.id
                                ? '报表生成中...'
                                : '生成报表'}
                          </button>
                          <button
                            type="button"
                            className="button button-secondary small"
                            onClick={openUploadDialog}
                          >
                            继续上传复查影像
                          </button>
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div className="text-message">{message.content}</div>
                  )}
                </div>
              </div>
            )
          })}

          {loading ? (
            <div className="message-row ai">
              <div className="message-bubble ai">AI 正在处理中...</div>
            </div>
          ) : null}
        </div>

        <div className="chat-toolbar">
          <label className="button button-secondary upload-button">
            上传影像
            <input ref={uploadInputRef} type="file" accept="image/*" hidden multiple onChange={handleImageUpload} />
          </label>
          <select value={imageType} onChange={(event) => setImageType(event.target.value)}>
            <option value="XRAY">X射线</option>
            <option value="CT">CT</option>
            <option value="MRI">MRI</option>
            <option value="ULTRASOUND">超声</option>
            <option value="OTHER">其他</option>
          </select>
        </div>
        {hasConfirmedResult ? (
          <div className="follow-up-hint">
            已确认结果仍会保留在当前患者会话中，你可以直接继续上传复查影像并生成新的分析轮次。
          </div>
        ) : null}

        <div className="chat-input-row">
          <input
            value={inputMessage}
            onChange={(event) => setInputMessage(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                sendMessage()
              }
            }}
            placeholder="输入影像说明、指标问题或上传医学影像..."
          />
          <button type="button" className="button button-primary" disabled={socketStatus !== 'connected'} onClick={sendMessage}>
            发送
          </button>
        </div>
      </div>

      <div className="bottom-action-bar">
        <button type="button" className="button button-secondary" onClick={openUploadDialog}>
          继续上传影像
        </button>
        <button type="button" className="button button-primary" disabled={!hasConfirmedResult || isGeneratingAnyReport} onClick={() => generateReport()}>
          {isGeneratingAnyReport ? '报表保存中...' : '生成当前已确认报表'}
        </button>
        <button type="button" className="button button-secondary" onClick={clearChat}>
          清空聊天
        </button>
      </div>
    </section>
  )
}

function NotFoundPage() {
  const { navigate } = useRouter()

  return (
    <section className="panel">
      <div className="section-header">
        <div>
          <h2>页面不存在</h2>
          <p>当前地址没有对应的 React 页面，请返回工作台继续操作。</p>
        </div>
      </div>
      <div className="field-actions">
        <button type="button" className="button button-primary" onClick={() => navigate('/dashboard')}>
          返回工作台
        </button>
      </div>
    </section>
  )
}

export default App
