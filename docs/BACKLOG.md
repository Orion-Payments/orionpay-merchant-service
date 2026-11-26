# Backlog: ajustes da análise técnica (2026-11-25)

Prioridades: **P0** = bloqueante (risco financeiro/segurança) · **P1** = alto · **P2** = médio · **P3** = melhoria.
Tamanho: **P** (≤ 0,5 dia) · **M** (1–2 dias) · **G** (3+ dias).

Cada item segue a mesma estrutura: **Problema → O que fazer → Critérios de aceite**. Escreva o teste que reproduz o problema antes de corrigir.

---

## Épico 1: Autorização por lojista (IDOR)

Regra geral: o `merchantId` vem **sempre do token JWT** (`SecurityContextService.getCurrentMerchantId()`), nunca do corpo, da URL ou de um header.

### SEC-01 · Saque usa `merchantId` do corpo · P0 · P
- **Problema:** `WithdrawController.java:36` lê o `merchantId` do token e o ignora. `WithdrawMoneyUseCase` usa `request.merchantId()`, então um lojista consegue sacar o saldo de outro.
- **O que fazer:** remover `merchantId` de `WithdrawRequest`, mudar a assinatura para `execute(UUID merchantId, WithdrawRequest, String idempotencyKey)` e passar o id do token. Adicionar `@Valid` no `@RequestBody`.
- **Critérios de aceite:**
  - [ ] Teste de controller: lojista A autenticado envia payload com o id de B → o débito ocorre na conta de A (ou 4xx), nunca na de B.
  - [ ] `WithdrawRequest` sem o campo `merchantId`; a collection do Postman foi atualizada.

### SEC-02 · Autorização de transação usa `merchantId` do corpo · P0 · M
- **Problema:** `POST /transactions/authorize` aceita qualquer `merchantId`.
- **O que fazer:** definir quem chama este endpoint: o lojista (usar o id do token) ou o terminal/POS. No caso do terminal, validar que o `terminalSn` pertence ao lojista e criar uma role/credencial própria. Remover `merchantId` do `TransactionRequest` ou validar que ele é igual ao do token.
- **Critérios de aceite:**
  - [ ] Teste: token de A com `merchantId` de B → 403.
  - [ ] Teste: `terminalSn` de outro lojista → 403.

### SEC-03 · Endpoints com `{id}` do lojista na URL · P0 · M
- **Problema:** não há verificação de dono em:
  - `GET  /merchants/{id}/anticipation/available`
  - `GET  /merchants/{id}/anticipation/pricing`
  - `POST /merchants/{id}/anticipation/execute`
  - `GET  /merchants/{id}/agenda`
  - `POST /dashboard/{merchantId}/rebuild-summary`
- **O que fazer:** criar um helper único, por exemplo `SecurityContextService.assertOwnership(UUID)` ou um `@PreAuthorize("@ownership.check(#id)")`, e aplicar em todos esses endpoints. Restringir `rebuild-summary` a `ROLE_ADMIN`. Unificar a regra que hoje está duplicada à mão no `DashboardController.getSummary`.
- **Critérios de aceite:**
  - [ ] Um teste de 403 por endpoint (token de A, URL com o id de B).
  - [ ] `ROLE_ADMIN` continua acessando qualquer lojista, se essa for a regra de negócio (confirmar com o PO).

### SEC-04 · Detalhe de liquidação sem verificação de dono · P0 · P
- **Problema:** `GET /merchants/agenda/{settlementId}/detail` devolve o detalhe de qualquer liquidação.
- **O que fazer:** filtrar `findDetailById` também pelo `merchantId` do token e devolver 404 quando a liquidação não pertence a ele.
- **Critérios de aceite:**
  - [ ] Teste: `settlementId` de B acessado com o token de A → 404.

### SEC-05 · Envio de comprovante usa o header `X-Merchant-Id` · P0 · P
- **Problema:** `TransactionEmailController` confia em um header que o cliente controla.
- **O que fazer:** remover o header e usar o id do token. Validar o e-mail com `@Email` e `@Valid`. Avaliar rate limit para o endpoint não virar disparador de spam.
- **Critérios de aceite:**
  - [ ] Teste: transação de B com o token de A → 404/403.
  - [ ] Requisição sem header funciona normalmente.

---

## Épico 2: Consistência financeira

### FIN-01 · Os `@Transactional` do saque não têm efeito (chamada interna) · P0 · M
- **Problema:** `reserveFunds`, `confirmPayout` e `failPayout` são chamados via `this.`, então o proxy do Spring é ignorado e cada repositório faz commit sozinho. Se o débito passar e o `save` do payout falhar, o saldo é perdido.
- **O que fazer:** extrair esses métodos para um bean separado (ex.: `PayoutLedgerService`), mantendo a chamada externa ao PIX **fora** da transação. Remover o `@Transactional` do `LedgerRepositoryAdapter.deductBalanceAtomic`/`saveEntry` para que eles participem da transação de quem chama (usar `Propagation.MANDATORY` onde fizer sentido).
- **Critérios de aceite:**
  - [ ] Teste de integração (Testcontainers): forçar falha no `payoutRepository.save` → o saldo **não** muda e não sobra `ledger_entry` órfã.
  - [ ] O comentário "Rollback automático" passa a ser verdadeiro.

### FIN-02 · O estorno de saque com falha não é atômico · P0 · P
- **Problema:** `failPayout` faz `account.credit()` + `saveAccount()` (ler-alterar-gravar), o que gera race condition.
- **O que fazer:** criar `creditBalanceAtomic(merchantId, amount)` (um `UPDATE … SET balance = balance + :amount`), simétrico ao débito. Garantir que um mesmo payout não seja estornado duas vezes, seja por constraint ou pela checagem de status dentro da mesma transação.
- **Critérios de aceite:**
  - [ ] Teste concorrente: N estornos em paralelo para payouts diferentes → saldo final correto.
  - [ ] Chamar `failPayout` duas vezes no mesmo payout → crédito aplicado uma única vez.

### FIN-03 · Transações negadas não são gravadas · P1 · P
- **Problema:** em `AuthorizeTransactionUseCase`, o `save()` da transação negada sofre rollback porque logo depois uma `DomainException` é lançada dentro do `@Transactional`.
- **O que fazer:** usar `@Transactional(dontRollbackOn = DomainException.class)` ou gravar a negada em `REQUIRES_NEW`. Trocar para `org.springframework.transaction.annotation.Transactional` (ver ARQ-03).
- **Critérios de aceite:**
  - [ ] Teste de integração: switch devolve código ≠ "00" → a transação existe no banco com status DECLINED e o motivo.
  - [ ] Falha de comunicação com o switch → também fica gravada.

### FIN-04 · Corrida na checagem de saque em andamento · P1 · P
- **Problema:** `checkActiveWithdrawals` roda antes do lock e fora de transação. Duas requisições com chaves de idempotência diferentes passam as duas.
- **O que fazer:** criar um índice único parcial no banco, por exemplo `CREATE UNIQUE INDEX ux_payout_active ON payout(merchant_id) WHERE status IN ('PENDING','WAITING_BANK_CONFIRMATION')`, e traduzir a violação para `ACTIVE_WITHDRAWAL`. Ou usar `SELECT … FOR UPDATE` na `ledger_account` dentro do `reserveFunds`.
- **Critérios de aceite:**
  - [ ] Teste concorrente: 2 saques simultâneos do mesmo lojista → só 1 é criado.
  - [ ] O script de migração foi adicionado em `database/`.

### FIN-05 · Chave de idempotência presa após timeout do PIX · P1 · P
- **Problema:** no caminho `WAITING_BANK_CONFIRMATION` a chave fica em `PROCESSING` por 24h, e o retry do cliente recebe "já está sendo processada".
- **O que fazer:** gravar um status `PENDING_CONFIRMATION` no Redis com a resposta 202, para que o retry receba o mesmo 202. Criar um job de reconciliação que consulta o banco/PSP e finaliza o payout com crédito ou confirmação.
- **Critérios de aceite:**
  - [ ] Retry com a mesma chave após timeout → 202 com o mesmo corpo.
  - [ ] O job de reconciliação tem teste.

### FIN-06 · Idempotência não separa lojistas · P2 · P
- **Problema:** a chave `idemp:{key}` é global, então lojistas diferentes podem colidir ou ler a resposta um do outro.
- **O que fazer:** usar o prefixo `idemp:{operacao}:{merchantId}:{key}`.
- **Critérios de aceite:**
  - [ ] Teste: a mesma chave usada por A e B gera dois processamentos independentes.

### FIN-07 · Duas fontes de verdade para o saldo · P2 · G
- **Problema:** a coluna `ledger_account.balance` e a soma de `ledger_entry` podem ficar diferentes.
- **O que fazer:** escrever um ADR definindo qual é a fonte oficial (recomendação: `balance` como cache materializado, atualizado na mesma transação das entries). Criar uma query/job de conciliação que alerta quando houver diferença.
- **Critérios de aceite:**
  - [ ] ADR registrado em `docs/adr/`.
  - [ ] Job de conciliação que loga/alerta divergências.

---

## Épico 3: Dados sensíveis e segredos

### SEC-06 · PAN completo trafega em claro via gRPC · P0 · G
- **Problema:** `AuthorizeTransactionUseCase.toAuthorizationRequest` envia `request.cardNumber()` com `negotiation-type: plaintext` para um IP fixo no código. O DTO ainda aceita CVV.
- **O que fazer:**
  - Ligar TLS (mTLS de preferência) no canal gRPC e tirar o endereço do yml (via variável de ambiente).
  - Avaliar se o PAN deveria chegar já cifrado do POS (o `EncryptionService` com RSA já existe) e ser repassado sem decifrar, ou trocado por um token.
  - Garantir que PAN e CVV nunca sejam gravados nem logados (auditar `toString` dos records e logs de exceção).
  - Fazer o `TransactionRequest.toString()` mascarar `cardNumber`, `cvv` e `pinBlock`.
- **Critérios de aceite:**
  - [ ] Canal gRPC com TLS em todos os profiles, exceto `local`.
  - [ ] Teste: `TransactionRequest.toString()` não contém o PAN.
  - [ ] Nenhuma coluna guarda o PAN completo.

### SEC-07 · Segredos com valor padrão no `application.yml` · P1 · P
- **Problema:** JWT secret, senhas do banco e do RabbitMQ, senha de monitoramento e e-mail do SMTP estão commitados.
- **O que fazer:** remover os valores padrão do `application.yml` (a aplicação deve falhar se não estiverem definidos) e deixá-los só em `application-local.yml`. **Rotacionar** o JWT secret, porque o atual está público no histórico do git.
- **Critérios de aceite:**
  - [ ] Subir sem `JWT_SECRET` no profile padrão → a aplicação não inicia.
  - [ ] Novo secret gerado e o antigo invalidado.

### SEC-08 · CORS inconsistente · P2 · P
- **Problema:** `@CrossOrigin("*")` no `DashboardController` e `@CrossOrigin` no `AuthController`, enquanto o `SecurityConfig` é restrito.
- **O que fazer:** remover os `@CrossOrigin` dos controllers e tornar as origens configuráveis por propriedade.
- **Critérios de aceite:**
  - [ ] Nenhum `@CrossOrigin` no código.
  - [ ] Origens definidas em `cors.allowed-origins`.

---

## Épico 4: Mensageria / Outbox

### MSG-01 · Eventos FAILED nunca são reenviados · P1 · M
- **O que fazer:** adicionar `attempts` e `next_attempt_at` à `outbox_event`, reenviar com backoff exponencial e marcar como `DEAD` após N tentativas, com alerta. Usar publisher confirms no RabbitMQ antes de marcar como `PROCESSED`.
- **Critérios de aceite:**
  - [ ] Teste: RabbitMQ fora do ar → o evento volta a ser publicado quando ele voltar.
  - [ ] Após N falhas o evento fica `DEAD` e é logado como erro.

### MSG-02 · Publicação duplicada com mais de uma instância · P1 · P
- **O que fazer:** buscar os pendentes com `SELECT … FOR UPDATE SKIP LOCKED LIMIT :batch` e processar em lotes pequenos. Ajustar o `fixedDelay` e corrigir o comentário (hoje diz 5 s, mas o intervalo é 500 ms). Documentar que os consumidores precisam ser idempotentes pelo `event.id`.
- **Critérios de aceite:**
  - [ ] Teste de integração com 2 relays em paralelo → cada evento é publicado uma vez.

---

## Épico 5: Build, testes e CI

### BLD-01 · Não compila com o JDK 27 · P1 · P
- **O que fazer:** fixar o JDK 21 (via `maven-toolchains-plugin` ou `maven-enforcer-plugin` com `requireJavaVersion [21,22)`) e documentar isso no README. Opcionalmente, atualizar o Lombok para uma versão compatível com JDK 27.
- **Critérios de aceite:**
  - [ ] Com JDK 27 o build mostra uma mensagem clara (enforcer) em vez do `EndPosTable`.
  - [ ] Com JDK 21, `./mvnw test` passa.

### BLD-02 · Testes de integração não são executados · P1 · P
- **O que fazer:** adicionar o `maven-failsafe-plugin` para os `*IT.java` (`SettlementResilienceIT` hoje é ignorado).
- **Critérios de aceite:**
  - [ ] `./mvnw verify` roda os ITs.

### BLD-03 · Meta de cobertura irreal (90% exigido, 34% atual) · P2 · P
- **O que fazer:** definir uma meta progressiva (ex.: 40% agora, com aumentos a cada sprint), ou aplicar a regra só ao pacote `domain`.
- **Critérios de aceite:**
  - [ ] `./mvnw verify` passa.
  - [ ] Meta registrada no README.

### BLD-04 · JVM do fork não encerra · P3 · P
- **Problema:** o Surefire avisa que "vai matar a JVM do fork" porque ela demora mais de 30 s para encerrar.
- **O que fazer:** investigar threads não-daemon (Netty/gRPC, `@Scheduled`, Testcontainers) e desligar o scheduler e o canal gRPC no profile de teste.
- **Critérios de aceite:**
  - [ ] O build termina sem esse aviso.

### BLD-05 · Testes que cobrem as falhas acima · P1 · (junto com cada item)
- **O que fazer:** testes de segurança por endpoint (MockMvc + JWT real) e testes de integração com Testcontainers para saque, estorno, negadas e outbox. Os testes que hoje mockam tudo não pegam esses problemas.
- **Critérios de aceite:**
  - [ ] Todo item P0/P1 entregue com o teste que reproduz o problema.

---

## Épico 6: Arquitetura e limpeza

### ARQ-01 · O domínio depende da infraestrutura · P2 · G
- **O que fazer:** mover as interfaces de repositório (`MerchantRepository`, `LedgerRepository`, ...) para `application/ports/output`. Os use cases passam a receber e devolver comandos/modelos de domínio em vez de DTOs REST. Mover o `GlobalExceptionHandler` e o `ErrorResponse` para `infrastructure/adapters/input/rest`. Considerar ArchUnit para garantir a regra.
- **Critérios de aceite:**
  - [ ] Nenhum `import orionpay.merchant.infrastructure` dentro de `domain` (teste ArchUnit).

### ARQ-02 · Erros de digitação e duplicações · P3 · P
- **O que fazer:**
  - Renomear os pacotes `excepion` → `exception` e `reposittory` → `repository`.
  - Remover a duplicação de `TransactionRepository` (há um em `persistence/` e outro em `persistence/reposittory/`).
  - Unificar `MerchantResponse` e `MerchantResponseDto`.
- **Critérios de aceite:**
  - [ ] Build e testes verdes depois da renomeação (fazer num PR isolado, só de rename).

### ARQ-03 · Anotações e logging misturados · P3 · P
- **O que fazer:** padronizar em `org.springframework.transaction.annotation.Transactional` e `@Slf4j`.
- **Critérios de aceite:**
  - [ ] Nenhum `jakarta.transaction.Transactional` nem `@Log4j2` no código.

### ARQ-04 · Estorno desligado responde 500 · P2 · P
- **O que fazer:** até o contrato gRPC suportar estorno, responder **501 Not Implemented** com uma mensagem clara (ou esconder o endpoint). Remover o código comentado; ele continua disponível no histórico do git. Abrir uma história separada para implementar o estorno de verdade.
- **Critérios de aceite:**
  - [ ] `POST /transactions/refund` → 501.
  - [ ] Nenhum bloco comentado no use case.

### ARQ-05 · Observabilidade configurada mas inativa · P2 · P
- **O que fazer:** adicionar `spring-boot-starter-actuator` e `micrometer-registry-prometheus`. Proteger `/actuator/**` com a credencial de monitoramento e trocar `show-details: always` por `when-authorized`.
- **Critérios de aceite:**
  - [ ] `/actuator/health` responde.
  - [ ] `/actuator/prometheus` exige autenticação.

### ARQ-06 · Limpeza de `docs/` e `scripts/` · P3 · P
- **O que fazer:** juntar ou apagar os arquivos `BUGFIX_*`, `*_FIX.md` e `CORREÇÃO_RESUMO.md` (são histórico de debug) e revisar se os `scripts/*.sh` ainda servem. Manter README, arquitetura, API e ADRs.
- **Critérios de aceite:**
  - [ ] `docs/` contém só documentação atual.

---

## Ordem sugerida (sprints)

| Sprint | Itens |
|---|---|
| **1: Estancar o risco** | SEC-01, SEC-02, SEC-03, SEC-04, SEC-05, FIN-01, FIN-02, BLD-01, BLD-05 (parcial) |
| **2: Integridade** | SEC-06, SEC-07, FIN-03, FIN-04, FIN-05, MSG-01, MSG-02, BLD-02 |
| **3: Robustez** | FIN-06, FIN-07, SEC-08, ARQ-04, ARQ-05, BLD-03 |
| **4: Saúde do código** | ARQ-01, ARQ-02, ARQ-03, ARQ-06, BLD-04 |
