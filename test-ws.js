const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:8080/ws/logs');

ws.on('open', () => {
    console.log('CONNECTED');
    ws.close();
});

ws.on('error', (err) => {
    console.log('ERROR:', err.message);
});

ws.on('close', () => {
    console.log('CLOSED');
});
