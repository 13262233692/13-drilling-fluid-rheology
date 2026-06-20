<script setup>
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import * as echarts from 'echarts'
import { historyToTimeSeries, PARAMETER_UNITS, PARAMETER_LABELS } from '../utils/dataFormatter.js'

const props = defineProps({
  data: { type: Object, default: () => ({}) },
  history: { type: Array, default: () => [] }
})

const pvSparkRef = ref(null)
const ypSparkRef = ref(null)
const densitySparkRef = ref(null)
let pvSparkChart = null
let ypSparkChart = null
let densitySparkChart = null

const dialKeys = ['theta600', 'theta300', 'theta200', 'theta100', 'theta6', 'theta3']
const gelKeys = ['gel10s', 'gel10min']
const modelKeys = ['flowBehaviorIndex', 'consistencyIndex']

function fmtVal(val, decimals) {
  if (val === null || val === undefined || isNaN(val)) return '--'
  return Number(val).toFixed(decimals ?? 2)
}

const dialCards = computed(() =>
  dialKeys.map(key => ({
    key,
    label: PARAMETER_LABELS[key],
    value: fmtVal(props.data?.[key], 1),
    unit: PARAMETER_UNITS[key]
  }))
)

const gelCards = computed(() =>
  gelKeys.map(key => ({
    key,
    label: PARAMETER_LABELS[key],
    value: fmtVal(props.data?.[key], 1),
    unit: PARAMETER_UNITS[key]
  }))
)

const modelCards = computed(() =>
  modelKeys.map(key => ({
    key,
    label: PARAMETER_LABELS[key],
    value: fmtVal(props.data?.[key], key === 'flowBehaviorIndex' ? 3 : 4),
    unit: PARAMETER_UNITS[key]
  }))
)

function buildSparklineOption(seriesData, color, unit) {
  const xData = seriesData.map(d => d.time)
  const yData = seriesData.map(d => d.value)
  return {
    backgroundColor: 'transparent',
    grid: { left: 5, right: 5, top: 18, bottom: 20 },
    title: {
      text: unit,
      left: 'center',
      top: 0,
      textStyle: { color: '#8ecae6', fontSize: 11 }
    },
    xAxis: {
      type: 'category',
      data: xData,
      show: false
    },
    yAxis: {
      type: 'value',
      show: false
    },
    series: [{
      type: 'line',
      data: yData,
      smooth: true,
      symbol: 'none',
      lineStyle: { color, width: 2 },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: color.replace(')', ',0.3)').replace('rgb', 'rgba') },
            { offset: 1, color: 'transparent' }
          ]
        }
      }
    }],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10,22,40,0.9)',
      borderColor: '#1a3a5c',
      textStyle: { color: '#8ecae6', fontSize: 11 }
    }
  }
}

function initSparklines() {
  if (pvSparkRef.value) {
    pvSparkChart = echarts.init(pvSparkRef.value, null, { renderer: 'canvas' })
  }
  if (ypSparkRef.value) {
    ypSparkChart = echarts.init(ypSparkRef.value, null, { renderer: 'canvas' })
  }
  if (densitySparkRef.value) {
    densitySparkChart = echarts.init(densitySparkRef.value, null, { renderer: 'canvas' })
  }
  updateSparklines()
}

function updateSparklines() {
  const h = props.history
  const pvSeries = historyToTimeSeries(h, 'plasticViscosity')
  const ypSeries = historyToTimeSeries(h, 'yieldPoint')
  const densitySeries = historyToTimeSeries(h, 'density')
  pvSparkChart?.setOption(buildSparklineOption(pvSeries, 'rgb(6,214,160)', 'PV (cP)'))
  ypSparkChart?.setOption(buildSparklineOption(ypSeries, 'rgb(255,209,102)', 'YP (lb/100ft²)'))
  densitySparkChart?.setOption(buildSparklineOption(densitySeries, 'rgb(17,138,178)', '密度 (g/cm³)'))
}

function handleResize() {
  pvSparkChart?.resize()
  ypSparkChart?.resize()
  densitySparkChart?.resize()
}

watch(() => [props.data, props.history], () => {
  updateSparklines()
}, { deep: true })

onMounted(() => {
  initSparklines()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  pvSparkChart?.dispose()
  ypSparkChart?.dispose()
  densitySparkChart?.dispose()
  pvSparkChart = null
  ypSparkChart = null
  densitySparkChart = null
})
</script>

<template>
  <div class="param-panel">
    <div class="cards-section">
      <div class="card-group">
        <div class="group-title">粘度计读数</div>
        <div class="card-grid">
          <div v-for="c in dialCards" :key="c.key" class="param-card">
            <div class="card-label">{{ c.label }}</div>
            <div class="card-value">{{ c.value }}</div>
            <div class="card-unit">{{ c.unit }}</div>
          </div>
        </div>
      </div>
      <div class="card-group">
        <div class="group-title">切力</div>
        <div class="card-grid">
          <div v-for="c in gelCards" :key="c.key" class="param-card">
            <div class="card-label">{{ c.label }}</div>
            <div class="card-value">{{ c.value }}</div>
            <div class="card-unit">{{ c.unit }}</div>
          </div>
        </div>
      </div>
      <div class="card-group">
        <div class="group-title">流变模型参数</div>
        <div class="card-grid">
          <div v-for="c in modelCards" :key="c.key" class="param-card">
            <div class="card-label">{{ c.label }}</div>
            <div class="card-value">{{ c.value }}</div>
            <div class="card-unit">{{ c.unit }}</div>
          </div>
        </div>
      </div>
    </div>
    <div class="sparkline-section">
      <div ref="pvSparkRef" class="sparkline-item"></div>
      <div ref="ypSparkRef" class="sparkline-item"></div>
      <div ref="densitySparkRef" class="sparkline-item"></div>
    </div>
  </div>
</template>

<style scoped>
.param-panel {
  width: 100%;
  background: #0a1628;
  border-radius: 8px;
  padding: 12px;
}
.cards-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 12px;
}
.card-group {
  width: 100%;
}
.group-title {
  color: #8ecae6;
  font-size: 13px;
  margin-bottom: 8px;
  font-weight: 600;
  border-bottom: 1px solid #1a3a5c;
  padding-bottom: 4px;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 8px;
}
.param-card {
  background: #0d1f3c;
  border: 1px solid #1a3a5c;
  border-radius: 6px;
  padding: 8px 10px;
  text-align: center;
}
.card-label {
  color: #8ecae6;
  font-size: 11px;
  margin-bottom: 4px;
}
.card-value {
  color: #ffffff;
  font-size: 18px;
  font-weight: bold;
  line-height: 1.2;
}
.card-unit {
  color: #8ecae6;
  font-size: 10px;
  margin-top: 2px;
}
.sparkline-section {
  display: flex;
  gap: 8px;
}
.sparkline-item {
  flex: 1;
  height: 100px;
  background: #0d1f3c;
  border: 1px solid #1a3a5c;
  border-radius: 6px;
}
</style>
