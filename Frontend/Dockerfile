FROM node:20-alpine AS builder
WORKDIR /app

COPY package.json ./
RUN npm install --no-audit --no-fund

COPY tsconfig.json vite.config.ts tailwind.config.js postcss.config.js index.html ./
COPY src ./src

RUN npm run build && cp dist/index.html dist/dashboard.html

FROM nginx:1.27-alpine
RUN apk add --no-cache openssl && \
    mkdir -p /etc/nginx/ssl && \
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
      -keyout /etc/nginx/ssl/scada.key \
      -out /etc/nginx/ssl/scada.crt \
      -subj "/C=CO/ST=Narino/L=Pasto/O=Universidad Cooperativa de Colombia/OU=Ingenieria de Software/CN=scada-ucc-alejandro.77.113.90.86.sslip.io" \
      -addext "subjectAltName=DNS:scada-ucc-alejandro.77.113.90.86.sslip.io,DNS:scada-ucc.77-113-90-86.nip.io,IP:77.113.90.86"

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80 443
CMD ["nginx", "-g", "daemon off;"]
