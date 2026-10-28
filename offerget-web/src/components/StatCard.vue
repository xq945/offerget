<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], default: 0 },
  hint: { type: String, default: '' },
  icon: { type: [Object, Function], required: true },
  tone: { type: String, default: 'bg-gradient-to-br from-blue-600 to-sky-400' },
  badge: { type: String, default: '' },
})
</script>

<template>
  <div class="soft-card group relative overflow-hidden p-5 transition duration-200 hover:-translate-y-0.5 hover:shadow-[0_20px_44px_rgba(37,99,235,0.14)]">
    <div class="pointer-events-none absolute -right-10 -top-10 h-24 w-24 rounded-full bg-blue-100/50 blur-2xl transition group-hover:bg-blue-200/60" />
    <div class="relative flex items-start justify-between gap-3">
      <div class="min-w-0">
        <p class="text-sm font-semibold text-slate-500">{{ label }}</p>
        <p class="mt-2 text-3xl font-black leading-none text-slate-950">{{ value }}</p>
        <p class="mt-2 line-clamp-2 text-xs leading-5 text-slate-400">{{ hint }}</p>
      </div>
      <span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl text-white shadow-[0_12px_24px_rgba(37,99,235,0.2)]" :class="tone">
        <component :is="icon" class="h-5 w-5" />
      </span>
    </div>
    <span v-if="badge" class="relative mt-3 inline-flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-1 text-[11px] font-bold text-blue-600">
      {{ badge }}
    </span>
  </div>
</template>
