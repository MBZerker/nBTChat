const PRODUCTS = {
  cartela_de_eventos: {
    id: "cartela_de_eventos",
    title: "Cartela de eventos",
    price: 4.99,
    durationDays: 7,
    footer: "Produto destinado exclusivamente para organizacao de eventos familiares, recreativos e chas beneficentes.",
  },
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (request.method === "GET" && url.pathname === "/") {
        return json({ ok: true, products: PRODUCTS });
      }
      if (request.method === "GET" && url.pathname === "/checkout") {
        return checkoutPage(url);
      }
      if (request.method === "POST" && url.pathname === "/create-payment") {
        return createPayment(request, env, url);
      }
      if (request.method === "POST" && url.pathname === "/webhook/mercadopago") {
        return mercadoPagoWebhook(request, env, url);
      }
      if (request.method === "GET" && url.pathname === "/entitlement") {
        return entitlement(env, url);
      }
      if (request.method === "GET" && ["/success", "/pending", "/failure"].includes(url.pathname)) {
        return resultPage(url.pathname);
      }
      return json({ error: "not_found" }, 404);
    } catch (error) {
      return json({ error: error.message || "server_error" }, 500);
    }
  },
};

async function createPayment(request, env, url) {
  const data = await readBody(request);
  const product = PRODUCTS[data.productId || "cartela_de_eventos"];
  if (!product) {
    return json({ error: "invalid_product" }, 400);
  }

  const deviceId = clean(data.deviceId);
  const buyerName = clean(data.buyerName);
  const buyerCpf = onlyDigits(data.buyerCpf);
  if (!deviceId || buyerName.length < 3 || buyerCpf.length !== 11) {
    return json({ error: "invalid_buyer_data" }, 400);
  }

  const externalReference = `nbtchat:${product.id}:${deviceId}:${Date.now()}`;
  await env.STORE.put(`pending:${externalReference}`, JSON.stringify({
    productId: product.id,
    deviceId,
    buyerName,
    cpfHash: await sha256(buyerCpf),
    cpfLast4: buyerCpf.slice(-4),
    createdAt: Date.now(),
  }), { expirationTtl: 2 * 24 * 60 * 60 });

  const preference = {
    items: [{
      id: product.id,
      title: product.title,
      quantity: 1,
      currency_id: "BRL",
      unit_price: product.price,
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
    return Response.redirect(body.init_point || body.sandbox_init_point, 303);
  }
  return json({
    preferenceId: body.id,
    initPoint: body.init_point,
    sandboxInitPoint: body.sandbox_init_point,
    externalReference,
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

  const pendingRaw = await env.STORE.get(`pending:${payment.external_reference}`);
  if (!pendingRaw) {
    return json({ ok: true, skipped: "pending_not_found" });
  }
  const pending = JSON.parse(pendingRaw);
  const product = PRODUCTS[pending.productId];
  if (!product) {
    return json({ ok: true, skipped: "product_not_found" });
  }

  const expiresAt = Date.now() + product.durationDays * 24 * 60 * 60 * 1000;
  await env.STORE.put(`entitlement:${pending.deviceId}:${product.id}`, JSON.stringify({
    active: true,
    productId: product.id,
    title: product.title,
    expiresAt,
    paymentId: String(paymentId),
    externalReference: payment.external_reference,
    buyerName: pending.buyerName,
    cpfHash: pending.cpfHash,
    cpfLast4: pending.cpfLast4,
    updatedAt: Date.now(),
  }));
  await env.STORE.delete(`pending:${payment.external_reference}`);
  return json({ ok: true });
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
    title: record.title || PRODUCTS[productId]?.title || productId,
    expiresAt: record.expiresAt,
  });
}

function checkoutPage(url) {
  const productId = clean(url.searchParams.get("productId") || "cartela_de_eventos");
  const deviceId = clean(url.searchParams.get("deviceId"));
  const product = PRODUCTS[productId] || PRODUCTS.cartela_de_eventos;
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
    button { width: 100%; border: 0; border-radius: 14px; padding: 14px; margin-top: 18px; background: #16a34a; color: white; font-weight: 800; font-size: 16px; }
    .price { color: #16a34a; font-weight: 800; font-size: 20px; }
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
    <h1>${escapeHtml(product.title)}</h1>
    <p>100 numeros interativos para enviar em conversas do nBTChat.</p>
    <p class="price">R$ ${product.price.toFixed(2).replace(".", ",")} por ${product.durationDays} dias</p>
    <form method="post" action="/create-payment">
      <input type="hidden" name="productId" value="${escapeHtml(product.id)}">
      <input type="hidden" name="deviceId" value="${escapeHtml(deviceId)}">
      <label>Nome</label>
      <input name="buyerName" autocomplete="name" required minlength="3" maxlength="80">
      <label>CPF</label>
      <input name="buyerCpf" inputmode="numeric" autocomplete="off" required minlength="11" maxlength="14">
      <button type="submit">Pagar com Mercado Pago</button>
    </form>
    <p class="footer">${escapeHtml(product.footer)}</p>
  </main>
</body>
</html>`);
}

function resultPage(path) {
  const title = path === "/success" ? "Pagamento aprovado" : path === "/pending" ? "Pagamento pendente" : "Pagamento nao concluido";
  const body = path === "/success"
    ? "Volte ao nBTChat para liberar sua Cartela de eventos."
    : "Voce pode voltar ao nBTChat e verificar novamente daqui a pouco.";
  return html(`<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1"><style>body{font-family:Arial,sans-serif;padding:32px;max-width:520px;margin:auto;line-height:1.45}</style><h1>${title}</h1><p>${body}</p>`);
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

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

function html(value, status = 200) {
  return new Response(value, {
    status,
    headers: { "content-type": "text/html; charset=utf-8" },
  });
}

function clean(value) {
  return String(value || "").trim();
}

function onlyDigits(value) {
  return clean(value).replace(/\D+/g, "");
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
