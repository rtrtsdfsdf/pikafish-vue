<template>
  <div id="app">
    <header>
      <h1>🎮 皮卡鱼中国象棋</h1>
      <p>Pikafish Chinese Chess - Vue + WASM</p>
    </header>
    <main>
      <ChessBoard />
    </main>
    <footer>
      <p>Powered by Pikafish WASM Engine</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import ChessBoard from './components/ChessBoard.vue';
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  min-height: 100vh;
}

#app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

header {
  text-align: center;
  padding: 20px;
  color: white;
}

header h1 {
  font-size: 32px;
  margin-bottom: 5px;
}

header p {
  font-size: 14px;
  opacity: 0.8;
}

main {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

footer {
  text-align: center;
  padding: 15px;
  color: white;
  opacity: 0.7;
  font-size: 12px;
}
</style>
