<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick, defineProps, defineEmits } from 'vue'
import * as echarts from 'echarts'
import { formatTimestamp } from '../utils/dataFormatter.js'

const props = defineProps({
  alert: { type: Object, default: null },
  show: { type: Boolean, default: false }
})

const emit = defineEmits(['confirm', 'cancel'])

const chartRef = ref(null)
let chartInstance = null

function buildOption(alert) {
  const historyData = alert?.ecdHistory || []
  const forecastData = alert?.ecdForecast || []
  const threshold = alert?.fracturePressureThreshold || 0

  const historyTime = historyData.map(d => formatTimestamp(d.timestamp))
  const historyValue = historyData.map(d => d.value)
  const forecastTime = forecastData.map(d => formatTimestamp(d.timestamp))
  const forecastValue = forecastData.map(d => d.value)

  const allTimes = [...historyTime, ...forecastTime]
  const allValues = [...historyValue, ...forecastValue]
  const minY = Math.max(0, Math.min(...allValues) - 0.1)
  const maxY = Math.max(...allValues, threshold) + 0.1

  const markAreaData = []
  const aboveThreshold = forecastData.filter(d => d.value >= threshold)
  if (aboveThreshold.length > 0) {
    const firstIdx = forecastData.findIndex(d => d.value >= threshold)
    const startX = forecastTime[Math.max(0, firstIdx - 1)]
    const endX = forecastTime[forecastTime.length - 1]
    markAreaData.push([
      { name: '风险区域', xAxis: startX, itemStyle: { color: 'rgba(239, 68, 68, 0.3)' } },
      { xAxis: endX }
    ])
  }

  return {
    backgroundColor: '#0a1628',
    title: {
      text: 'ECD 时序差值对比图 (历史 vs ARIMA 预测)',
      left: 'center',
      top: 10,
      textStyle: { color: '#8ecae6', fontSize: 16 }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10, 22, 40, 0.95)',
      borderColor: '#1a3a5c',
      textStyle: { color: '#8ecae6' },
      axisPointer: {
        type: 'cross',
        lineStyle: { color: '#1a3a5c' }
      }
    },
    legend: {
      top: 40,
      textStyle: { color: '#8ecae6' },
      data: ['实际ECD历史', 'ARIMA预测ECD', '破裂压力临界线']
    },
    grid: {
      left: 70,
      right: 30,
      top: 80,
      bottom: 50
    },
    xAxis: {
      type: 'category',
      name: '时间',
      nameTextStyle: { color: '#8ecae6' },
      data: allTimes,
      axisLine: { lineStyle: { color: '#1a3a5c' } },
      axisLabel: { color: '#8ecae6', rotate: 30, fontSize: 10 },
      splitLine: { lineStyle: { color: '#1a3a5c' } }
    },
    yAxis: {
      type: 'value',
      name: 'ECD (g/cm³)',
      nameTextStyle: { color: '#8ecae6' },
      min: minY,
      max: maxY,
      axisLine: { lineStyle: { color: '#1a3a5c' } },
      axisLabel: { color: '#8ecae6' },
      splitLine: { lineStyle: { color: '#1a3a5c' } }
    },
    series: [
      {
        name: '实际ECD历史',
        type: 'line',
        data: historyValue.concat(new Array(forecastTime.length).fill(null)),
        smooth: true,
        lineStyle: { color: '#3b82f6', width: 3 },
        itemStyle: { color: '#3b82f6' },
        symbol: 'circle',
        symbolSize: 6
      },
      {
        name: 'ARIMA预测ECD',
        type: 'line',
        data: new Array(historyTime.length).fill(null).concat(forecastValue),
        smooth: true,
        lineStyle: { color: '#ef4444', width: 3, type: 'dashed' },
        itemStyle: { color: '#ef4444' },
        symbol: 'diamond',
        symbolSize: 6,
        markArea: {
          silent: true,
          data: markAreaData
        },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: {
            color: '#f59e0b',
            width: 2,
            type: 'dashed'
          },
          label: {
            color: '#f59e0b',
            formatter: '破裂压力临界线',
            position: 'insideEndTop'
          },
          data: [
            { yAxis: threshold }
          ]
        }
      }
    ]
  }
}

function initChart() {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value, null, { renderer: 'canvas' })
  if (props.alert) {
    chartInstance.setOption(buildOption(props.alert))
  }
}

function handleResize() {
  chartInstance?.resize()
}

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('cancel')
}

watch(() => props.show, async (val) => {
  if (val) {
    await nextTick()
    if (!chartInstance) {
      initChart()
    } else if (props.alert) {
      chartInstance.setOption(buildOption(props.alert), true)
    }
  }
})

watch(() => props.alert, (val) => {
  if (val && chartInstance && props.show) {
    chartInstance.setOption(buildOption(val), true)
  }
}, { deep: true })

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<template>
  <div v-if="show && alert" class="dialog-overlay">
    <div class="dialog-card">
      <div class="dialog-header">
        <h2 class="dialog-title">⚠️ 警报确认 · 阻断式操作</h2>
      </div>
      <div class="chart-area">
        <div ref="chartRef" class="ecd-chart"></div>
      </div>
      <div class="dialog-footer">
        <div class="info-text">
          检测到 ECD 非线性异常上扬，ARIMA 预测 {{ alert.ecdForecast?.length || 0 }} 步内将触及破裂压力阈值。请确认是否采取措施。
        </div>
        <div class="button-group">
          <button class="btn btn-primary" @click="handleConfirm">确认并采取措施</button>
          <button class="btn btn-secondary" @click="handleCancel">稍后处理</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.85);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(5px);
}

.dialog-card {
  background: #0a1628;
  border: 2px solid #ef4444;
  border-radius: 12px;
  width: 900px;
  max-width: 95vw;
  max-height: 95vh;
  overflow: hidden;
  box-shadow: 0 0 40px rgba(239, 68, 68, 0.5);
  display: flex;
  flex-direction: column;
}

.dialog-header {
  padding: 20px 30px;
  border-bottom: 1px solid #1a3a5c;
}

.dialog-title {
  font-size: 24px;
  color: #ef4444;
  margin: 0;
  text-shadow: 0 0 10px rgba(239, 68, 68, 0.5);
}

.chart-area {
  flex: 1;
  min-height: 400px;
  padding: 10px;
}

.ecd-chart {
  width: 100%;
  height: 400px;
  background: #0a1628;
  border-radius: 8px;
}

.dialog-footer {
  padding: 20px 30px;
  border-top: 1px solid #1a3a5c;
}

.info-text {
  font-size: 16px;
  color: #8ecae6;
  margin-bottom: 20px;
  line-height: 1.6;
}

.button-group {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
}

.btn {
  padding: 12px 30px;
  font-size: 16px;
  font-weight: bold;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-primary {
  background: #ef4444;
  color: #ffffff;
}

.btn-primary:hover {
  background: #dc2626;
  box-shadow: 0 0 20px rgba(239, 68, 68, 0.6);
}

.btn-secondary {
  background: #374151;
  color: #d1d5db;
}

.btn-secondary:hover {
  background: #4b5563;
}
</style>
