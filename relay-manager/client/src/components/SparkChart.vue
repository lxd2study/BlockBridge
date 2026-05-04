<template>
  <div class="spark-chart-container">
    <div class="chart-header" v-if="title">
      <h3>{{ title }}</h3>
    </div>
    <div class="chart-body">
      <svg class="spark-chart" viewBox="0 0 600 180" preserveAspectRatio="none" role="img" aria-label="监控趋势图">
        <defs>
          <linearGradient :id="`chart-gradient-${uid}`" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="var(--primary)" stop-opacity="0.13" />
            <stop offset="100%" stop-color="var(--primary)" stop-opacity="0" />
          </linearGradient>
        </defs>

        <path class="grid-lines" d="M0 45H600M0 90H600M0 135H600" />
        <path class="area" :d="areaPath" :fill="`url(#chart-gradient-${uid})`" />
        <path class="line" :d="linePath" />
      </svg>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  samples: { type: Array, default: () => [] },
  field: { type: String, default: "activeStreams" },
  title: { type: String, default: "" },
});

const uid = Math.random().toString(36).substring(2, 9);

const points = computed(() => {
  const values = props.samples.map((item) => Number(item[props.field] || 0));
  const max = Math.max(1, ...values) * 1.1; // Add some headroom
  return values.map((value, index) => {
    const x = values.length <= 1 ? 0 : (index / (values.length - 1)) * 600;
    const y = 170 - (value / max) * 150;
    return [x, y];
  });
});

const linePath = computed(() => {
  if (!points.value.length) return "";
  return points.value.map(([x, y], index) => `${index ? "L" : "M"}${x.toFixed(1)} ${y.toFixed(1)}`).join(" ");
});

const areaPath = computed(() => {
  if (!points.value.length) return "";
  return `${linePath.value} L600 180 L0 180 Z`;
});
</script>

<style scoped>
.spark-chart-container {
  width: 100%;
  background: rgba(0, 0, 0, 0.1);
  border: 1px solid var(--border);
  border-radius: 7px;
  overflow: hidden;
  padding: 14px;
}

.spark-chart-container:hover {
  border-color: var(--border-strong);
}

.chart-header h3 {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 1.5px;
}

.chart-body {
  width: 100%;
  height: 150px;
}

.spark-chart {
  width: 100%;
  height: 100%;
  overflow: visible;
}

.grid-lines {
  stroke: rgba(255, 255, 255, 0.045);
  stroke-width: 1;
}

.line {
  fill: none;
  stroke: var(--primary);
  stroke-width: 2.2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.area {
  pointer-events: none;
}

</style>
