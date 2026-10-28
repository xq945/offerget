<script setup>
import { Inbox } from 'lucide-vue-next'

defineProps({
  icon: { type: [Object, Function], default: () => Inbox },
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  actionLabel: { type: String, default: '' },
})

defineEmits(['action'])
</script>

<template>
  <div class="flex flex-col items-center justify-center rounded-2xl border border-dashed border-blue-200/80 bg-blue-50/30 px-6 py-10 text-center">
    <span class="grid h-12 w-12 place-items-center rounded-2xl bg-white text-blue-400 shadow-sm">
      <component :is="icon" class="h-5.5 w-5.5" />
    </span>
    <p class="mt-3 text-sm font-bold text-slate-700">{{ title }}</p>
    <p v-if="description" class="mt-1 max-w-sm text-xs leading-5 text-slate-500">{{ description }}</p>
    <button
      v-if="actionLabel"
      class="mt-4 rounded-full bg-blue-600 px-4 py-2 text-xs font-bold text-white shadow-[0_10px_20px_rgba(37,99,235,0.2)] transition hover:bg-blue-700"
      @click="$emit('action')"
    >
      {{ actionLabel }}
    </button>
  </div>
</template>
