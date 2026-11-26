---
name: orionpay-specialist
description: >-
  Regras de arquitetura e de segurança financeira do OrionPay Merchant Service. Use ao criar
  ou revisar endpoints, casos de uso, fluxos que movem dinheiro (autorização, saque,
  antecipação, liquidação, estorno), integrações (gRPC, RabbitMQ, Redis) ou ao replicar o
  padrão em um novo microsserviço.
---

# OrionPay Specialist

Você implementa e revisa código de um sistema financeiro. Cada regra abaixo existe porque já houve um bug real que ela teria evitado. O débito técnico conhecido está em `../../../docs/BACKLOG.md` (os IDs `SEC-*`, `FIN-*` e outros são citados abaixo). Ao mexer em código que tem um item aberto, corrija dentro do escopo da tarefa ou cite o ID.

## Ambiente

- Build e testes rodam **só com JDK 21**. Com JDK mais novo o Lombok quebra com `EndPosTable` (BLD-01). Rode com `JAVA_HOME` apontando para o JDK 21.
- As versões de stack e dependências estão no `../../../pom.xml`. Ele é a fonte da verdade.

## Arquitetura hexagonal: estado alvo e estado atual

**Estado alvo** (todo código novo segue este):

- `domain`: modelo de negócio puro (entidades, VOs, enums, exceções de negócio). Sem Spring e sem imports de `infrastructure`.
- `application`: casos de uso e ports.
  - `application.ports.input`: contratos dos casos de uso.
  - `application.ports.output`: repositórios, gateways e publicadores, vistos como interfaces.
  - Os casos de uso recebem **comandos/modelos de domínio**, nunca DTOs REST.
- `infrastructure.adapters.input.rest`: controllers, DTOs HTTP, segurança e `GlobalExceptionHandler`.
- `infrastructure.adapters.output.{persistence,messaging,grpc,email,payment}`: implementações dos ports. Entidades JPA e mappers MapStruct ficam em `persistence`.

**Estado atual** (débito ARQ-01): os casos de uso ficam em `domain/service`, importam DTOs e repositórios de `infrastructure`, e as interfaces de repositório estão em `persistence/reposittory`. Os pacotes `excepion` e `reposittory` têm erro de digitação (ARQ-02). Ao alterar um caso de uso existente, mantenha a localização atual. Mover pacotes é tarefa dedicada de ARQ-01/ARQ-02, num PR isolado.

Padrões de código: `org.springframework.transaction.annotation.Transactional` e `@Slf4j` (ARQ-03).

## Isolamento de lojista (tenant)

O `merchantId` de qualquer operação vem **do token**: `SecurityContextService.getCurrentMerchantId()`.

- DTOs de entrada não têm campo `merchantId`. Headers como `X-Merchant-Id` não servem como identidade.
- Endpoint com `{id}` de lojista na URL valida URL == token antes de chamar o caso de uso (SEC-03).
- Recurso buscado por id próprio (transação, liquidação, payout) é filtrado também pelo `merchantId` do token. Se pertencer a outro lojista, responda **404**.
- Todo endpoint novo nasce com um teste de acesso entre lojistas (token de A, recurso de B → 403/404).

## Transações e dinheiro

- **Proxy do Spring:** `@Transactional` só vale em chamadas que passam pelo proxy. Um método anotado chamado via `this.` roda sem transação (FIN-01). Para separar fases (reserva → chamada externa → confirmação), coloque cada fase transacional em **outro bean**.
- **Chamada externa** (PIX, switch gRPC) fica **fora** da transação do banco. Antes dela, deixe o estado persistido como `PENDING`.
- **Saldo** só muda com `UPDATE` atômico condicional (`balance = balance ± :amount WHERE ...`), conferindo as linhas afetadas. Crédito e débito seguem a mesma regra (FIN-02).
- **Gravar e depois lançar exceção** na mesma transação desfaz a gravação. Para registrar uma recusa/falha e ainda assim devolver erro, use `noRollbackFor` ou `REQUIRES_NEW` (FIN-03).
- **Unicidade de negócio** ("um saque ativo por lojista", "um estorno por payout") fica garantida no banco, com índice único parcial ou `SELECT ... FOR UPDATE`. Uma checagem prévia na aplicação não protege contra concorrência (FIN-04).
- **Valores** são sempre `BigDecimal`, com `compareTo` para comparar.

## Idempotência

- APIs que alteram estado exigem `X-Idempotency-Key`. A chave no Redis inclui operação e lojista: `idemp:{operacao}:{merchantId}:{key}` (FIN-06).
- Todo desfecho grava um estado final na chave, inclusive o "pendente de confirmação" (devolva o mesmo 202 no retry). Uma chave só pode ficar em `PROCESSING` enquanto a requisição está viva (FIN-05).
- Consumidores de mensagem fazem **Check-and-Skip**: se a entidade já está no estado de destino, retornam sucesso sem efeito colateral.

## Outbox (at-least-once)

- Eventos gerados junto com uma escrita no banco vão para a tabela `outbox_event` **na mesma transação**. Nunca publique direto no RabbitMQ de dentro de um caso de uso.
- O relay busca com `FOR UPDATE SKIP LOCKED` em lotes, reenvia com backoff e marca como `DEAD` após N tentativas (MSG-01/MSG-02).
- O consumidor deduplica pelo `event.id`.

## Dados de cartão (PCI)

- PAN, CVV e PIN block nunca vão para log, banco, `toString()` ou mensagem de exceção. Persista apenas BIN + últimos 4 dígitos (`MaskingUtils`).
- Canal gRPC com o switch usa TLS. Endereços e segredos vêm de variáveis de ambiente, nunca com valor padrão no `application.yml` (SEC-06/SEC-07).

## Integrações síncronas

- Chamada a outro serviço tem `@CircuitBreaker` + `@Bulkhead` do Resilience4j, com instância configurada no `application.yml`. Referência: `LedgerIntegrationService`.
- O fallback deixa a operação num estado recuperável (`PENDING`, para reconciliação). Ele nunca engole a falha silenciosamente.

## Erros

- Regra de negócio violada → `DomainException` (ou subclasse) com código estável, traduzida no `GlobalExceptionHandler`.
- Funcionalidade indisponível → 501 com mensagem clara (ARQ-04).

## Ordem de implementação de uma feature

1. Modelo de domínio e regras puras, com testes unitários.
2. Ports de entrada e saída.
3. Caso de uso, recebendo o `merchantId` do chamador como parâmetro.
4. Adapters de saída: entidade JPA, mapper, repositório. Script SQL em `../../../database` para qualquer mudança de schema (`ddl-auto: validate`).
5. Controller: identidade vinda do token, `@Valid` no corpo, `X-Idempotency-Key` se alterar estado.
6. Revisão final contra as seções acima. Pronto quando:
   - todo `merchantId` vem do token;
   - toda transação passa pelo proxy;
   - toda alteração de saldo é atômica;
   - toda chamada externa está fora da transação e protegida por Resilience4j;
   - todo evento sai pelo outbox;
   - nenhum dado de cartão vaza;
   - os testes da skill `orionpay-tester` existem e passam.
