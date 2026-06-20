<template>
  <div class="dashboard">
    <FullScreenAlert :alert="activeAlert" @acknowledge="handleAlertAcknowledge" />
    <AlertConfirmationDialog :alert="activeAlert" :show="showDialog" @confirm="handleAlertConfirm" @cancel="handleAlertCancel" />

    <header class="dashboard-header">
      <div class="header-left">
        <h1>🌊 钻井液流变性监控中心</h1>
        <span class="subtitle">深海钻井平台 · Deep-Sea Drilling Platform</span>
      </div>
      <div class="header-right">
        <div v-if="alertSystemConnected" class="alert-status" :class="activeAlert ? 'has-alert' : ''">
          <span class="alert-icon">⚠️</span>
          <span>预警系统: {{ activeAlert ? '最高级别警报' : '正常' }}</span>
        </div>
        <div class="status-indicator" :class="connected ? 'online' : 'offline'">
          <span class="status-dot"></span>
          {{ connected ? '在线' : '离线' }}
        </div>
        <div class="timestamp" v-if="latestData">
          {{ formatTimestamp(latestData.timestamp) }}
        </div>
      </div>
    </header>

    <main class="dashboard-body">
      <section class="section-top">
        <div class="chart-card rheology-curve">
          <RheologyCurve :data="latestData" />
        </div>
        <div class="gauge-column">
          <div class="chart-card">
            <ViscometerGauge :data="latestData" />
          </div>
          <div class="chart-card">
            <DensityGauge :data="latestData" />
          </div>
        </div>
      </section>

      <section class="section-bottom">
        <div class="chart-card full-width">
          <ParameterPanel :data="latestData" :history="historyData" />
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { useRheologyWebSocket } from '../composables/useWebSocket.js'
import { useAlertSystem } from '../composables/useAlertSystem.js'
import { formatTimestamp } from '../utils/dataFormatter.js'
import RheologyCurve from '../components/RheologyCurve.vue'
import ViscometerGauge from '../components/ViscometerGauge.vue'
import DensityGauge from '../components/DensityGauge.vue'
import ParameterPanel from '../components/ParameterPanel.vue'
import FullScreenAlert from '../components/FullScreenAlert.vue'
import AlertConfirmationDialog from '../components/AlertConfirmationDialog.vue'

const { connected, latestData, historyData } = useRheologyWebSocket()
const {
  activeAlert,
  showDialog,
  connected: alertSystemConnected,
  acknowledgeAlert,
  confirmAlert,
  cancelAlert
} = useAlertSystem()

function handleAlertAcknowledge(alertId) {
  acknowledgeAlert(alertId, '操作员')
}

function handleAlertConfirm() {
  confirmAlert()
}

function handleAlertCancel() {
  cancelAlert()
}
</script>

<style scoped>
.dashboard {
  min-height: 100vh;
  background: #060e1a;
  color: #e0e8f0;
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  display: flex;
  flex-direction: column;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: linear-gradient(135deg, #0a1628 0%, #0d2137 100%);
  border-bottom: 1px solid #1a3a5c;
}

.header-left h1 {
  margin: 0;
  font-size: 22px;
  color: #8ecae6;
  letter-spacing: 2px;
}

.subtitle {
  font-size: 12px;
  color: #4a7a9b;
  margin-left: 12px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.alert-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  padding: 4px 12px;
  border-radius: 12px;
  color: #4ade80;
  background: rgba(74, 222, 128, 0.1);
  transition: all 0.3s ease;
}

.alert-status.has-alert {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.2);
  animation: pulse-alert 500ms infinite;
}

@keyframes pulse-alert {
  0%, 100% { opacity: 0.85; }
  50% { opacity: 1; }
}

.alert-icon {
  font-size: 16px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  padding: 4px 12px;
  border-radius: 12px;
}

.status-indicator.online {
  color: #4ade80;
  background: rgba(74, 222, 128, 0.1);
}

.status-indicator.offline {
  color: #f87171;
  background: rgba(248, 113, 113, 0.1);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.online .status-dot {
  background: #4ade80;
  box-shadow: 0 0 8px #4ade80;
}

.offline .status-dot {
  background: #f87171;
  box-shadow: 0 0 8px #f87171;
}

.timestamp {
  font-size: 13px;
  color: #4a7a9b;
  font-family: 'Courier New', monospace;
}

.dashboard-body {
  flex: 1;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: auto;
}

.section-top {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.rheology-curve {
  flex: 2;
}

.gauge-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-bottom {
  flex: 0 0 auto;
}

.chart-card {
  background: #0a1628;
  border: 1px solid #1a3a5c;
  border-radius: 8px;
  overflow: hidden;
}

.full-width {
  width: 100%;
}
</style>
