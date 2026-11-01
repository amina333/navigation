// Production: everything goes through nginx on the same host/port as the page
const wsProto = window.location.protocol === 'https:' ? 'wss://' : 'ws://';

export const environment = {
  production: true,
  apiUrl: '/api',
  wsUrl: '/ws',

  backend: {
    host: window.location.hostname,
    port: Number(window.location.port) || 80,
    protocol: window.location.protocol.replace(':', '')
  },

  websocket: {
    url: `${wsProto}${window.location.host}/ws`,
    reconnectDelay: 5000,
    heartbeatInterval: 30000
  },

  app: {
    name: 'Navigation Aérienne',
    version: '1.0.0',
    debug: false
  }
};
