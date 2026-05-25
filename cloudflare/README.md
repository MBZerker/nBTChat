# nBTChat Store Worker

Worker publico usado pela loja do nBTChat.

## Bindings esperados

- `MP_ACCESS_TOKEN`: secret do Mercado Pago. Use teste enquanto estiver no ambiente de teste e producao quando publicar de verdade.
- `STORE`: binding do Workers KV apontando para o namespace `NBTCHAT_STORE`.
- `NBTCHAT_ADMIN_KEY` ou `ADMIN_API_KEY` ou `API_KEY`: chave para entrar na area ADM em `/admin`.

## Rotas

- `GET /checkout?productId=cartela_de_eventos&deviceId=...`: pagina de compra aberta pelo app.
- `POST /create-payment`: cria a preferencia do Mercado Pago a partir do formulario.
- `POST /webhook/mercadopago`: recebe notificacoes de pagamento aprovado.
- `GET /entitlement?deviceId=...&productId=cartela_de_eventos`: consulta se a Cartela de eventos esta ativa.
- `GET /recover?productId=cartela_de_eventos&deviceId=...`: pagina para recuperar uma compra usando CPF e codigo de recuperacao nBTChat.
- `POST /cartela/register`: registra/atualiza uma Cartela de eventos comprada.
- `GET /cartela/state?tableId=...`: consulta numeros escolhidos/confirmados da cartela.
- `POST /cartela/choose`: marca um numero como escolhido, impedindo duplicidade.
- `POST /cartela/confirm`: dono confirma ou remove confirmacao de uma escolha.
- `POST /cartela/delete-choice`: dono remove um participante e libera o numero.
- `GET /admin`: area ADM para configurar itens oficiais da loja.
- `POST /admin/login` e `POST /admin/products`: login e salvamento da area ADM.
- `POST /shorten`: recebe `{ "url": "https://mbzerker.github.io/nBTChat/..." }` ou link do CompraLink e devolve `{ shortUrl }`.
- `GET /s/:codigo`: abre o link encurtado.
- `POST /share-link`: recebe um payload de compartilhamento do nBTChat e devolve um link curto `/s/:codigo` sem colocar o payload na URL.
- `GET /share/:codigo`: usado pelo app para recuperar o payload do item compartilhado.

O app nao precisa carregar chave secreta. Nome e CPF ficam no fluxo do Worker/Mercado Pago; o app envia apenas o `deviceId` pseudonimo e consulta a liberacao. O codigo de recuperacao e mostrado antes do pagamento e deve ser guardado pelo comprador.

O encurtador aceita apenas destinos conhecidos dos projetos `nBTChat` e `CompraLink` no GitHub Pages, alem das paginas internas de compra/recuperacao do proprio Worker. Isso evita que o dominio seja usado como encurtador aberto para links de terceiros.
