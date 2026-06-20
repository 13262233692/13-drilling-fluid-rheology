<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Object, default: () => ({}) }
})

const pvRef = ref(null)
const ypRef = ref(null)
const avRef = ref(null)
let pvChart = null
let ypChart = null
let avChart = null

function buildGaugeOption(title, unit, value, max) {
  const val = value ?? 0
  return {
    backgroundColor: '#0a1628',
    series: [
      {
        type: 'gauge',
        center: ['50%', '60%'],
        radius: '85%',
        startAngle: 200,
        endAngle: -20,
        min: 0,
        max,
        splitNumber: 6,
        progress: {
          show: true,
          width: 14,
          itemStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 1, y2: 0,
              colorStops: [
                { offset: 0, color: '#06d6a0' },
                { offset: 0.5, color: '#ffd166' },
                { offset: 1, color: '#ef476f' }
              ]
            }
          }
        },
        pointer: {
          length: '60%',
          width: 4,
          itemStyle: { color: '#8ecae6' }
        },
        axisLine: {
          lineStyle: {
            width: 14,
            color: [[1, '#1a3a5c']]
          }
        },
        axisTick: {
          distance: -18,
          length: 6,
          lineStyle: { color: '#1a3a5c', width: 1 }
        },
        splitLine: {
          distance: -22,
          length: 12,
          lineStyle: { color: '#1a3a5c', width: 2 }
        },
        axisLabel: {
          distance: -30,
          color: '#8ecae6',
          fontSize: 10
        },
        anchor: {
          show: true,
          size: 10,
          itemStyle: { borderWidth: 2, borderColor: '#1a3a5c', color: '#0a1628' }
        },
        title: {
          offsetCenter: [0, '80%'],
          color: '#8ecae6',
          fontSize: 13
        },
        detail: {
          valueAnimation: true,
          offsetCenter: [0, '45%'],
          color: '#ffffff',
          fontSize: 22,
          fontWeight: 'bold',
          formatter: `{value} ${unit}`
        },
        data: [{ value: val, name: `${title} (${unit})` }]
      }
    ]
  }
}

function initCharts() {
  if (pvRef.value) {
    pvChart = echarts.init(pvRef.value, null, { renderer: 'canvas' })
  }
  if (ypRef.value) {
    ypChart = echarts.init(ypRef.value, null, { renderer: 'canvas' })
  }
  if (avRef.value) {
    avChart = echarts.init(avRef.value, null, { renderer: 'canvas' })
  }
  updateCharts()
}

function updateCharts() {
  const d = props.data
  pvChart?.setOption(buildGaugeOption('PV', 'cP', d?.plasticViscosity, 60))
  ypChart?.setOption(buildGaugeOption('YP', 'lb/100ft²', d?.yieldPoint, 60))
  avChart?.setOption(buildGaugeOption('AV', 'cP', d?.apparentViscosity, 60))
}

function handleResize() {
  pvChart?.resize()
  ypChart?.resize()
  avChart?.resize()
}

watch(() => props.data, () => {
  updateCharts()
}, { deep: true })

onMounted(() => {
  initCharts()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  pvChart?.dispose()
  ypChart?.dispose()
  avChart?.dispose()
  pvChart = null
  ypChart = null
  avChart = null
})
</script>

<template>
  <div class="gauge-row">
    <div ref="pvRef" class="gauge-item"></div>
    <div ref="ypRef" class="gauge-item"></div>
    <div ref="avRef" class="gauge-item"></div>
  </div>
</template>

<style scoped>
.gauge-row {
  display: flex;
  width: 100%;
  min-height: 220px;
  background: #0a1628;
  border-radius: 8px;
}
.gauge-item {
  flex: 1;
  min-height: 220px;
}
</style>
