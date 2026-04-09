const STORAGE_KEY = 'system_notifications'
const READ_AT_KEY = 'system_notifications_read_at'
const UPDATE_EVENT = 'system-notifications-updated'

export const loadSystemNotifications = () => {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return []
  }

  try {
    const notifications = JSON.parse(raw)
    return Array.isArray(notifications) ? notifications : []
  } catch (_error) {
    return []
  }
}

export const saveSystemNotifications = (notifications) => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(notifications))
  window.dispatchEvent(new CustomEvent(UPDATE_EVENT, { detail: notifications }))
}

export const mergeSystemNotification = (notification) => {
  const notifications = loadSystemNotifications()
  const nextNotifications = [
    notification,
    ...notifications.filter(item => item.id !== notification.id),
  ]
  saveSystemNotifications(nextNotifications)
  return nextNotifications
}

export const replaceSystemNotifications = (notifications) => {
  const nextNotifications = [...notifications].sort((left, right) => {
    return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()
  })
  saveSystemNotifications(nextNotifications)
  return nextNotifications
}

export const markSystemNotificationsRead = () => {
  localStorage.setItem(READ_AT_KEY, new Date().toISOString())
  window.dispatchEvent(new CustomEvent(UPDATE_EVENT, { detail: loadSystemNotifications() }))
}

export const getUnreadNotificationCount = () => {
  const notifications = loadSystemNotifications()
  const lastReadAt = localStorage.getItem(READ_AT_KEY)

  if (!lastReadAt) {
    return notifications.length
  }

  const lastReadTime = new Date(lastReadAt).getTime()
  return notifications.filter(notification => {
    return new Date(notification.createdAt).getTime() > lastReadTime
  }).length
}

export const systemNotificationEvents = {
  update: UPDATE_EVENT,
}
