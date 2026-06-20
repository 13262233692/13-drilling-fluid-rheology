<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Object, default: () => ({}) }
})

const densityRef = ref(null)
const tempRef = ref(null)
let densityChart = null
let tempChart = null

function buildDensityOption(value) {
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
        min: 1.0,
        max: 2.5,
        splitNumber: 6,
        progress: {
          show: true,
          width: 14,
          itemStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 1, y2: 0,
              colorStops: [
                { offset: 0, color: '#118ab2' },
                { offset: 0.5, color: '#f4a261' },
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
          fontSize: 10,
          formatter(v) { return v.toFixed(1) }
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
          formatter: '{value} g/cm³'
        },
        data: [{ value: val, name: '密度 (g/cm³)' }]
      }
    ]
  }
}

function buildTempOption(value) {
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
        max: 100,
        splitNumber: 5,
        progress: {
          show: true,
          width: 14,
          itemStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 1, y2: 0,
              colorStops: [
                { offset: 0, color: '#118ab2' },
                { offset: 0.5, color: '#06d6a0' },
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
          formatter: '{value} °C'
        },
        data: [{ value: val, name: '温度 (°C)' }]
      }
    ]
  }
}

function initCharts() {
  if (densityRef.value) {
    densityChart = echarts.init(densityRef.value, null, { renderer: 'canvas' })
  }
  if (tempRef.value) {
    tempChart = echarts.init(tempRef.value, null, { renderer: 'canvas' })
  }
  updateCharts()
}

function updateCharts() {
  const d = props.data
  densityChart?.setOption(buildDensityOption(d?.density))
  tempChart?.setOption(buildTempOption(d?.temperature))
}

function handleResize() {
  densityChart?.resize()
  tempChart?.resize()
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
  densityChart?.dispose()
  tempChart?.dispose()
  densityChart = null
  tempChart = null
})
</script>

<template>
  <div class="gauge-row">
    <div ref="densityRef" class="gauge-item"></div>
    <div ref="tempRef" class="gauge-item"></div>
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
