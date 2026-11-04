# LC backend — no npm dependencies, only the Node standard library.
FROM node:22-bookworm-slim

ENV NODE_ENV=production \
    PORT=8787 \
    DB_PATH=/data/lc.sqlite

WORKDIR /app

COPY --chown=node:node package.json ./
COPY --chown=node:node src ./src
COPY --chown=node:node scripts ./scripts

RUN mkdir -p /data && chown node:node /data

USER node
EXPOSE 8787

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.PORT||8787)+'/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "--no-warnings", "src/server.js"]
