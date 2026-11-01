<template>
  <span
    class="brand-mark"
    :class="[`brand-mark--${tone}`, `brand-mark--${size}`]"
    aria-hidden="true"
  >
    <i></i>
    <i></i>
    <i></i>
  </span>
</template>

<script setup>
defineProps({
  tone: { type: String, default: 'ink' },
  size: { type: String, default: 'md' }
})
</script>

<style scoped>
.brand-mark {
  --mark-size: 36px;
  --mark-pad: 9px;
  --mark-gap: 3px;
  --mark-bar: 2px;

  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  width: var(--mark-size);
  height: var(--mark-size);
  padding: var(--mark-pad);
  gap: var(--mark-gap);
  flex-shrink: 0;
  border-radius: 30%;
  background: linear-gradient(155deg, #2d323b 0%, #1a1d23 70%, #14161b 100%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.16),
    0 2px 6px rgba(27, 30, 36, 0.22);
}

.brand-mark i {
  display: block;
  height: var(--mark-bar);
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.92);
}

.brand-mark i:nth-child(1) {
  width: 100%;
}

.brand-mark i:nth-child(2) {
  width: 64%;
  opacity: 0.72;
}

.brand-mark i:nth-child(3) {
  width: 38%;
  opacity: 0.46;
}

.brand-mark--sm {
  --mark-size: 28px;
  --mark-pad: 7px;
  --mark-gap: 2.5px;
  --mark-bar: 2px;
}

.brand-mark--lg {
  --mark-size: 56px;
  --mark-pad: 13px;
  --mark-gap: 4px;
  --mark-bar: 3px;
}

.brand-mark--light {
  background: linear-gradient(155deg, rgba(255, 255, 255, 0.14), rgba(255, 255, 255, 0.06));
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.18);
}
</style>
