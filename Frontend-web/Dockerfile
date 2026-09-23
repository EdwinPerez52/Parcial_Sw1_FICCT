FROM node:22-alpine AS build
RUN corepack enable
WORKDIR /workspace
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY Frontend-web/package.json Frontend-web/package.json
RUN pnpm install --frozen-lockfile
COPY Frontend-web Frontend-web
RUN pnpm --filter @modeler/web build

FROM nginx:1.29-alpine
COPY Frontend-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/Frontend-web/dist /usr/share/nginx/html
EXPOSE 80
