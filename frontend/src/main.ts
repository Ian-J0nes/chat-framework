import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// Mount Vue app with router
createApp(App)
  .use(router)
  .mount('#app')
