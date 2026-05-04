<template>
  <span class="level-badge" :title="label">
    <div class="badge-icon-wrapper">
      <svg viewBox="0 0 64 64" aria-hidden="true">
        <defs>
          <linearGradient :id="gradientId" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" :stop-color="lightColor" />
            <stop offset="100%" :stop-color="color" />
          </linearGradient>
          <filter :id="`glow-${gradientId}`" x="-30%" y="-30%" width="160%" height="160%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
        </defs>

        <!-- Hexagon Border -->
        <path d="M32 4 L58 18 L58 46 L32 60 L6 46 L6 18 Z" fill="rgba(0,0,0,0.3)" :stroke="color" stroke-width="2" />

        <!-- Level Shape -->
        <g :filter="`url(#glow-${gradientId})`">
          <circle v-if="level === 1" cx="32" cy="32" r="18" :fill="`url(#${gradientId})`" />
          <path v-else-if="level === 2" d="M32 12 L48 20 L48 44 L32 52 L16 44 L16 20 Z" :fill="`url(#${gradientId})`" />
          <path v-else d="M32 8 L54 22 L54 42 L32 56 L10 42 L10 22 Z" :fill="`url(#${gradientId})`" />
        </g>

        <text x="32" y="40" text-anchor="middle" font-size="20" font-weight="900" fill="#03130d" style="font-family: 'Segoe UI', sans-serif;">{{ level }}</text>
      </svg>
    </div>
    <div class="badge-text">
      <span class="badge-label">{{ label }}</span>
      <span class="badge-sub">LEVEL {{ level }}</span>
    </div>
  </span>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  level: { type: Number, default: 1 },
  label: { type: String, default: "一级用户" },
  color: { type: String, default: "#00f2ff" },
});

const gradientId = computed(() => `badge-${props.level}-${props.color.replace(/[^a-z0-9]/gi, "")}`);
const lightColor = computed(() => {
  return props.color.startsWith('#') ? props.color + 'dd' : props.color;
});
</script>

<style scoped>
.level-badge {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 6px 16px 6px 6px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  backdrop-filter: blur(10px);
  transition: border-color 0.2s var(--ease), background 0.2s var(--ease), transform 0.2s var(--ease);
}

.level-badge:hover {
  background: rgba(255, 255, 255, 0.06);
  border-color: var(--border-strong);
  transform: translateX(4px);
}

.badge-icon-wrapper {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
}

.badge-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.badge-label {
  font-size: 14px;
  font-weight: 800;
  color: #fff;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.badge-sub {
  font-size: 9px;
  font-weight: 700;
  color: var(--muted);
  letter-spacing: 1.5px;
}
</style>
