const ALLOWED_ORIGINS = [
  'https://grid-admin.netlify.app',
  'https://ev-shop-management.netlify.app',
  'https://ev-booking.netlify.app',
  'http://localhost:5173',
  'http://localhost:5174',
  'http://localhost:5175'
];

module.exports = async (req, res) => {
  const origin = req.headers.origin;
  if (origin && (ALLOWED_ORIGINS.includes(origin) || origin.endsWith('.netlify.app') || origin.startsWith('http://localhost:'))) {
    res.setHeader('Access-Control-Allow-Origin', origin);
  } else {
    res.setHeader('Access-Control-Allow-Origin', ALLOWED_ORIGINS[0]);
  }
  
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD');
  res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type, X-Requested-With, Accept, Origin, Access-Control-Request-Method, Access-Control-Request-Headers');
  res.setHeader('Access-Control-Max-Age', '3600');

  // Handle preflight
  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  // Gateway health check
  if (req.url === '/' || req.url === '/api' || req.url === '/api/health') {
    return res.status(200).json({
      status: 'UP',
      service: 'VoltGrid Vercel API Gateway',
      supabaseHost: 'db.zabxtltqxpmfvjsvmwfv.supabase.co',
      configuredBackends: {
        targetBackendUrl: process.env.BACKEND_URL || 'Set BACKEND_URL environment variable to your Spring Boot server'
      },
      connectedFrontends: [
        'https://grid-admin.netlify.app',
        'https://ev-shop-management.netlify.app',
        'https://ev-booking.netlify.app'
      ]
    });
  }

  const backendUrl = process.env.BACKEND_URL;
  if (!backendUrl) {
    return res.status(502).json({
      error: 'BACKEND_URL environment variable not configured on Vercel.',
      message: 'Java Spring Boot applications require a persistent container runtime (such as Render or Railway) to maintain Supabase Hikari connection pools and background scheduling. Deploy the backend to Render/Railway and set BACKEND_URL in Vercel settings, or point your Netlify frontends directly to the backend URL.',
      supabaseHost: 'db.zabxtltqxpmfvjsvmwfv.supabase.co'
    });
  }

  try {
    const targetUrl = `${backendUrl.replace(/\/+$/, '')}${req.url}`;
    
    // Read request body if present
    let bodyBuffer = null;
    if (['POST', 'PUT', 'PATCH'].includes(req.method)) {
      bodyBuffer = await new Promise((resolve, reject) => {
        const chunks = [];
        req.on('data', chunk => chunks.push(chunk));
        req.on('end', () => resolve(Buffer.concat(chunks)));
        req.on('error', err => reject(err));
      });
    }

    const headers = { ...req.headers };
    delete headers.host;

    const fetchOptions = {
      method: req.method,
      headers,
      body: bodyBuffer && bodyBuffer.length > 0 ? bodyBuffer : undefined
    };

    const upstreamResponse = await fetch(targetUrl, fetchOptions);
    res.status(upstreamResponse.status);

    upstreamResponse.headers.forEach((val, key) => {
      if (key.toLowerCase() !== 'content-encoding') {
        res.setHeader(key, val);
      }
    });

    const data = await upstreamResponse.arrayBuffer();
    return res.send(Buffer.from(data));
  } catch (err) {
    return res.status(500).json({
      error: 'Upstream gateway error forwarding to Spring Boot backend',
      details: err.message
    });
  }
};
