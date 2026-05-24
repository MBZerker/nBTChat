const PRODUCTS = {
  cartela_de_eventos: {
    id: "cartela_de_eventos",
    title: "Cartela de eventos",
    price: 4.99,
    durationDays: 15,
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
      if (request.method === "GET" && url.pathname === "/recover") {
        return recoverPage(url);
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
    return recoveryCodePage(product, recoveryCode, preferenceCheckoutUrl(body, env));
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

  const activation = await activateApprovedPayment(env, payment, paymentId);
  return json({ ok: true, activated: activation.activated || false, skipped: activation.skipped || "" });
}

async function activateApprovedPayment(env, payment, paymentId) {
  const externalReference = clean(payment.external_reference);
  const productId = externalReference.split(":")[1] || "";
  const product = PRODUCTS[productId];
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

  const expiresAt = Date.now() + product.durationDays * 24 * 60 * 60 * 1000;
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
    updatedAt: Date.now(),
  }));
}

async function recoverPurchase(request, env) {
  const data = await readBody(request);
  const product = PRODUCTS[data.productId || "cartela_de_eventos"];
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
    title: record.title || PRODUCTS[productId]?.title || productId,
    expiresAt: record.expiresAt,
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
    ownerDeviceId,
    ownerName: clean(data.ownerName),
    ownerMessage: clean(data.ownerMessage),
    copyText: clean(data.copyText),
    ownerContact: clean(data.ownerContact),
    allowReservations: !!data.allowReservations,
    reservationHours: clampInt(data.reservationHours, 24, 1, 168),
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
  if (taken) {
    taken.chooserName = chooserName || taken.chooserName || "Contato";
    taken.reserved = !!data.reserved && !taken.confirmed && !!cartela.allowReservations;
    taken.reservationExpiresAt = taken.reserved ? Date.now() + clampInt(cartela.reservationHours, 24, 1, 168) * 60 * 60 * 1000 : 0;
    taken.updatedAt = Date.now();
  } else {
    const reserved = !!data.reserved;
    if (reserved && !cartela.allowReservations) {
      return json({ error: "reservations_disabled", cartela: publicCartela(cartela) }, 403);
    }
    cartela.choices.push({
      id: `${chooserDeviceId}:${number}`,
      chooserDeviceId,
      chooserName: chooserName || "Contato",
      number,
      confirmed: false,
      reserved,
      reservationExpiresAt: reserved ? Date.now() + clampInt(cartela.reservationHours, 24, 1, 168) * 60 * 60 * 1000 : 0,
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
  }
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
  cartela.choices = cleanupExpiredChoices(cartela).filter((item) => !(item.chooserDeviceId === chooserDeviceId && Number(item.number) === number));
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
      reserved: !!choice.reserved && !choice.confirmed,
      reservationExpiresAt: choice.confirmed ? 0 : Number(choice.reservationExpiresAt) || 0,
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
    .price { margin: 14px 0 8px; }
    .money { color: #16a34a; font-weight: 900; font-size: 24px; }
    .days { color: #38bdf8; font-weight: 800; font-size: 17px; margin-left: 6px; }
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
    <p class="price"><span class="money">R$ ${product.price.toFixed(2).replace(".", ",")}</span><span class="days">${product.durationDays} dias</span></p>
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

function recoverPage(url) {
  const productId = clean(url.searchParams.get("productId") || "cartela_de_eventos");
  const deviceId = clean(url.searchParams.get("deviceId"));
  const product = PRODUCTS[productId] || PRODUCTS.cartela_de_eventos;
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

function resultHtml(title, body, ok) {
  const color = ok ? "#16a34a" : "#dc2626";
  return html(`<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1"><style>body{font-family:Arial,sans-serif;padding:32px;max-width:520px;margin:auto;line-height:1.45}h1{color:${color}}</style><h1>${escapeHtml(title)}</h1><p>${escapeHtml(body)}</p>`);
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

function clampInt(value, fallback, min, max) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return Math.max(min, Math.min(max, parsed));
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
