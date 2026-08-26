import { defineConfig, loadEnv } from 'vite';
import handler from './api/analyze.js';

function serverlessApiDevPlugin() {
  return {
    name: 'serverless-api-dev',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        if (req.url && req.url.startsWith('/api/analyze')) {
          let body = '';
          req.on('data', (chunk) => {
            body += chunk;
          });
          req.on('end', async () => {
            try {
              req.body = body ? JSON.parse(body) : {};
            } catch (e) {
              req.body = {};
            }

            res.status = function (code) {
              res.statusCode = code;
              return this;
            };
            res.json = function (data) {
              res.setHeader('Content-Type', 'application/json');
              res.end(JSON.stringify(data));
            };

            try {
              await handler(req, res);
            } catch (err) {
              res.statusCode = 500;
              res.setHeader('Content-Type', 'application/json');
              res.end(JSON.stringify({ error: err.message }));
            }
          });
          return;
        }
        next();
      });
    }
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  process.env.GEMINI_API_KEY = env.GEMINI_API_KEY || env.VITE_GEMINI_API_KEY;

  return {
    root: './',
    publicDir: 'public',
    plugins: [serverlessApiDevPlugin()],
    server: {
      port: 3000,
      open: true,
      proxy: {
        '/api/plantnet': {
          target: 'https://my-api.plantnet.org',
          changeOrigin: true,
          secure: false,
          headers: {
            'Origin': 'https://my-api.plantnet.org',
            'Referer': 'https://my-api.plantnet.org/'
          },
          rewrite: (path) => path.replace(/^\/api\/plantnet/, '')
        }
      }
    },
    build: {
      outDir: 'dist',
      emptyOutDir: true
    }
  };
});
