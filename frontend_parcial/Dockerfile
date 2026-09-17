FROM node:22-alpine AS build
RUN corepack enable
WORKDIR /workspace
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY frontend_parcial/package.json frontend_parcial/package.json
RUN pnpm install --frozen-lockfile
COPY frontend_parcial frontend_parcial
RUN pnpm --filter @modeler/web build

FROM nginx:1.29-alpine
COPY frontend_parcial/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend_parcial/dist /usr/share/nginx/html
EXPOSE 80
