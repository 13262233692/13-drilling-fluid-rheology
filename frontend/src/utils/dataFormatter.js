export function formatTimestamp(ts) {
  if (!ts) return '--'
  const d = new Date(ts)
  return d.toLocaleTimeString('zh-CN', { hour12: false }) + '.' + String(d.getMilliseconds()).padStart(3, '0')
}

export function formatValue(val, decimals = 2) {
  if (val === null || val === undefined || isNaN(val)) return '--'
  return Number(val).toFixed(decimals)
}

export function rheologyDataToRheogram(data) {
  if (!data) return []
  return [
    { rpm: 600, shearRate: 1022, shearStress: data.theta600 },
    { rpm: 300, shearRate: 511, shearStress: data.theta300 },
    { rpm: 200, shearRate: 340.7, shearStress: data.theta200 },
    { rpm: 100, shearRate: 170.3, shearStress: data.theta100 },
    { rpm: 6, shearRate: 10.22, shearStress: data.theta6 },
    { rpm: 3, shearRate: 5.11, shearStress: data.theta3 },
  ]
}

export function historyToTimeSeries(history, field, maxPoints = 100) {
  if (!history || history.length === 0) return []
  const step = Math.max(1, Math.floor(history.length / maxPoints))
  const result = []
  for (let i = 0; i < history.length; i += step) {
    const item = history[i]
    if (item && item[field] !== undefined) {
      result.push({
        time: formatTimestamp(item.timestamp),
        value: item[field],
      })
    }
  }
  return result
}

export const PARAMETER_UNITS = {
  theta600: '°',
  theta300: '°',
  theta200: '°',
  theta100: '°',
  theta6: '°',
  theta3: '°',
  gel10s: 'lb/100ft²',
  gel10min: 'lb/100ft²',
  plasticViscosity: 'cP',
  yieldPoint: 'lb/100ft²',
  apparentViscosity: 'cP',
  density: 'g/cm³',
  temperature: '°C',
  flowBehaviorIndex: '',
  consistencyIndex: 'Pa·sⁿ',
  ecd: 'g/cm³',
  equivalentCirculatingDensity: 'g/cm³',
  frictionDrag: 't',
  weightOnBit: 't',
  anomalyScore: '',
  fracturePressure: 'g/cm³',
}

export const PARAMETER_LABELS = {
  theta600: 'θ600',
  theta300: 'θ300',
  theta200: 'θ200',
  theta100: 'θ100',
  theta6: 'θ6',
  theta3: 'θ3',
  gel10s: '初切力(10s)',
  gel10min: '终切力(10min)',
  plasticViscosity: '塑性粘度(PV)',
  yieldPoint: '动切力(YP)',
  apparentViscosity: '表观粘度(AV)',
  density: '密度(ρ)',
  temperature: '温度(T)',
  flowBehaviorIndex: '流性指数(n)',
  consistencyIndex: '稠度系数(K)',
  ecd: '等效循环密度(ECD)',
  equivalentCirculatingDensity: '等效循环密度(ECD)',
  frictionDrag: '摩阻',
  weightOnBit: '钻压(WOB)',
  anomalyScore: '异常得分',
  fracturePressure: '破裂压力',
}

export const RISK_LEVELS = {
  NONE: { level: 0, label: '正常', color: '#4ade80' },
  WARNING: { level: 1, label: '警告', color: '#f59e0b' },
  CRITICAL: { level: 2, label: '危险', color: '#ef4444' },
}

export function getRiskLevelInfo(level) {
  return RISK_LEVELS[level] || RISK_LEVELS.NONE
}

export function formatRiskLevel(level) {
  const info = getRiskLevelInfo(level)
  return info.label
}

export function formatRiskColor(level) {
  const info = getRiskLevelInfo(level)
  return info.color
}
