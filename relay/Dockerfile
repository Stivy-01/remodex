FROM node:20-alpine AS runtime

ENV NODE_ENV=production \
    PORT=9000 \
    RELAY_BIND_HOST=0.0.0.0

WORKDIR /app

COPY package*.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY *.js ./
COPY *.mjs ./

USER node
EXPOSE 9000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "const http=require('http'); const port=process.env.PORT||9000; const req=http.get({host:'127.0.0.1', port, path:'/health', timeout:3000}, res=>process.exit(res.statusCode===200?0:1)); req.on('error',()=>process.exit(1)); req.on('timeout',()=>{req.destroy(); process.exit(1);});"

CMD ["npm", "start"]
