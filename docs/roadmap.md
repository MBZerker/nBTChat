# nBTChat Roadmap

## Cartela de eventos sincronizada pela internet

Objetivo: manter cartelas compartilhadas atualizadas mesmo quando os aparelhos nao estao proximos por Bluetooth.

Status em 0.4.0: primeira versao implementada com Worker/KV e app Android. A cartela agora registra estado online por `tableId`, bloqueia numero duplicado no servidor, sincroniza escolhas ao abrir e permite confirmacao do dono pela internet. Proximos refinamentos naturais: fila local para reenviar escolhas quando a internet falhar, tela web administrativa do dono e historico/auditoria das confirmacoes.

Fluxo desejado:

- A compra/cria a Cartela de eventos e vira dona da cartela.
- A compartilha a cartela com B pelo nBTChat.
- B pode compartilhar a mesma cartela com C.
- Se C estiver longe de A, C ainda consegue escolher um numero usando a internet.
- O servidor registra a escolha ligada ao `tableId` da cartela.
- A, dona da cartela, recebe/consulta essas escolhas pelo site/app e pode confirmar ou remover confirmacao.
- B e C devem ver os numeros ja escolhidos/travados quando abrirem a cartela novamente.
- A tabela compartilhada deve preservar o `tableId`, os numeros bloqueados e os status pendente/confirmado.

Requisitos importantes:

- Numeros ja escolhidos precisam aparecer com X/cinza para qualquer pessoa que abrir a mesma cartela.
- O mesmo numero nao pode ser escolhido duas vezes.
- O app deve continuar funcionando localmente por Bluetooth, mas usar internet como camada de sincronizacao quando disponivel.
- Dados sensiveis devem ficar no Worker/KV e nunca no APK ou no repositorio.
