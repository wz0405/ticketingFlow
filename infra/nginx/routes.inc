# HTTP/HTTPS 서버가 공유하는 라우팅 규칙
# 정적 FE 서빙 + API 리버스프록시(대기열/예매 분기) + WebSocket 업그레이드

location / {
    try_files $uri $uri/ /index.html;
}

# 대기열 업무만 queue-api로, 나머지는 booking-api로
location ~ ^/api/(queueEnter|queueStatus|queueLeave)$ {
    proxy_pass http://queue_api;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location /api/ {
    proxy_pass http://booking_api;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location /ws/queue {
    proxy_pass http://queue_ws;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 3600s;
}
