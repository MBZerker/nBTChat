# nBTChat

Aplicativo Android de chat local via Bluetooth, sem servidor e sem internet.

## Estado atual

- Perfil local com nome, recado, sexo e foto opcional.
- Foto de perfil pela galeria ou camera.
- Avatares padrao por sexo quando a pessoa nao escolhe foto.
- Tela principal em formato de lista de contatos/conversas.
- Busca de aparelhos pareados que anunciam o servico Bluetooth do nBTChat.
- Tela de encontrar aparelhos proximos para contas novas ou convite de novos contatos.
- Ao entrar na tela de encontrar aparelhos, o Android e solicitado a deixar o celular visivel por ate 2 minutos.
- Conexao Bluetooth Classic RFCOMM entre dois aparelhos com o app aberto.
- Handshake de perfil e chave ECDH P-256.
- Mensagens criptografadas com AES-GCM depois do handshake.
- Historico local de conversas e contador de mensagens nao lidas.
- Chat com envio de texto, links reconhecidos, seletor de emojis, fotos e mensagens de voz.
- Links confiaveis abrem direto; links desconhecidos exibem alerta antes de abrir.
- Imagens recebidas abrem em tela cheia pelo toque.
- Mensagens de voz com play/pause, barra de progresso, tempo e reproducao automatica da proxima voz.
- Recibos de envio, entrega e leitura.
- Ponte Bluetooth privada por pacote selado para encaminhar mensagens entre aparelhos conhecidos sem expor o conteudo ao intermediario.
- Compartilhamento interno de mensagens entre contatos do nBTChat.
- Resposta a mensagens por menu de selecao ou arraste lateral do balao.
- Remocao de mensagem so para mim ou para todos.
- Notificacoes de mensagens recebidas com o app fechado, usando servico em primeiro plano.
- Notificacao de segundo plano reduzida para canal silencioso/minimo; o Android ainda pode exibir indicador do servico.
- Menu com edicao de perfil, configuracoes, compartilhamento do app, alternancia claro/escuro e apagamento de conversa no chat.
- Configuracao para ativar/desativar notificacoes de mensagens.
- Verificacao de atualizacao via `docs/update.json`.
- Pagina de download em `docs/` para GitHub Pages.

## Build

O projeto usa Android Gradle Plugin 8.7.3, `compileSdk 35`, Java 17 e nao depende de bibliotecas externas de UI.

APK debug gerado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

APK para link publico:

```text
docs/nBTChat.apk
```

Pagina GitHub Pages esperada, depois de ativar Pages em `Settings > Pages > Deploy from a branch > main /docs`:

```text
https://mbzerker.github.io/nBTChat/
```

## Observacoes

Para contatos ja conhecidos, a tela principal tenta manter a lista limpa com aparelhos pareados/conhecidos. Para conta nova ou convite, a tela de scanner lista aparelhos proximos detectaveis e tenta conectar usando o servico Bluetooth do nBTChat; o outro aparelho precisa estar com o app aberto/escutando para aceitar a conexao. O perfil/foto de outra pessoa aparece depois da primeira conexao bem-sucedida.
