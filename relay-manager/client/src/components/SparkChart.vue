<template>
  <svg class="spark-chart" viewBox="0 0 600 180" preserveAspectRatio="none" role="img" aria-label="监控趋势图">
    <path class="grid" d="M0 45H600M0 90H600M0 135H600" />
    <path class="area" :d="areaPath" />
    <path class="line" :d="linePath" />
  </svg>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  samples: { type: Array, default: () => [] },
  field: { type: String, default: "activeStreams" },
});

const points = computed(() => {
  const values = props.samples.map((item) => Number(item[props.field] || 0));
  const max = Math.max(1, ...values);
  return values.map((value, index) => {
    const x = values.length <= 1 ? 0 : (index / (values.length - 1)) * 600;
    const y = 160 - (value / max) * 140;
    return [x, y];
  });
});

const linePath = computed(() => points.value.map(([x, y], index) => `${index ? "L" : "M"}${x.toFixed(1)} ${y.toFixed(1)}`).join(" "));
const areaPath = computed(() => {
  if (!points.value.length) return "";
  return `${linePath.value} L600 180 L0 180 Z`;
});
</script>
