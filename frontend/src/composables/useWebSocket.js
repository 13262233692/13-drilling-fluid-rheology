import { ref, onUnmounted } from 'vue'
import SockJS from 'sockjs-client'
import Stomp from 'stompjs'

export function useRheologyWebSocket(url = '/ws/rheology') {
  const connected = ref(false)
  const latestData = ref(null)
  const historyData = ref([])
  const error = ref(null)

  let stompClient = null
  let reconnectTimer = null
  const MAX_HISTORY = 300

  function connect() {
    try {
      const socket = new SockJS(url)
      stompClient = Stomp.over(socket)
      stompClient.debug = () => {}

      stompClient.connect(
        {},
        (frame) => {
          connected.value = true
          error.value = null
          stompClient.subscribe('/topic/rheology', (message) => {
            try {
              const data = JSON.parse(message.body)
              latestData.value = data
              historyData.value.push(data)
              if (historyData.value.length > MAX_HISTORY) {
                historyData.value.shift()
              }
            } catch (e) {
              console.error('Failed to parse rheology data', e)
            }
          })
        },
        (err) => {
          connected.value = false
          error.value = err
          scheduleReconnect()
        }
      )
    } catch (e) {
      error.value = e
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
    if (reconnectTimer) clearTimeout(reconnectTimer)
    if (stompClient && connected.value) {
      stompClient.disconnect()
    }
    connected.value = false
  }

  connect()

  onUnmounted(() => {
    disconnect()
  })

  return {
    connected,
    latestData,
    historyData,
    error,
    disconnect,
    reconnect: connect,
  }
}
