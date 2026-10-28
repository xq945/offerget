<script setup>
import { computed } from 'vue'

import { statusMeta } from '@/utils/format'

const props = defineProps({
  status: { type: String, default: 'PENDING' },
  size: { type: String, default: 'md' },
})

const meta = computed(() => statusMeta(props.status))
</script>

<template>
  <span
    class="inline-flex items-center rounded-full font-bold ring-1 ring-inset"
    :class="[meta.tone, size === 'sm' ? 'px-2 py-0.5 text-[10px]' : 'px-2.5 py-1 text-[11px]']"
  >
    {{ meta.label }}
  </span>
</template>
