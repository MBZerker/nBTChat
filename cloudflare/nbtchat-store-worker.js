const PRODUCTS = {
  cartela_de_eventos: {
    id: "cartela_de_eventos",
    title: "Cartela de eventos",
    kind: "item",
    free: false,
    price: 2.49,
    dailyFee: 1.25,
    power: 1.25,
    durationDays: 1,
    footer: "Produto destinado exclusivamente para organizacao de eventos familiares, recreativos e chas beneficentes.",
  },
  palitinhos: {
    id: "palitinhos",
    title: "Palitinhos",
    kind: "game",
    free: true,
    price: 0,
    dailyFee: 0,
    power: 1.25,
    durationDays: 0,
    footer: "Jogo gratuito para partidas locais entre contatos conectados pelo nBTChat.",
  },
};

const SHORT_LINK_CORS_HEADERS = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "POST, OPTIONS",
  "access-control-allow-headers": "content-type",
};

async function getProducts(env) {
  const defaults = JSON.parse(JSON.stringify(PRODUCTS));
  try {
    const raw = await env.STORE.get("config:products");
    if (!raw) {
      return defaults;
    }
    const saved = JSON.parse(raw);
    for (const [id, product] of Object.entries(defaults)) {
      const custom = saved && saved[id] ? saved[id] : {};
      defaults[id] = normalizeProduct({ ...product, ...custom }, product);
    }
    if (saved && typeof saved === "object") {
      for (const [id, product] of Object.entries(saved)) {
        if (!defaults[id] && product && typeof product === "object") {
          defaults[id] = normalizeProduct({ ...product, id }, PRODUCTS.cartela_de_eventos);
        }
      }
    }
  } catch (_) {
  }
  return defaults;
}

async function getProduct(env, productId) {
  const products = await getProducts(env);
  return products[productId] || null;
}

function normalizeProduct(product, fallback) {
  const base = fallback || PRODUCTS.cartela_de_eventos;
  const free = product.free === true || product.free === "on" || product.free === "true";
  let price = free ? 0 : Math.max(0.01, Number(product.price) || base.price);
  let dailyFee = Math.max(0, Number(product.dailyFee) || base.dailyFee || 0);
  let power = Math.max(1, Number(product.power) || base.power || 1.25);
  let durationDays = clampInt(product.durationDays, base.durationDays, 1, 365);
  if (base.id === "cartela_de_eventos" && price === 4.99 && durationDays === 15 && !Number(product.dailyFee)) {
    price = base.price;
    dailyFee = base.dailyFee || 0;
    power = base.power || 1.25;
    durationDays = base.durationDays;
  }
  return {
    id: clean(product.id || base.id),
    title: clean(product.title || base.title),
    kind: clean(product.kind || base.kind || "item"),
    free,
    price,
    dailyFee,
    power,
    durationDays,
    footer: clean(product.footer || base.footer),
  };
}

async function createShortLink(request, env, originUrl) {
  const data = await readBody(request);
  const target = clean(data.url);
  if (!isAllowedShortTarget(target, originUrl.origin)) {
    return json({ ok: false, error: "invalid_url" }, 400, SHORT_LINK_CORS_HEADERS);
  }

  const normalized = new URL(target).toString();
  const targetHash = await sha256(`short:${normalized}`);
  const existing = await env.STORE.get(`short:index:${targetHash}`);
  if (existing) {
    return json({ ok: true, code: existing, shortUrl: `${originUrl.origin}/s/${existing}`, url: normalized }, 200, SHORT_LINK_CORS_HEADERS);
  }

  for (let attempt = 0; attempt < 8; attempt++) {
    const code = randomShortCode(7);
    const key = `short:${code}`;
    if (await env.STORE.get(key)) {
      continue;
    }
    const record = { url: normalized, createdAt: Date.now() };
    await env.STORE.put(key, JSON.stringify(record));
    await env.STORE.put(`short:index:${targetHash}`, code);
    return json({ ok: true, code, shortUrl: `${originUrl.origin}/s/${code}`, url: normalized }, 200, SHORT_LINK_CORS_HEADERS);
  }

  return json({ ok: false, error: "short_code_unavailable" }, 503, SHORT_LINK_CORS_HEADERS);
}

async function createShareLink(request, env, originUrl) {
  const data = await readBody(request);
  const payload = clean(data.payload);
  if (!isValidSharePayload(payload)) {
    return json({ ok: false, error: "invalid_payload" }, 400, SHORT_LINK_CORS_HEADERS);
  }

  const payloadHash = await sha256(`share:${payload}`);
  const existing = await env.STORE.get(`share:index:${payloadHash}`);
  if (existing) {
    return json({ ok: true, code: existing, shortUrl: `${originUrl.origin}/s/${existing}` }, 200, SHORT_LINK_CORS_HEADERS);
  }

  for (let attempt = 0; attempt < 8; attempt++) {
    const code = randomShortCode(7);
    if (await env.STORE.get(`short:${code}`)) {
      continue;
    }
    const record = { type: "share", payload, createdAt: Date.now() };
    await env.STORE.put(`short:${code}`, JSON.stringify(record));
    await env.STORE.put(`share:index:${payloadHash}`, code);
    return json({ ok: true, code, shortUrl: `${originUrl.origin}/s/${code}` }, 200, SHORT_LINK_CORS_HEADERS);
  }

  return json({ ok: false, error: "short_code_unavailable" }, 503, SHORT_LINK_CORS_HEADERS);
}

async function sharePayload(env, url) {
  const code = clean(url.pathname.replace(/^\/share\//, "")).replace(/[^A-Za-z0-9]/g, "");
  if (!code) {
    return json({ ok: false, error: "invalid_code" }, 400, SHORT_LINK_CORS_HEADERS);
  }
  const raw = await env.STORE.get(`short:${code}`);
  if (!raw) {
    return json({ ok: false, error: "not_found" }, 404, SHORT_LINK_CORS_HEADERS);
  }
  try {
    const record = JSON.parse(raw);
    if (record.type !== "share" || !isValidSharePayload(record.payload)) {
      return json({ ok: false, error: "invalid_share" }, 400, SHORT_LINK_CORS_HEADERS);
    }
    return json({ ok: true, code, payload: record.payload }, 200, SHORT_LINK_CORS_HEADERS);
  } catch (_) {
    return json({ ok: false, error: "invalid_share" }, 500, SHORT_LINK_CORS_HEADERS);
  }
}

async function shortRedirect(env, url) {
  const code = clean(url.pathname.replace(/^\/s\//, "")).replace(/[^A-Za-z0-9]/g, "");
  if (!code) {
    return resultHtml("Link invalido", "Este link curto nao esta completo.", false, 400);
  }
  const raw = await env.STORE.get(`short:${code}`);
  if (!raw) {
    return resultHtml("Link nao encontrado", "Este link curto nao existe ou expirou.", false, 404);
  }
  try {
    const record = JSON.parse(raw);
    if (record.type === "share" && isValidSharePayload(record.payload)) {
      return shareLandingPage(url, code);
    }
    const target = clean(record.url);
    if (!isAllowedShortTarget(target, url.origin)) {
      return resultHtml("Link bloqueado", "Este destino nao e permitido.", false, 400);
    }
    return new Response(null, {
      status: 302,
      headers: {
        location: target,
        "cache-control": "no-store",
      },
    });
  } catch (_) {
    return resultHtml("Link corrompido", "Nao foi possivel abrir este link.", false, 500);
  }
}

function shareLandingPage(url, code) {
  const appUrl = `nbtchat://share?c=${encodeURIComponent(code)}&u=${encodeURIComponent(url.origin)}`;
  return html(`<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Abrir no nBTChat</title>
  <style>
    body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #f4f7f5; color: #17212b; font-family: Arial, sans-serif; }
    main { width: min(92vw, 430px); background: white; border: 1px solid #d7ddd8; border-radius: 18px; padding: 24px; box-shadow: 0 16px 50px #0002; }
    .badge { width: 68px; height: 68px; border-radius: 20px; display: grid; place-items: center; background: #ddf7e8; color: #16a34a; font-size: 32px; margin-bottom: 14px; font-weight: 900; }
    h1 { margin: 0; font-size: 28px; }
    p { color: #52606d; line-height: 1.45; }
    a { display: block; text-align: center; text-decoration: none; border-radius: 14px; padding: 14px; margin-top: 12px; font-weight: 900; }
    .primary { background: #16a34a; color: white; }
    .secondary { background: #edf2f0; color: #17212b; }
  </style>
</head>
<body>
  <main>
    <div class="badge">nB</div>
    <h1>Abrir item no nBTChat</h1>
    <p>Se o app estiver instalado, abra o contato e o item compartilhado. Se ainda nao tiver, baixe o APK e volte por este link.</p>
    <a id="openApp" class="primary" href="${escapeHtml(appUrl)}">Abrir no nBTChat</a>
    <a class="secondary" href="https://mbzerker.github.io/nBTChat/nBTChat.apk">Baixar nBTChat</a>
  </main>
  <script>
    const appUrl = ${JSON.stringify(appUrl)};
    setTimeout(() => { location.href = appUrl; }, 350);
  </script>
</body>
</html>`);
}

function isAllowedShortTarget(target, workerOrigin) {
  try {
    const parsed = new URL(target);
    if (parsed.origin === workerOrigin && (parsed.pathname.startsWith("/checkout") || parsed.pathname.startsWith("/recover"))) {
      return true;
    }
    if (parsed.protocol !== "https:") {
      return false;
    }
    if (parsed.hostname === "mbzerker.github.io") {
      return parsed.pathname.startsWith("/nBTChat/") || parsed.pathname.startsWith("/CompraLink/");
    }
  } catch (_) {
  }
  return false;
}

function isValidSharePayload(payload) {
  if (!payload || payload.length > 120000 || !/^[A-Za-z0-9_-]+={0,2}$/.test(payload)) {
    return false;
  }
  try {
    let normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    while (normalized.length % 4 !== 0) {
      normalized += "=";
    }
    const jsonText = atob(normalized);
    const parsed = JSON.parse(jsonText);
    return parsed && Number(parsed.v) === 1 && typeof parsed.kind === "string" && typeof parsed.body === "string";
  } catch (_) {
    return false;
  }
}

function randomShortCode(length) {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  let code = "";
  for (const byte of bytes) {
    code += alphabet[byte % alphabet.length];
  }
  return code;
}

function adminKey(env) {
  return clean(env.NBTCHAT_ADMIN_KEY || env.ADMIN_API_KEY || env.API_KEY || "");
}

async function adminSessionValue(env) {
  const key = adminKey(env);
  return key ? await sha256(`nbtchat-admin:${key}`) : "";
}

function cookieValue(request, name) {
  const raw = request.headers.get("cookie") || "";
  for (const part of raw.split(";")) {
    const [key, ...value] = part.trim().split("=");
    if (key === name) {
      return value.join("=");
    }
  }
  return "";
}

async function isAdminRequest(request, env) {
  const expected = await adminSessionValue(env);
  return !!expected && cookieValue(request, "nbtchat_admin") === expected;
}

async function adminLogin(request, env) {
  const data = await readBody(request);
  const expected = adminKey(env);
  const provided = clean(data.key);
  if (!expected || provided !== expected) {
    return resultHtml("Acesso negado", "Chave ADM ausente ou inválida.", false, 403);
  }
  const session = await adminSessionValue(env);
  return html("<!doctype html><meta charset=\"utf-8\"><meta http-equiv=\"refresh\" content=\"0; url=/admin\"><p>Entrando...</p>", 303, {
    "set-cookie": `nbtchat_admin=${session}; HttpOnly; Secure; SameSite=Strict; Path=/admin; Max-Age=7200`,
    location: "/admin",
  });
}

async function adminPage(request, env) {
  const authenticated = await isAdminRequest(request, env);
  const products = await getProducts(env);
  return html(`<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ADM nBTChat Store</title>
  <style>
    :root { color-scheme: light dark; font-family: Arial, sans-serif; }
    body { margin: 0; min-height: 100vh; background: #f4f7f5; color: #17212b; display: grid; place-items: center; padding: 24px 0; }
    main { width: min(94vw, 900px); background: white; border: 1px solid #d7ddd8; border-radius: 18px; padding: 24px; box-shadow: 0 16px 50px #0002; }
    h1 { margin: 0 0 6px; font-size: 28px; }
    p { color: #52606d; line-height: 1.45; }
    label { display: block; font-size: 13px; font-weight: 800; margin-top: 14px; }
    input, textarea { width: 100%; box-sizing: border-box; border: 1px solid #cbd5cf; border-radius: 12px; padding: 13px; font-size: 16px; margin-top: 6px; }
    textarea { min-height: 92px; resize: vertical; }
    button { border: 0; border-radius: 14px; padding: 14px 18px; margin-top: 18px; background: #16a34a; color: white; font-weight: 900; font-size: 16px; }
    .locked { background: #fff7ed; border: 1px solid #fed7aa; color: #9a3412; padding: 12px; border-radius: 12px; }
    .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
    .product { border: 1px solid #d7ddd8; border-radius: 16px; padding: 18px; margin-top: 18px; background: #fbfcfb; }
    .product h2 { margin: 0; font-size: 20px; }
    .product-id { margin-top: 4px; color: #64748b; font-size: 12px; font-weight: 800; }
    .add { background: #eefbf3; border-color: #bbf7d0; }
    .hint { font-size: 13px; }
    .login-id { background: #eef2f0; color: #52606d; }
    .check { display: flex; align-items: center; gap: 10px; margin-top: 14px; font-weight: 800; }
    .check input { width: auto; margin: 0; }
    @media (max-width: 640px) { .grid { grid-template-columns: 1fr; } }
    @media (prefers-color-scheme: dark) {
      body { background: #101820; color: #f7f8f5; }
      main { background: #18232c; border-color: #2f3b45; }
      input, textarea { background: #24313b; border-color: #2f3b45; color: #f7f8f5; }
      .login-id { background: #24313b; color: #cbd5cf; }
      .locked { background: #3a2615; border-color: #9a5a1f; color: #fed7aa; }
      .product { background: #1d2933; border-color: #2f3b45; }
      .add { background: #123328; border-color: #166534; }
    }
  </style>
</head>
<body>
  <main>
    <h1>ADM nBTChat Store</h1>
    <p>Configure os itens oficiais da loja. A chave ADM fica apenas no envio do login e depois a sessão usa cookie seguro.</p>
    ${authenticated ? adminProductsForm(products) : adminLoginForm()}
  </main>
</body>
</html>`);
}

function adminLoginForm() {
  return `<div class="locked">Entre com a chave ADM configurada nas variáveis do Worker.</div>
    <form method="post" action="/admin/login">
      <label>Identificação</label>
      <input class="login-id" name="username" value="nBTChat ADM" autocomplete="username" readonly>
      <label>Chave ADM</label>
      <input name="key" type="password" autocomplete="current-password" required autofocus>
      <button type="submit">Entrar</button>
    </form>`;
}

function adminProductsForm(products) {
  const forms = Object.values(products)
    .sort((a, b) => String(a.title).localeCompare(String(b.title), "pt-BR"))
    .map((product) => adminProductForm(product, false))
    .join("");
  const blank = adminProductForm({
    id: "",
    title: "",
    price: PRODUCTS.cartela_de_eventos.price,
    dailyFee: PRODUCTS.cartela_de_eventos.dailyFee,
    power: PRODUCTS.cartela_de_eventos.power,
    durationDays: PRODUCTS.cartela_de_eventos.durationDays,
    kind: "game",
    free: true,
    footer: PRODUCTS.cartela_de_eventos.footer,
  }, true);
  return `<p class="hint">Itens salvos aqui aparecem no endpoint da loja e podem ser usados pelo app conforme forem implementados. A Cartela de eventos já usa preço, validade, taxa diária e potencializador vindos daqui.</p>
    ${forms}
    ${blank}`;
}

function adminProductForm(product, isNew) {
  return `<section class="product ${isNew ? "add" : ""}">
    <h2>${isNew ? "Adicionar novo item" : escapeHtml(product.title)}</h2>
    ${isNew ? `<p class="product-id">Use um ID curto, sem espaços. Ex.: adesivo_premium</p>` : `<p class="product-id">${escapeHtml(product.id)}</p>`}
    <form method="post" action="/admin/products">
      <label>ID do item</label>
      <input name="id" value="${escapeHtml(product.id)}" required maxlength="60" pattern="[A-Za-z0-9_-]+">
      <label>Nome do item</label>
      <input name="title" value="${escapeHtml(product.title)}" required maxlength="80">
      <label>Tipo</label>
      <select name="kind" style="width:100%;box-sizing:border-box;border:1px solid #cbd5cf;border-radius:12px;padding:13px;font-size:16px;margin-top:6px">
        <option value="item" ${product.kind === "item" ? "selected" : ""}>Item</option>
        <option value="game" ${product.kind === "game" ? "selected" : ""}>Jogo</option>
      </select>
      <label class="check"><input name="free" type="checkbox" ${product.free ? "checked" : ""}> Gratuito</label>
      <div class="grid">
        <div>
          <label>Preço em R$</label>
          <input name="price" value="${escapeHtml(String(product.price.toFixed(2)).replace(".", ","))}" required inputmode="decimal">
        </div>
        <div>
          <label>Validade padrão em dias</label>
          <input name="durationDays" value="${escapeHtml(String(product.durationDays))}" required inputmode="numeric">
        </div>
        <div>
          <label>Taxa diária em R$</label>
          <input name="dailyFee" value="${escapeHtml(String((product.dailyFee || 0).toFixed(2)).replace(".", ","))}" required inputmode="decimal">
        </div>
        <div>
          <label>Potencializador</label>
          <input name="power" value="${escapeHtml(String(product.power || 1.25).replace(".", ","))}" required inputmode="decimal">
        </div>
      </div>
      <label>Rodapé/observação</label>
      <textarea name="footer" required>${escapeHtml(product.footer)}</textarea>
      <button type="submit">${isNew ? "Adicionar item" : "Salvar item"}</button>
    </form>
  </section>`;
}

async function saveAdminProducts(request, env) {
  if (!(await isAdminRequest(request, env))) {
    return resultHtml("Acesso negado", "Faça login na área ADM para salvar.", false, 403);
  }
  const data = await readBody(request);
  const products = await getProducts(env);
  const id = clean(data.id || "cartela_de_eventos");
  const fallback = products[id] || PRODUCTS[id] || PRODUCTS.cartela_de_eventos;
  products[id] = normalizeProduct({
    id,
    title: data.title,
    kind: data.kind,
    free: data.free === "on",
    price: decimalNumber(data.price),
    dailyFee: decimalNumber(data.dailyFee),
    power: decimalNumber(data.power),
    durationDays: data.durationDays,
    footer: data.footer,
  }, fallback);
  await env.STORE.put("config:products", JSON.stringify(products));
  return resultHtml("Item salvo", "As configurações da loja foram atualizadas.", true);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (request.method === "GET" && url.pathname === "/") {
        return json({ ok: true, products: await getProducts(env) });
      }
      if (request.method === "GET" && url.pathname === "/admin") {
        return adminPage(request, env);
      }
      if (request.method === "POST" && url.pathname === "/admin/login") {
        return adminLogin(request, env);
      }
      if (request.method === "POST" && url.pathname === "/admin/products") {
        return saveAdminProducts(request, env);
      }
      if (request.method === "OPTIONS" && (url.pathname === "/shorten" || url.pathname === "/share-link" || url.pathname.startsWith("/share/"))) {
        return new Response(null, { status: 204, headers: SHORT_LINK_CORS_HEADERS });
      }
      if (request.method === "POST" && url.pathname === "/shorten") {
        return createShortLink(request, env, url);
      }
      if (request.method === "POST" && url.pathname === "/share-link") {
        return createShareLink(request, env, url);
      }
      if (request.method === "GET" && url.pathname.startsWith("/share/")) {
        return sharePayload(env, url);
      }
      if (request.method === "GET" && url.pathname.startsWith("/s/")) {
        return shortRedirect(env, url);
      }
      if (request.method === "GET" && url.pathname === "/checkout") {
        return checkoutPage(url, env);
      }
      if (request.method === "GET" && url.pathname === "/recover") {
        return recoverPage(url, env);
      }
      if (request.method === "POST" && url.pathname === "/create-payment") {
        return createPayment(request, env, url);
      }
      if (request.method === "POST" && url.pathname === "/recover-purchase") {
        return recoverPurchase(request, env);
      }
      if (request.method === "POST" && url.pathname === "/webhook/mercadopago") {
        return mercadoPagoWebhook(request, env, url);
      }
      if (request.method === "GET" && url.pathname === "/entitlement") {
        return entitlement(env, url);
      }
      if (request.method === "GET" && url.pathname === "/cartela/state") {
        return cartelaState(env, url);
      }
      if (request.method === "POST" && url.pathname === "/cartela/register") {
        return registerCartela(request, env);
      }
      if (request.method === "POST" && url.pathname === "/cartela/choose") {
        return chooseCartelaNumber(request, env);
      }
      if (request.method === "POST" && url.pathname === "/cartela/confirm") {
        return confirmCartelaNumber(request, env);
      }
      if (request.method === "POST" && url.pathname === "/cartela/rename-choice") {
        return renameCartelaChoice(request, env);
      }
      if (request.method === "POST" && url.pathname === "/cartela/restore-choice") {
        return restoreCartelaChoice(request, env);
      }
      if (request.method === "POST" && url.pathname === "/cartela/delete-choice") {
        return deleteCartelaChoice(request, env);
      }
      if (request.method === "GET" && ["/success", "/pending", "/failure"].includes(url.pathname)) {
        return resultPage(url.pathname, env, url);
      }
      return json({ error: "not_found" }, 404);
    } catch (error) {
      return json({ error: error.message || "server_error" }, 500);
    }
  },
};

async function createPayment(request, env, url) {
  const data = await readBody(request);
  const product = await getProduct(env, data.productId || "cartela_de_eventos");
  if (!product) {
    return json({ error: "invalid_product" }, 400);
  }

  const deviceId = clean(data.deviceId);
  const buyerName = clean(data.buyerName);
  const buyerCpf = onlyDigits(data.buyerCpf);
  const cartelas = normalizeCartelaOrder(data.cartelas, product);
  const totalPrice = cartelas.reduce((sum, item) => sum + item.price, 0);
  if (!deviceId || buyerName.length < 3 || buyerCpf.length !== 11) {
    return json({ error: "invalid_buyer_data" }, 400);
  }

  const externalReference = `nbtchat:${product.id}:${deviceId}:${Date.now()}`;
  const recoveryCode = generateRecoveryCode();
  const recoveryCodeHash = await sha256(normalizeRecoveryCode(recoveryCode));
  await env.STORE.put(`pending:${externalReference}`, JSON.stringify({
    productId: product.id,
    deviceId,
    buyerName,
    cpfHash: await sha256(buyerCpf),
    cpfLast4: buyerCpf.slice(-4),
    recoveryCode,
    recoveryCodeHash,
    recoveryCodeHint: recoveryCode.slice(-4),
    cartelas,
    totalPrice,
    createdAt: Date.now(),
  }), { expirationTtl: 2 * 24 * 60 * 60 });

  const preference = {
    items: [{
      id: product.id,
      title: product.title,
      quantity: 1,
      currency_id: "BRL",
      unit_price: Number(totalPrice.toFixed(2)),
    }],
    payer: {
      name: buyerName,
      identification: {
        type: "CPF",
        number: buyerCpf,
      },
    },
    external_reference: externalReference,
    notification_url: `${url.origin}/webhook/mercadopago`,
    back_urls: {
      success: `${url.origin}/success`,
      pending: `${url.origin}/pending`,
      failure: `${url.origin}/failure`,
    },
    auto_return: "approved",
  };

  const mp = await fetch("https://api.mercadopago.com/checkout/preferences", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.MP_ACCESS_TOKEN}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(preference),
  });
  const body = await mp.json();
  if (!mp.ok) {
    return json({ error: "mercadopago_error", detail: body }, 502);
  }

  if (isFormRequest(request)) {
    return recoveryCodePage(product, recoveryCode, preferenceCheckoutUrl(body, env));
  }
  return json({
    preferenceId: body.id,
    initPoint: body.init_point,
    sandboxInitPoint: body.sandbox_init_point,
    externalReference,
  });
}

function normalizeCartelaOrder(raw, product) {
  let parsed = [];
  try {
    parsed = typeof raw === "string" && raw.trim() ? JSON.parse(raw) : [];
  } catch (_) {
    parsed = [];
  }
  if (!Array.isArray(parsed) || !parsed.length) {
    parsed = [{ days: product.durationDays || 1 }];
  }
  return parsed.slice(0, 20).map((item, index) => {
    const days = clampInt(item && item.days, product.durationDays || 1, 1, 365);
    const extraDays = Math.max(0, days - clampInt(product.durationDays, 1, 1, 365));
    const price = product.price + (Number(product.dailyFee) || 0) * Math.pow(extraDays, Number(product.power) || 1.25);
    return {
      index: index + 1,
      days,
      price: Number(price.toFixed(2)),
    };
  });
}

async function mercadoPagoWebhook(request, env, url) {
  let paymentId = url.searchParams.get("data.id") || url.searchParams.get("id");
  try {
    const body = await request.json();
    paymentId = paymentId || body?.data?.id || body?.id;
  } catch (_) {
  }
  if (!paymentId) {
    return json({ ok: true });
  }

  const mp = await fetch(`https://api.mercadopago.com/v1/payments/${paymentId}`, {
    headers: { Authorization: `Bearer ${env.MP_ACCESS_TOKEN}` },
  });
  if (!mp.ok) {
    return json({ ok: false }, 200);
  }
  const payment = await mp.json();
  if (payment.status !== "approved" || !payment.external_reference) {
    return json({ ok: true, status: payment.status || "" });
  }

  const activation = await activateApprovedPayment(env, payment, paymentId);
  return json({ ok: true, activated: activation.activated || false, skipped: activation.skipped || "" });
}

async function activateApprovedPayment(env, payment, paymentId) {
  const externalReference = clean(payment.external_reference);
  const productId = externalReference.split(":")[1] || "";
  const product = await getProduct(env, productId);
  if (!product) {
    return { activated: false, skipped: "product_not_found" };
  }

  const existingRaw = await env.STORE.get(`payment:${paymentId}:${product.id}`);
  if (existingRaw) {
    const existing = JSON.parse(existingRaw);
    await writeEntitlement(env, existing.deviceId, product, existing);
    const pendingRaw = await env.STORE.get(`pending:${externalReference}`);
    const pending = pendingRaw ? JSON.parse(pendingRaw) : null;
    return {
      activated: true,
      product,
      paymentRecord: existing,
      recoveryCode: pending?.recoveryCode || "",
    };
  }

  const pendingRaw = await env.STORE.get(`pending:${externalReference}`);
  if (!pendingRaw) {
    return { activated: false, skipped: "pending_not_found" };
  }
  const pending = JSON.parse(pendingRaw);
  if (pending.productId !== product.id) {
    return { activated: false, skipped: "product_mismatch" };
  }

  const cartelas = Array.isArray(pending.cartelas) && pending.cartelas.length ? pending.cartelas : [{ days: product.durationDays, price: product.price }];
  const maxDays = Math.max(...cartelas.map((item) => clampInt(item.days, product.durationDays, 1, 365)));
  const expiresAt = Date.now() + maxDays * 24 * 60 * 60 * 1000;
  const recoveryCodeHash = pending.recoveryCodeHash || await sha256(normalizeRecoveryCode(pending.recoveryCode || ""));
  const paymentRecord = {
    productId: product.id,
    title: product.title,
    deviceId: pending.deviceId,
    paymentId: String(paymentId),
    externalReference,
    buyerName: pending.buyerName,
    cpfHash: pending.cpfHash,
    cpfLast4: pending.cpfLast4,
    recoveryCodeHash,
    recoveryCodeHint: pending.recoveryCodeHint || (pending.recoveryCode || "").slice(-4),
    cartelas,
    totalPrice: Number(pending.totalPrice) || product.price,
    approvedAt: payment.date_approved || payment.date_created || new Date().toISOString(),
    expiresAt,
    updatedAt: Date.now(),
  };
  await writeEntitlement(env, pending.deviceId, product, paymentRecord);
  await env.STORE.put(`payment:${paymentId}:${product.id}`, JSON.stringify(paymentRecord));
  await env.STORE.put(`recovery:${product.id}:${recoveryCodeHash}`, JSON.stringify(paymentRecord));
  return {
    activated: true,
    product,
    paymentRecord,
    recoveryCode: pending.recoveryCode || "",
  };
}

async function writeEntitlement(env, deviceId, product, record) {
  await env.STORE.put(`entitlement:${deviceId}:${product.id}`, JSON.stringify({
    active: true,
    productId: product.id,
    title: product.title,
    expiresAt: record.expiresAt,
    paymentId: String(record.paymentId || ""),
    externalReference: record.externalReference || "",
    buyerName: record.buyerName || "",
    cpfHash: record.cpfHash || "",
    cpfLast4: record.cpfLast4 || "",
    recoveryCodeHash: record.recoveryCodeHash || "",
    recoveryCodeHint: record.recoveryCodeHint || "",
    approvedAt: record.approvedAt || "",
    updatedAt: Date.now(),
  }));
}

async function recoverPurchase(request, env) {
  const data = await readBody(request);
  const product = await getProduct(env, data.productId || "cartela_de_eventos");
  const deviceId = clean(data.deviceId);
  const normalizedRecoveryCode = normalizeRecoveryCode(data.recoveryCode);
  const recoveryCodeHash = normalizedRecoveryCode ? await sha256(normalizedRecoveryCode) : "";
  const buyerCpf = onlyDigits(data.buyerCpf);
  if (!product || !deviceId || !recoveryCodeHash || buyerCpf.length !== 11) {
    return resultHtml("Dados incompletos", "Confira CPF e codigo de recuperacao e tente novamente.", false);
  }

  const cpfHash = await sha256(buyerCpf);
  const indexed = await env.STORE.get(`recovery:${product.id}:${recoveryCodeHash}`);
  if (!indexed) {
    return resultHtml("Codigo nao encontrado", "Confira o codigo de recuperacao nBTChat. Ele foi mostrado antes de ir ao Mercado Pago.", false);
  }
  const paymentRecord = JSON.parse(indexed);
  if (paymentRecord.cpfHash !== cpfHash || paymentRecord.recoveryCodeHash !== recoveryCodeHash) {
    return resultHtml("Nao foi possivel recuperar", "O CPF informado nao confere com essa compra.", false);
  }
  if (!paymentRecord.expiresAt || paymentRecord.expiresAt <= Date.now()) {
    return resultHtml("Compra expirada", "O periodo de uso dessa compra ja terminou.", false);
  }
  const originalDeviceId = clean(paymentRecord.deviceId);
  if (originalDeviceId && originalDeviceId !== deviceId) {
    const originalEntitlement = await activeEntitlement(env, originalDeviceId, product.id);
    if (originalEntitlement.active) {
      return resultHtml(
        "Compra ativa em outro aparelho",
        "Esta compra ainda esta ativa no aparelho original. Para evitar clonagem, ela nao pode ser recuperada em outro celular enquanto estiver em uso.",
        false,
        409
      );
    }
  }

  await writeEntitlement(env, deviceId, product, paymentRecord);
  return resultHtml("Compra recuperada", "Volte ao nBTChat e toque em verificar para liberar a Cartela de eventos.", true);
}

async function entitlement(env, url) {
  const deviceId = clean(url.searchParams.get("deviceId"));
  const productId = clean(url.searchParams.get("productId"));
  if (!deviceId || !productId) {
    return json({ error: "missing_params" }, 400);
  }
  const raw = await env.STORE.get(`entitlement:${deviceId}:${productId}`);
  if (!raw) {
    return json({ active: false, productId });
  }
  const record = JSON.parse(raw);
  if (!record.expiresAt || record.expiresAt <= Date.now()) {
    return json({ active: false, productId, expiresAt: record.expiresAt || 0 });
  }
  return json({
    active: true,
    productId,
    title: record.title || (await getProduct(env, productId))?.title || productId,
    expiresAt: record.expiresAt,
    approvedAt: record.approvedAt || "",
    updatedAt: record.updatedAt || 0,
  });
}

async function registerCartela(request, env) {
  const data = await readBody(request);
  const tableId = clean(data.tableId);
  const ownerDeviceId = clean(data.ownerDeviceId);
  if (!tableId || !ownerDeviceId) {
    return json({ error: "missing_params" }, 400);
  }
  const active = await activeEntitlement(env, ownerDeviceId, "cartela_de_eventos");
  if (!active.active) {
    return json({ error: "entitlement_required" }, 403);
  }
  const existing = await readCartela(env, tableId);
  if (existing.ownerDeviceId && existing.ownerDeviceId !== ownerDeviceId) {
    return json({ error: "owner_mismatch" }, 403);
  }
  const cartela = {
    tableId,
    productId: "cartela_de_eventos",
    title: clean(data.title),
    ownerDeviceId,
    ownerName: clean(data.ownerName),
    ownerMessage: clean(data.ownerMessage),
    copyText: clean(data.copyText),
    ownerContact: clean(data.ownerContact),
    allowReservations: false,
    reservationHours: 0,
    expiresAt: active.expiresAt,
    choices: Array.isArray(existing.choices) ? existing.choices : [],
    createdAt: existing.createdAt || Date.now(),
    updatedAt: Date.now(),
  };
  await writeCartela(env, cartela);
  return json({ ok: true, cartela: publicCartela(cartela) });
}

async function cartelaState(env, url) {
  const tableId = clean(url.searchParams.get("tableId"));
  if (!tableId) {
    return json({ error: "missing_params" }, 400);
  }
  const cartela = await readCartela(env, tableId);
  if (!cartela.tableId) {
    return json({ error: "cartela_not_found" }, 404);
  }
  return json({ ok: true, cartela: publicCartela(cartela) });
}

async function chooseCartelaNumber(request, env) {
  const data = await readBody(request);
  const tableId = clean(data.tableId);
  const chooserDeviceId = clean(data.chooserDeviceId);
  const chooserName = clean(data.chooserName);
  const number = Number.parseInt(data.number, 10);
  if (!tableId || !chooserDeviceId || number < 1 || number > 100) {
    return json({ error: "invalid_choice" }, 400);
  }
  const cartela = await readCartela(env, tableId);
  if (!cartela.tableId) {
    return json({ error: "cartela_not_found" }, 404);
  }
  if (cartela.expiresAt && cartela.expiresAt <= Date.now()) {
    return json({ error: "cartela_expired" }, 410);
  }
  cartela.choices = cleanupExpiredChoices(cartela);
  const taken = cartela.choices.find((choice) => Number(choice.number) === number);
  if (taken && taken.chooserDeviceId !== chooserDeviceId) {
    return json({ error: "number_taken", cartela: publicCartela(cartela) }, 409);
  }
  for (const choice of cartela.choices) {
    if (choice.chooserDeviceId === chooserDeviceId && chooserName) {
      choice.chooserName = chooserName;
    }
  }
  if (taken) {
    taken.chooserName = chooserName || taken.chooserName || "Contato";
    taken.reserved = false;
    taken.reservationExpiresAt = 0;
    taken.removed = false;
    taken.updatedAt = Date.now();
  } else {
    cartela.choices.push({
      id: `${chooserDeviceId}:${number}`,
      chooserDeviceId,
      chooserName: chooserName || "Contato",
      number,
      confirmed: false,
      reserved: false,
      reservationExpiresAt: 0,
      removed: false,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    });
  }
  cartela.updatedAt = Date.now();
  await writeCartela(env, cartela);
  return json({ ok: true, cartela: publicCartela(cartela) });
}

async function confirmCartelaNumber(request, env) {
  const data = await readBody(request);
  const tableId = clean(data.tableId);
  const ownerDeviceId = clean(data.ownerDeviceId);
  const chooserDeviceId = clean(data.chooserDeviceId);
  const number = Number.parseInt(data.number, 10);
  if (!tableId || !ownerDeviceId || !chooserDeviceId || number < 1 || number > 100) {
    return json({ error: "invalid_confirmation" }, 400);
  }
  const cartela = await readCartela(env, tableId);
  if (!cartela.tableId) {
    return json({ error: "cartela_not_found" }, 404);
  }
  if (cartela.ownerDeviceId !== ownerDeviceId) {
    return json({ error: "owner_mismatch" }, 403);
  }
  cartela.choices = Array.isArray(cartela.choices) ? cartela.choices : [];
  const choice = cartela.choices.find((item) => item.chooserDeviceId === chooserDeviceId && Number(item.number) === number);
  if (!choice) {
    return json({ error: "choice_not_found" }, 404);
  }
  choice.confirmed = !!data.confirmed;
  if (choice.confirmed) {
    choice.reserved = false;
    choice.reservationExpiresAt = 0;
    choice.removed = false;
  }
  choice.updatedAt = Date.now();
  cartela.updatedAt = Date.now();
  await writeCartela(env, cartela);
  return json({ ok: true, cartela: publicCartela(cartela) });
}

async function renameCartelaChoice(request, env) {
  const data = await readBody(request);
  const tableId = clean(data.tableId);
  const ownerDeviceId = clean(data.ownerDeviceId);
  const chooserDeviceId = clean(data.chooserDeviceId);
  const chooserName = clean(data.chooserName);
  const number = Number.parseInt(data.number, 10);
  if (!tableId || !ownerDeviceId || !chooserDeviceId || number < 1 || number > 100 || !chooserName) {
    return json({ error: "invalid_rename" }, 400);
  }
  const cartela = await readCartela(env, tableId);
  if (!cartela.tableId) {
    return json({ error: "cartela_not_found" }, 404);
  }
  if (cartela.ownerDeviceId !== ownerDeviceId) {
    return json({ error: "owner_mismatch" }, 403);
  }
  cartela.choices = Array.isArray(cartela.choices) ? cartela.choices : [];
  const choice = cartela.choices.find((item) => item.chooserDeviceId === chooserDeviceId && Number(item.number) === number);
  if (!choice) {
    return json({ error: "choice_not_found" }, 404);
  }
  choice.chooserName = chooserName;
  choice.updatedAt = Date.now();
  cartela.updatedAt = Date.now();
  await writeCartela(env, cartela);
  return json({ ok: true, cartela: publicCartela(cartela) });
}

async function deleteCartelaChoice(request, env) {
  const data = await readBody(request);
  const tableId = clean(data.tableId);
  const ownerDeviceId = clean(data.ownerDeviceId);
  const chooserDeviceId = clean(data.chooserDeviceId);
  const number = Number.parseInt(data.number, 10);
  if (!tableId || !ownerDeviceId || !chooserDeviceId || number < 1 || number > 100) {
    return json({ error: "invalid_delete" }, 400);
  }
  const cartela = await readCartela(env, tableId);
  if (!cartela.tableId) {
    return json({ error: "cartela_not_found" }, 404);
  }
  if (cartela.ownerDeviceId !== ownerDeviceId) {
    return json({ error: "owner_mismatch" }, 403);
  }
  const permanent = !!data.permanent;
  cartela.choices = cleanupExpiredChoices(cartela);
  if (permanent) {
    cartela.choices = cartela.choices.filter((item) => !(item.chooserDeviceId === chooserDeviceId && Number(item.number) === number));
  } else {
    const choice = cartela.choices.find((item) => item.chooserDeviceId === chooserDeviceId && Number(item.number) === number);
    if (!choice) {
      return json({ error: "choice_not_found" }, 404);
    }
    choice.confirmed = false;
    choice.reserved = false;
    choice.reservationExpiresAt = 0;
    choice.removed = true;
    choice.updatedAt = Date.now();
  }
  cartela.updatedAt = Date.now();
  await writeCartela(env, cartela);
  return json({ ok: true, cartela: publicCartela(cartela) });
}

async function restoreCartelaChoice(request, env) {
  const data = await readBody(request);
  const tableId = clean(data.tableId);
  const ownerDeviceId = clean(data.ownerDeviceId);
  const chooserDeviceId = clean(data.chooserDeviceId);
  const number = Number.parseInt(data.number, 10);
  if (!tableId || !ownerDeviceId || !chooserDeviceId || number < 1 || number > 100) {
    return json({ error: "invalid_restore" }, 400);
  }
  const cartela = await readCartela(env, tableId);
  if (!cartela.tableId) {
    return json({ error: "cartela_not_found" }, 404);
  }
  if (cartela.ownerDeviceId !== ownerDeviceId) {
    return json({ error: "owner_mismatch" }, 403);
  }
  cartela.choices = cleanupExpiredChoices(cartela);
  const choice = cartela.choices.find((item) => item.chooserDeviceId === chooserDeviceId && Number(item.number) === number);
  if (!choice) {
    return json({ error: "choice_not_found" }, 404);
  }
  choice.confirmed = false;
  choice.reserved = false;
  choice.reservationExpiresAt = 0;
  choice.removed = false;
  choice.updatedAt = Date.now();
  cartela.updatedAt = Date.now();
  await writeCartela(env, cartela);
  return json({ ok: true, cartela: publicCartela(cartela) });
}

async function activeEntitlement(env, deviceId, productId) {
  const raw = await env.STORE.get(`entitlement:${deviceId}:${productId}`);
  if (!raw) {
    return { active: false, expiresAt: 0 };
  }
  const record = JSON.parse(raw);
  const active = !!record.expiresAt && record.expiresAt > Date.now();
  return { active, expiresAt: record.expiresAt || 0 };
}

async function readCartela(env, tableId) {
  const raw = await env.STORE.get(`cartela:${tableId}`);
  return raw ? JSON.parse(raw) : {};
}

async function writeCartela(env, cartela) {
  await env.STORE.put(`cartela:${cartela.tableId}`, JSON.stringify(cartela));
}

function publicCartela(cartela) {
  const choices = cleanupExpiredChoices(cartela);
  return {
    tableId: cartela.tableId || "",
    productId: cartela.productId || "cartela_de_eventos",
    ownerDeviceId: cartela.ownerDeviceId || "",
    ownerName: cartela.ownerName || "",
    title: cartela.title || "",
    ownerMessage: cartela.ownerMessage || "",
    copyText: cartela.copyText || "",
    ownerContact: cartela.ownerContact || "",
    allowReservations: !!cartela.allowReservations,
    reservationHours: clampInt(cartela.reservationHours, 24, 1, 168),
    expiresAt: cartela.expiresAt || 0,
    choices: choices.map((choice) => ({
      chooserDeviceId: choice.chooserDeviceId || "",
      chooserName: choice.chooserName || "Contato",
      number: Number(choice.number) || 0,
      confirmed: !!choice.confirmed,
      reserved: !!choice.reserved && !choice.confirmed && !choice.removed,
      reservationExpiresAt: choice.confirmed || choice.removed ? 0 : Number(choice.reservationExpiresAt) || 0,
      removed: !!choice.removed,
      updatedAt: choice.updatedAt || choice.createdAt || 0,
    })),
    updatedAt: cartela.updatedAt || 0,
  };
}

function cleanupExpiredChoices(cartela) {
  const now = Date.now();
  return (Array.isArray(cartela.choices) ? cartela.choices : []).filter((choice) => {
    if (!choice || choice.confirmed || !choice.reserved) {
      return true;
    }
    const expiresAt = Number(choice.reservationExpiresAt) || 0;
    return !expiresAt || expiresAt > now;
  });
}

async function checkoutPage(url, env) {
  const productId = clean(url.searchParams.get("productId") || "cartela_de_eventos");
  const deviceId = clean(url.searchParams.get("deviceId"));
  const product = await getProduct(env, productId) || (await getProduct(env, "cartela_de_eventos"));
  const cartelas = normalizeCartelaOrder(url.searchParams.get("cartelas"), product);
  const totalPrice = cartelas.reduce((sum, item) => sum + item.price, 0);
  const cartelaSummary = cartelas.map((item) =>
    `<div class="line"><div class="line-head"><span>Cartela ${item.index}</span><span>R$ ${item.price.toFixed(2).replace(".", ",")}</span></div><p>${item.days} dia${item.days === 1 ? "" : "s"}</p></div>`
  ).join("");
  if (!deviceId) {
    return html("<h1>nBTChat Loja</h1><p>Abra esta tela pelo app para comprar.</p>");
  }
  return html(`<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escapeHtml(product.title)} - nBTChat</title>
  <style>
    :root { color-scheme: light dark; font-family: Arial, sans-serif; }
    body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #f4f7f5; color: #17212b; }
    main { width: min(92vw, 430px); background: white; border: 1px solid #d7ddd8; border-radius: 18px; padding: 24px; box-shadow: 0 16px 50px #0002; }
    .badge { width: 68px; height: 68px; border-radius: 20px; display: grid; place-items: center; background: #ddf7e8; color: #16a34a; font-size: 34px; margin-bottom: 14px; }
    h1 { margin: 0; font-size: 28px; }
    p { line-height: 1.45; color: #52606d; }
    label { display: block; font-size: 13px; font-weight: 700; margin-top: 14px; }
    input { width: 100%; box-sizing: border-box; border: 1px solid #cbd5cf; border-radius: 12px; padding: 13px; font-size: 16px; margin-top: 6px; }
    .line { border: 1px solid #d7ddd8; border-radius: 14px; padding: 12px; margin-top: 10px; background: #f8faf9; }
    .line-head { display: flex; justify-content: space-between; gap: 12px; align-items: center; font-weight: 800; }
    .total { display: flex; justify-content: space-between; align-items: center; margin-top: 16px; padding-top: 14px; border-top: 1px solid #d7ddd8; font-weight: 900; }
    button { width: 100%; border: 0; border-radius: 14px; padding: 14px; margin-top: 18px; background: #16a34a; color: white; font-weight: 800; font-size: 16px; }
    .price { margin: 14px 0 8px; }
    .money { color: #16a34a; font-weight: 900; font-size: 24px; }
    .days { color: #38bdf8; font-weight: 800; font-size: 17px; margin-left: 6px; }
    .footer { font-size: 12px; color: #64748b; }
    @media (prefers-color-scheme: dark) {
      body { background: #101820; color: #f7f8f5; }
      main { background: #18232c; border-color: #2f3b45; }
      input { background: #24313b; border-color: #2f3b45; color: #f7f8f5; }
      .line { background: #1d2933; border-color: #2f3b45; }
    }
  </style>
</head>
<body>
  <main>
    <div class="badge">#</div>
    <h1>${escapeHtml(product.title)}</h1>
    <p>100 numeros interativos para enviar em conversas do nBTChat.</p>
    <p class="price"><span class="money">R$ ${product.price.toFixed(2).replace(".", ",")}</span><span class="days">${product.durationDays} dia</span></p>
    <form method="post" action="/create-payment">
      <input type="hidden" name="productId" value="${escapeHtml(product.id)}">
      <input type="hidden" name="deviceId" value="${escapeHtml(deviceId)}">
      <input type="hidden" name="cartelas" value="${escapeHtml(JSON.stringify(cartelas.map((item) => ({ days: item.days }))))}">
      <label>Cartelas</label>
      ${cartelaSummary}
      <div class="total"><span>Total</span><span>R$ ${totalPrice.toFixed(2).replace(".", ",")}</span></div>
      <label>Nome</label>
      <input name="buyerName" autocomplete="name" required minlength="3" maxlength="80">
      <label>CPF</label>
      <input name="buyerCpf" inputmode="numeric" autocomplete="off" required minlength="11" maxlength="14">
      <button type="submit">Continuar pagamento</button>
    </form>
    <p class="footer">${escapeHtml(product.footer)}</p>
  </main>
</body>
</html>`);
}

function recoveryCodePage(product, recoveryCode, checkoutUrl) {
  const safeCode = escapeHtml(recoveryCode);
  const note = `nBTChat - ${product.title}\nCodigo de recuperacao: ${recoveryCode}\nGuarde este codigo junto com o CPF usado na compra.`;
  const nextAction = checkoutUrl && checkoutUrl !== "#"
      ? `<a class="button" href="${escapeHtml(checkoutUrl)}">Continuar para o Mercado Pago</a>`
      : `<p class="warning">Volte ao nBTChat e toque em verificar para liberar a Cartela de eventos.</p>`;
  return html(`<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Codigo de recuperacao - nBTChat</title>
  <style>
    :root { color-scheme: light dark; font-family: Arial, sans-serif; }
    body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #f4f7f5; color: #17212b; }
    main { width: min(92vw, 460px); background: white; border: 1px solid #d7ddd8; border-radius: 18px; padding: 24px; box-shadow: 0 16px 50px #0002; }
    h1 { margin: 0; font-size: 27px; }
    p { line-height: 1.45; color: #52606d; }
    .code { margin: 18px 0; padding: 18px; text-align: center; border-radius: 16px; border: 1px solid #16a34a; background: #ddf7e8; color: #14532d; font-size: 24px; font-weight: 900; letter-spacing: 1px; user-select: all; }
    button, a.button { display: block; width: 100%; box-sizing: border-box; border: 0; border-radius: 14px; padding: 14px; margin-top: 10px; background: #16a34a; color: white; font-weight: 800; font-size: 16px; text-align: center; text-decoration: none; }
    button.secondary { background: #eef2f0; color: #17212b; }
    .warning { font-size: 13px; color: #64748b; }
    @media (prefers-color-scheme: dark) {
      body { background: #101820; color: #f7f8f5; }
      main { background: #18232c; border-color: #2f3b45; }
      .code { background: #123328; color: #bbf7d0; }
      button.secondary { background: #24313b; color: #f7f8f5; }
    }
  </style>
</head>
<body>
  <main>
    <h1>Guarde seu codigo</h1>
    <p>Este codigo recupera a compra se o app for reinstalado ou o aparelho perder os dados. Guarde em local seguro, como gerenciador de senhas, nuvem pessoal, bloco de notas privado ou tire um print.</p>
    <div class="code" id="code">${safeCode}</div>
    <button type="button" onclick="copyCode()">Copiar codigo</button>
    <button type="button" class="secondary" onclick="downloadCode()">Baixar TXT</button>
    <button type="button" class="secondary" onclick="window.print()">Imprimir ou salvar PDF</button>
    ${nextAction}
    <p class="warning">Para recuperar depois, sera necessario informar este codigo e o CPF usado na compra.</p>
  </main>
  <script>
    const recoveryText = ${JSON.stringify(note)};
    function copyCode() {
      navigator.clipboard?.writeText(document.getElementById("code").textContent.trim());
    }
    function downloadCode() {
      const blob = new Blob([recoveryText], { type: "text/plain;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "nbtchat-codigo-recuperacao.txt";
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
  </script>
</body>
</html>`);
}

async function recoverPage(url, env) {
  const productId = clean(url.searchParams.get("productId") || "cartela_de_eventos");
  const deviceId = clean(url.searchParams.get("deviceId"));
  const product = await getProduct(env, productId) || (await getProduct(env, "cartela_de_eventos"));
  if (!deviceId) {
    return html("<h1>nBTChat Loja</h1><p>Abra esta tela pelo app para recuperar.</p>");
  }
  return html(`<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Recuperar ${escapeHtml(product.title)} - nBTChat</title>
  <style>
    :root { color-scheme: light dark; font-family: Arial, sans-serif; }
    body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #f4f7f5; color: #17212b; }
    main { width: min(92vw, 430px); background: white; border: 1px solid #d7ddd8; border-radius: 18px; padding: 24px; box-shadow: 0 16px 50px #0002; }
    .badge { width: 68px; height: 68px; border-radius: 20px; display: grid; place-items: center; background: #ddf7e8; color: #16a34a; font-size: 34px; margin-bottom: 14px; }
    h1 { margin: 0; font-size: 28px; }
    p { line-height: 1.45; color: #52606d; }
    label { display: block; font-size: 13px; font-weight: 700; margin-top: 14px; }
    input { width: 100%; box-sizing: border-box; border: 1px solid #cbd5cf; border-radius: 12px; padding: 13px; font-size: 16px; margin-top: 6px; }
    button { width: 100%; border: 0; border-radius: 14px; padding: 14px; margin-top: 18px; background: #16a34a; color: white; font-weight: 800; font-size: 16px; }
    .footer { font-size: 12px; color: #64748b; }
    @media (prefers-color-scheme: dark) {
      body { background: #101820; color: #f7f8f5; }
      main { background: #18232c; border-color: #2f3b45; }
      input { background: #24313b; border-color: #2f3b45; color: #f7f8f5; }
    }
  </style>
</head>
<body>
  <main>
    <div class="badge">#</div>
    <h1>Recuperar compra</h1>
    <p>Informe o CPF usado na compra e o codigo de recuperacao nBTChat.</p>
    <form method="post" action="/recover-purchase">
      <input type="hidden" name="productId" value="${escapeHtml(product.id)}">
      <input type="hidden" name="deviceId" value="${escapeHtml(deviceId)}">
      <label>CPF</label>
      <input name="buyerCpf" inputmode="numeric" autocomplete="off" required minlength="11" maxlength="14">
      <label>Codigo de recuperacao nBTChat</label>
      <input name="recoveryCode" autocomplete="off" required minlength="8" maxlength="24" placeholder="NBT-ABCD-1234">
      <button type="submit">Recuperar ${escapeHtml(product.title)}</button>
    </form>
    <p class="footer">Esse codigo foi mostrado antes de continuar para o Mercado Pago.</p>
  </main>
</body>
</html>`);
}

function resultHtml(title, body, ok, status = 200) {
  const color = ok ? "#16a34a" : "#dc2626";
  return html(`<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1"><style>body{font-family:Arial,sans-serif;padding:32px;max-width:520px;margin:auto;line-height:1.45}h1{color:${color}}</style><h1>${escapeHtml(title)}</h1><p>${escapeHtml(body)}</p>`, status);
}

async function resultPage(path, env, url) {
  if (path === "/success") {
    const paymentId = paymentIdFromUrl(url);
    if (paymentId) {
      const payment = await fetchPayment(env, paymentId);
      if (payment && payment.status === "approved") {
        const activation = await activateApprovedPayment(env, payment, paymentId);
        if (activation.activated && activation.recoveryCode) {
          return recoveryCodePage(activation.product, activation.recoveryCode, "#");
        }
      }
    }
  }
  const title = path === "/success" ? "Pagamento aprovado" : path === "/pending" ? "Pagamento pendente" : "Pagamento nao concluido";
  const body = path === "/success"
    ? "Volte ao nBTChat para liberar sua Cartela de eventos. Se voce nao salvou o codigo de recuperacao antes do pagamento, procure o comprovante e entre em contato com o dono da loja."
    : "Voce pode voltar ao nBTChat e verificar novamente daqui a pouco.";
  return html(`<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1"><style>body{font-family:Arial,sans-serif;padding:32px;max-width:520px;margin:auto;line-height:1.45}</style><h1>${title}</h1><p>${body}</p>`);
}

async function fetchPayment(env, paymentId) {
  const mp = await fetch(`https://api.mercadopago.com/v1/payments/${paymentId}`, {
    headers: { Authorization: `Bearer ${env.MP_ACCESS_TOKEN}` },
  });
  return mp.ok ? mp.json() : null;
}

function paymentIdFromUrl(url) {
  return onlyDigits(url.searchParams.get("payment_id")
      || url.searchParams.get("collection_id")
      || url.searchParams.get("id")
      || "");
}

function preferenceCheckoutUrl(body, env) {
  const testToken = clean(env.MP_ACCESS_TOKEN).startsWith("TEST-");
  return testToken
    ? body.sandbox_init_point || body.init_point
    : body.init_point || body.sandbox_init_point;
}

async function readBody(request) {
  const type = request.headers.get("content-type") || "";
  if (type.includes("application/json")) {
    return request.json();
  }
  const form = await request.formData();
  return Object.fromEntries(form.entries());
}

function isFormRequest(request) {
  return (request.headers.get("content-type") || "").includes("application/x-www-form-urlencoded")
    || (request.headers.get("content-type") || "").includes("multipart/form-data");
}

function json(value, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...extraHeaders },
  });
}

function html(value, status = 200, extraHeaders = {}) {
  return new Response(value, {
    status,
    headers: { "content-type": "text/html; charset=utf-8", ...extraHeaders },
  });
}

function clean(value) {
  return String(value || "").trim();
}

function clampInt(value, fallback, min, max) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return Math.max(min, Math.min(max, parsed));
}

function decimalNumber(value) {
  const parsed = Number.parseFloat(String(value || "").replace(",", "."));
  return Number.isFinite(parsed) ? parsed : 0;
}

function onlyDigits(value) {
  return clean(value).replace(/\D+/g, "");
}

function generateRecoveryCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = new Uint8Array(12);
  crypto.getRandomValues(bytes);
  let raw = "";
  for (const byte of bytes) {
    raw += alphabet[byte % alphabet.length];
  }
  return `NBT-${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8, 12)}`;
}

function normalizeRecoveryCode(value) {
  const raw = clean(value).toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (!raw) {
    return "";
  }
  return raw.startsWith("NBT") ? raw : `NBT${raw}`;
}

async function sha256(value) {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function escapeHtml(value) {
  return clean(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
