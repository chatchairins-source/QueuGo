// Transport policy only. Caller checks the actual selected Rider, offer and app session.
export function isRiderOfferNotification(user, notification) {
  return user.role === 'rider' && notification.type === 'order' && notification.title === 'มีงานใหม่';
}

export function buildRiderOfferMessage(token, notification, expiresAt, sessionId, now = Date.now()) {
  const expiry = Date.parse(expiresAt);
  if (!Number.isFinite(expiry) || expiry <= now) return null;
  return {
    token,
    data: {
      notificationId: String(notification.id || ''),
      role: 'rider',
      type: 'rider_offer',
      referenceId: String(notification.reference_id || ''),
      userId: String(notification.user_id || ''),
      sessionId: String(sessionId || ''),
      title: String(notification.title || '').slice(0, 80),
      message: String(notification.message || '').slice(0, 220),
      offerExpiresAt: new Date(expiry).toISOString()
    },
    android: {
      priority: 'high',
      collapse_key: 'queuego_rider_offer',
      ttl: Math.min(30, Math.floor((expiry - now) / 1000)) + 's'
    }
  };
}

// Rider events must be checked by the app even after background logout/session changes.
export function buildRiderEventMessage(token, notification, sessionId) {
  return {
    token,
    data: {
      notificationId: String(notification.id || ''), role: 'rider',
      type: String(notification.type || 'notification'),
      referenceId: String(notification.reference_id || ''),
      userId: String(notification.user_id || ''), sessionId: String(sessionId || ''),
      title: String(notification.title || 'QueueGo Rider').slice(0, 80),
      message: String(notification.message || 'มีรายการอัปเดต').slice(0, 220)
    },
    android: { priority: 'high' }
  };
}
