<script setup>
import { defineProps, defineEmits } from 'vue'
import { formatTimestamp } from '../utils/dataFormatter.js'

const props = defineProps({
  alert: { type: Object, default: null }
})

const emit = defineEmits(['acknowledge'])

function handleClick() {
  if (props.alert) {
    emit('acknowledge', props.alert.alertId)
  }
}
</script>

<template>
  <div v-if="alert" class="full-screen-alert" @click="handleClick">
    <div class="alert-container">
      <div class="alert-content">
        <div class="alert-header">
          <span class="alert-icon">🚨</span>
          <h1 class="alert-title">井下事故警报</h1>
        </div>
        <div class="well-info">
          <span>井号: {{ alert.wellId }}</span>
          <span>时间: {{ formatTimestamp(alert.timestamp) }}</span>
        </div>
        <h2 class="alert-subtitle">ECD 异常上扬 · 即将触破裂压力</h2>
        <div class="metrics-grid">
          <div class="metric-card">
            <div class="metric-label">预测 ECD</div>
            <div class="metric-value red">{{ alert.predictedECD }} g/cm³</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">破裂压力</div>
            <div class="metric-value orange">{{ alert.fracturePressureThreshold }} g/cm³</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">异常得分</div>
            <div class="metric-value yellow">{{ alert.anomalyScore?.toFixed(3) }}</div>
          </div>
        </div>
        <div class="alert-footer">
          <span class="flashing-text">请立即确认!</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.full-screen-alert {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 9999;
  background: radial-gradient(ellipse at center, rgba(239, 68, 68, 0.95) 0%, rgba(0, 0, 0, 0.98) 100%);
  animation: pulse-alert 500ms infinite;
  cursor: pointer;
}

@keyframes pulse-alert {
  0%, 100% { opacity: 0.85; }
  50% { opacity: 1; }
}

.alert-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 8px solid #ef4444;
  box-shadow: 0 0 60px rgba(239, 68, 68, 0.8), inset 0 0 100px rgba(239, 68, 68, 0.4);
  animation: pulse-alert 500ms infinite;
}

.alert-content {
  text-align: center;
  padding: 40px;
}

.alert-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  margin-bottom: 20px;
}

.alert-icon {
  font-size: 64px;
  animation: pulse-alert 500ms infinite;
}

.alert-title {
  font-size: 48px;
  font-weight: bold;
  color: #ffffff;
  margin: 0;
  text-shadow: 0 0 20px rgba(255, 255, 255, 0.5);
}

.well-info {
  font-size: 20px;
  color: #8ecae6;
  margin-bottom: 30px;
  display: flex;
  justify-content: center;
  gap: 40px;
}

.alert-subtitle {
  font-size: 32px;
  color: #ef4444;
  margin: 0 0 40px 0;
  text-shadow: 0 0 15px rgba(239, 68, 68, 0.6);
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 30px;
  margin-bottom: 40px;
  max-width: 900px;
}

.metric-card {
  background: rgba(10, 22, 40, 0.8);
  border: 2px solid rgba(239, 68, 68, 0.5);
  border-radius: 12px;
  padding: 30px;
  backdrop-filter: blur(10px);
}

.metric-label {
  font-size: 18px;
  color: #8ecae6;
  margin-bottom: 15px;
}

.metric-value {
  font-size: 36px;
  font-weight: bold;
}

.metric-value.red {
  color: #ef4444;
  text-shadow: 0 0 15px rgba(239, 68, 68, 0.7);
}

.metric-value.orange {
  color: #f59e0b;
  text-shadow: 0 0 15px rgba(245, 158, 11, 0.7);
}

.metric-value.yellow {
  color: #fde047;
  text-shadow: 0 0 15px rgba(253, 224, 71, 0.7);
}

.alert-footer {
  margin-top: 20px;
}

.flashing-text {
  font-size: 24px;
  color: #fde047;
  font-weight: bold;
  animation: pulse-alert 500ms infinite;
  text-shadow: 0 0 10px rgba(253, 224, 71, 0.8);
}
</style>
