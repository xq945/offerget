<script setup>
import { BriefcaseBusiness } from 'lucide-vue-next'

defineProps({
  size: { type: String, default: 'md' },
  light: { type: Boolean, default: false },
  to: { type: String, default: '/' },
})
</script>

<template>
  <router-link :to="to" class="flex min-w-0 items-center gap-3">
    <span
      class="grid shrink-0 place-items-center rounded-xl bg-gradient-to-br from-blue-600 to-sky-400 text-white shadow-[0_12px_24px_rgba(37,99,235,0.22)]"
      :class="size === 'lg' ? 'h-12 w-12' : size === 'sm' ? 'h-9 w-9' : 'h-10 w-10'"
    >
      <BriefcaseBusiness :class="size === 'lg' ? 'h-6 w-6' : size === 'sm' ? 'h-4.5 w-4.5' : 'h-5 w-5'" />
    </span>
    <strong
      class="truncate font-black tracking-tight"
      :class="[
        light ? 'text-white' : 'text-slate-950',
        size === 'lg' ? 'text-3xl' : size === 'sm' ? 'text-xl' : 'text-2xl',
      ]"
    >
      Offer<span class="text-blue-600">Get</span>
    </strong>
  </router-link>
</template>
