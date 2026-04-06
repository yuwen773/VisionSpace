<template>
  <div style="width: 100%; height: 100vh;">
    <DomeGallery1
      :images="[
        'https://images.unsplash.com/photo-1755331039789-7e5680e26e8f?q=80&w=774&auto=format&fit=crop',
        'https://images.unsplash.com/photo-1755569309049-98410b94f66d?q=80&w=772&auto=format&fit=crop',
        'https://images.unsplash.com/photo-1755497595318-7e5e3523854f?q=80&w=774&auto=format&fit=crop'
      ]"
      :fit="0.8"
      fit-basis="auto"
      :min-radius="600"
      :segments="34"
      :drag-sensitivity="20"
      :enlarge-transition-ms="300"
      :grayscale="true"
      overlay-blur-color="#060010"
      image-border-radius="30px"
      opened-image-width="250px"
      opened-image-height="350px"
    />
  </div>
</template>

<script setup lang="ts">
import DomeGallery1 from "@/components/vue-bits/components/DomeGallery.vue";
</script>
