<template>
  <span class="level-badge" :title="label">
    <svg viewBox="0 0 64 64" aria-hidden="true">
      <defs>
        <linearGradient :id="gradientId" x1="8" x2="56" y1="8" y2="56">
          <stop offset="0" :stop-color="lightColor" />
          <stop offset="1" :stop-color="color" />
        </linearGradient>
      </defs>
      <circle v-if="level === 1" cx="32" cy="32" r="25" :fill="`url(#${gradientId})`" />
      <path v-else-if="level === 2" d="M32 6 54 15v17c0 13-8 22-22 27C18 54 10 45 10 32V15Z" :fill="`url(#${gradientId})`" />
      <path v-else d="M12 52h40l-4-31-10 11-6-18-6 18-10-11Z" :fill="`url(#${gradientId})`" />
      <path d="M20 47h24" fill="none" stroke="rgba(255,255,255,.65)" stroke-width="4" stroke-linecap="round" />
      <text x="32" y="38" text-anchor="middle" font-size="20" font-weight="900" fill="#fff">{{ level }}</text>
    </svg>
    <span>{{ label }}</span>
  </span>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  level: { type: Number, default: 1 },
  label: { type: String, default: "一级用户" },
  color: { type: String, default: "#ad6f3b" },
});

const gradientId = computed(() => `badge-${props.level}-${props.color.replace(/[^a-z0-9]/gi, "")}`);
const lightColor = computed(() => `${props.color}cc`);
</script>
