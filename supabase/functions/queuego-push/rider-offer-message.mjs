// Transport policy only. Caller checks the actual selected Rider, offer and app session.
export function isRiderOfferNotification(user, notification) {
  return user.role === 'rider' && notification.type === 'order' && notification.title === 'มีงานใหม่';
}

export function buildRiderOfferMessage(token, notification, expiresAt, sessionId, now = Date.now()) {
  const deviceToken = String(token || '').trim();
  const userId = String(notification.user_id || '').trim();
  const referenceId = String(notification.reference_id || '').trim();
  const lease = String(sessionId || '').trim();
  const expiry = Date.parse(expiresAt);
  if (!deviceToken || !userId || !referenceId || !lease || !Number.isFinite(expiry) || expiry <= now) return null;
  return {
    token: deviceToken,
    data: {
      notificationId: String(notification.id || ''),
      role: 'rider',
      type: 'rider_offer',
      referenceId,
      userId,
      sessionId: lease,
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
  const deviceToken = String(token || '').trim();
  const notificationId = String(notification.id || '').trim();
  const userId = String(notification.user_id || '').trim();
  const lease = String(sessionId || '').trim();
  if (!deviceToken || !notificationId || !userId || !lease) return null;
  return {
    token: deviceToken,
    data: {
      notificationId, role: 'rider',
      type: String(notification.type || 'notification'),
      referenceId: String(notification.reference_id || ''),
      userId, sessionId: lease,
      title: String(notification.title || 'QueueGo Rider').slice(0, 80),
      message: String(notification.message || 'มีรายการอัปเดต').slice(0, 220)
    },
    android: { priority: 'high' }
  };
}
