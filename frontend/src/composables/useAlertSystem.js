import { ref, onMounted, onUnmounted } from 'vue'
import SockJS from 'sockjs-client'
import Stomp from 'stompjs'

export function useAlertSystem(wsUrl = '/ws/rheology') {
  const activeAlert = ref(null)
  const alertHistory = ref([])
  const showDialog = ref(false)
  const connected = ref(false)

  let stompClient = null
  let reconnectTimer = null
  let redisplayTimer = null
  let audioContext = null

  function initAudio() {
    if (!audioContext) {
      audioContext = new (window.AudioContext || window.webkitAudioContext)()
    }
  }

  function playAlertSound() {
    initAudio()
    if (!audioContext) return

    const beepCount = 3
    const beepDuration = 0.2
    const beepGap = 0.1

    for (let i = 0; i < beepCount; i++) {
      const oscillator = audioContext.createOscillator()
      const gainNode = audioContext.createGain()

      oscillator.connect(gainNode)
      gainNode.connect(audioContext.destination)

      oscillator.frequency.value = 800
      oscillator.type = 'sine'

      const startTime = audioContext.currentTime + i * (beepDuration + beepGap)
      gainNode.gain.setValueAtTime(0.3, startTime)
      gainNode.gain.exponentialRampToValueAtTime(0.01, startTime + beepDuration)

      oscillator.start(startTime)
      oscillator.stop(startTime + beepDuration)
    }
  }

  function showBrowserNotification() {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification('井下事故警报', {
        body: 'ECD 即将触破裂压力',
        requireInteraction: true,
        icon: '/favicon.svg'
      })
    }
  }

  function handleAlertMessage(message) {
    try {
      const alert = JSON.parse(message.body)
      alertHistory.value.unshift(alert)
      if (alertHistory.value.length > 100) {
        alertHistory.value.pop()
      }

      if (alert.riskLevel === 'CRITICAL' && !alert.acknowledged) {
        if (!activeAlert.value || activeAlert.value.alertId !== alert.alertId) {
          activeAlert.value = alert
          showDialog.value = true
          playAlertSound()
          showBrowserNotification()
          startRedisplayTimer()
        }
      } else if (alert.acknowledged && activeAlert.value && activeAlert.value.alertId === alert.alertId) {
          activeAlert.value = null
          showDialog.value = false
          stopRedisplayTimer()
        }
    } catch (e) {
      console.error('Failed to parse alert message', e)
    }
  }

  function startRedisplayTimer() {
    stopRedisplayTimer()
    redisplayTimer = setInterval(() => {
      if (activeAlert.value && !activeAlert.value.acknowledged) {
        showDialog.value = true
        playAlertSound()
      }
    }, 10000)
  }

  function stopRedisplayTimer() {
    if (redisplayTimer) {
      clearInterval(redisplayTimer)
      redisplayTimer = null
    }
  }

  async function acknowledgeAlert(alertId, acknowledgedBy) {
    try {
      const response = await fetch(`/api/alerts/${alertId}/acknowledge`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ acknowledgedBy })
      })
      if (response.ok) {
        if (activeAlert.value && activeAlert.value.alertId === alertId) {
          activeAlert.value.acknowledged = true
          activeAlert.value.acknowledgedBy = acknowledgedBy
          activeAlert.value.acknowledgedAt = new Date().toISOString()
        }
        const idx = alertHistory.value.findIndex(a => a.alertId === alertId)
        if (idx !== -1) {
          alertHistory.value[idx].acknowledged = true
          alertHistory.value[idx].acknowledgedBy = acknowledgedBy
          alertHistory.value[idx].acknowledgedAt = new Date().toISOString()
        }
      }
      return response.ok
    } catch (e) {
      console.error('Failed to acknowledge alert', e)
      return false
    }
  }

  function confirmAlert() {
    if (activeAlert.value) {
      acknowledgeAlert(activeAlert.value.alertId, 'System')
      showDialog.value = false
    }
  }

  function cancelAlert() {
    showDialog.value = false
  }

  function connect() {
    try {
      const socket = new SockJS(wsUrl)
      stompClient = Stomp.over(socket)
      stompClient.debug = () => {}

      stompClient.connect(
        {},
        (frame) => {
          connected.value = true
          stompClient.subscribe('/topic/alerts', handleAlertMessage)
        },
        (err) => {
          connected.value = false
          scheduleReconnect()
        }
      )
    } catch (e) {
      connected.value = false
      scheduleReconnect()
    }
  }

  function scheduleReconnect() {
    if (reconnectTimer) clearTimeout(reconnectTimer)
    reconnectTimer = setTimeout(() => {
      connect()
    }, 5000)
  }

  function disconnect() {
    stopRedisplayTimer()
    if (reconnectTimer) clearTimeout(reconnectTimer)
    if (stompClient && connected.value) {
      stompClient.disconnect()
    }
    connected.value = false
  }

  async function requestNotificationPermission() {
    if ('Notification' in window && Notification.permission === 'default') {
      try {
        await Notification.requestPermission()
      } catch (e) {
        console.warn('Notification permission denied', e)
      }
    }
  }

  onMounted(() => {
    requestNotificationPermission()
    initAudio()
  })

  onUnmounted(() => {
    disconnect()
  })

  connect()

  return {
    activeAlert,
    alertHistory,
    showDialog,
    connected,
    acknowledgeAlert,
    confirmAlert,
    cancelAlert,
    disconnect,
    reconnect: connect
  }
}
