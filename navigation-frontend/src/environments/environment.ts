export const environment = {
  production: false,
  apiUrl: 'http://localhost:8081/api',
  wsUrl: 'http://localhost:8081/ws',
  
  // Configuration pour le dÃ©veloppement
  backend: {
    host: 'localhost',
    port: 8081,
    protocol: 'http'
  },
  
  // Configuration WebSocket
  websocket: {
    url: 'ws://localhost:8080/ws',
    reconnectDelay: 5000,
    heartbeatInterval: 30000
  },
  
  // Configuration application
  app: {
    name: 'Navigation Aérienne',
    version: '1.0.0',
    debug: true
  }
};

