<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import { rheologyDataToRheogram } from '../utils/dataFormatter.js'

const props = defineProps({
  data: { type: Object, default: () => ({}) }
})

const chartRef = ref(null)
let chartInstance = null

function buildOption(data) {
  const points = rheologyDataToRheogram(data)
  const scatterData = points.map(p => [p.shearRate, p.shearStress])

  const maxRate = 1100
  const curvePoints = []
  for (let g = 1; g <= maxRate; g += 5) {
    curvePoints.push([g, g])
  }

  let powerLawData = []
  const n = data?.flowBehaviorIndex
  const K = data?.consistencyIndex
  if (n != null && K != null && !isNaN(n) && !isNaN(K)) {
    powerLawData = curvePoints.map(([g]) => [g, K * Math.pow(g, n)])
  }

  let binghamData = []
  const YP = data?.yieldPoint
  const PV = data?.plasticViscosity
  if (YP != null && PV != null && !isNaN(YP) && !isNaN(PV)) {
    binghamData = curvePoints.map(([g]) => [g, YP + PV * g / 1000])
  }

  return {
    backgroundColor: '#0a1628',
    title: {
      text: '流变曲线 (Rheology Curve)',
      left: 'center',
      top: 10,
      textStyle: { color: '#8ecae6', fontSize: 16 }
    },
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(10,22,40,0.9)',
      borderColor: '#1a3a5c',
      textStyle: { color: '#8ecae6' },
      formatter(params) {
        const x = params.value[0].toFixed(1)
        const y = params.value[1].toFixed(2)
        return `${params.seriesName}<br/>剪切速率: ${x} 1/s<br/>剪切应力: ${y} lb/100ft²`
      }
    },
    legend: {
      top: 40,
      textStyle: { color: '#8ecae6' },
      data: ['实测数据', 'Power Law 模型', 'Bingham 塑性模型']
    },
    grid: {
      left: 60,
      right: 30,
      top: 80,
      bottom: 50
    },
    xAxis: {
      type: 'log',
      name: '剪切速率 γ (1/s)',
      nameTextStyle: { color: '#8ecae6' },
      min: 1,
      max: 1200,
      axisLine: { lineStyle: { color: '#1a3a5c' } },
      axisLabel: { color: '#8ecae6' },
      splitLine: { lineStyle: { color: '#1a3a5c' } }
    },
    yAxis: {
      type: 'value',
      name: '剪切应力 τ (lb/100ft²)',
      nameTextStyle: { color: '#8ecae6' },
      axisLine: { lineStyle: { color: '#1a3a5c' } },
      axisLabel: { color: '#8ecae6' },
      splitLine: { lineStyle: { color: '#1a3a5c' } }
    },
    series: [
      {
        name: '实测数据',
        type: 'scatter',
        data: scatterData,
        symbolSize: 10,
        itemStyle: { color: '#00d4ff', borderColor: '#8ecae6', borderWidth: 1 }
      },
      {
        name: 'Power Law 模型',
        type: 'line',
        data: powerLawData,
        smooth: true,
        lineStyle: { color: '#ffd166', type: 'dashed', width: 2 },
        symbol: 'none'
      },
      {
        name: 'Bingham 塑性模型',
        type: 'line',
        data: binghamData,
        lineStyle: { color: '#ef476f', type: 'dotted', width: 2 },
        symbol: 'none'
      }
    ]
  }
}

function initChart() {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value, null, { renderer: 'canvas' })
  chartInstance.setOption(buildOption(props.data))
}

function handleResize() {
  chartInstance?.resize()
}

watch(() => props.data, (val) => {
  if (chartInstance) {
    chartInstance.setOption(buildOption(val))
  }
}, { deep: true })

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<template>
  <div ref="chartRef" class="rheology-curve"></div>
</template>

<style scoped>
.rheology-curve {
  width: 100%;
  height: 100%;
  min-height: 400px;
  background: #0a1628;
  border-radius: 8px;
}
</style>
