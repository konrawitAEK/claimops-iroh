# Angular 22 needs Node >= 22.22.3, so pin a minor that satisfies it rather than a possibly stale 22.x.
FROM node:22.23-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Writes /iroh.json (which gateway to reach the backend through) from the environment at container start.
COPY --chmod=755 docker-entrypoint.d/40-iroh-config.sh /docker-entrypoint.d/
COPY --from=build /app/dist/browser /usr/share/nginx/html
EXPOSE 80
