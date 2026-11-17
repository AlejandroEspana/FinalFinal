# =============================================================================
# Etapa 1: Compilación de la SPA React 18 + TypeScript + Vite + TailwindCSS
# =============================================================================
FROM node:20-alpine AS builder
WORKDIR /app

COPY package.json ./
RUN npm install --no-audit --no-fund

COPY tsconfig.json vite.config.ts tailwind.config.js postcss.config.js index.html ./
COPY src ./src

RUN npm run build && cp dist/index.html dist/dashboard.html

# =============================================================================
# Etapa 2: Servidor Ligero Nginx Alpine con Reverse Proxy y Soporte WebSocket
# =============================================================================
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
