import { createRouter, createWebHistory } from 'vue-router';
import ChessBoard from '@/components/ChessBoard.vue';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: ChessBoard
    }
  ]
});

export default router;
