---
name: orionpay-tester
description: >-
  Padrões de testes automatizados do OrionPay (JUnit 5, Mockito, MockMvc, Testcontainers).
  Use ao criar, revisar ou refatorar testes, ao investigar cobertura, ou ao provar que um bug
  de segurança, transação ou concorrência foi corrigido.
---

# OrionPay Tester

Você escreve testes que pegam bugs reais. A suíte atual passa verde e mesmo assim deixou passar falhas graves (acesso a dados de outro lojista, `@Transactional` sem efeito, rollback que apaga registros), porque os casos de uso foram testados só com mocks. Cada bug corrigido nasce com um teste que fica **vermelho** antes da correção e **verde** depois.

## Executar

- Use **JDK 21**: `JAVA_HOME=<jdk-21> ./mvnw test`. Com JDK mais novo a compilação quebra (BLD-01).
- `./mvnw test` roda apenas `*Test.java`. Arquivos `*IT.java` só rodam com o `maven-failsafe-plugin` (`./mvnw verify`), que ainda não está configurado (BLD-02). Até lá, testes de integração que precisam rodar sempre usam o sufixo `*Test`.
- O Docker precisa estar ativo para os testes com Testcontainers.

## Ferramentas

- JUnit 5, AssertJ (`assertThat`) e Mockito. Testes novos usam AssertJ, mesmo nos arquivos que ainda usam `Assertions` do JUnit.
- Spring Boot 3.5: use `@MockitoBean` / `@MockitoSpyBean` (`org.springframework.test.context.bean.override.mockito`). `@MockBean` e `@SpyBean` estão deprecados. Migre ao tocar em um teste antigo.
- Containers: o `../../../pom.xml` não tem `spring-boot-testcontainers`, então `@ServiceConnection` não está disponível. Use `@Testcontainers` + `@Container` + `@DynamicPropertySource` (referência: `SettlementResilienceIT`).
  - Redis: `GenericContainer<>("redis:7-alpine").withExposedPorts(6379)`.
  - Postgres e RabbitMQ: módulos `postgresql` e `rabbitmq`, já presentes no `../../../pom.xml`.
  - Nunca use H2: há queries nativas e dialeto Postgres.

## Estratégia por camada

| Alvo | Tipo | Como |
|---|---|---|
| Modelo de domínio (`domain/model`) | Unitário puro | `new`/builder, sem Spring |
| Casos de uso (hoje em `domain/service`) | Unitário | `@ExtendWith(MockitoExtension.class)`, mock dos repositórios/ports, verificação das exceções de domínio e das interações relevantes |
| Controllers | Slice | `@WebMvcTest(Controller.class)` + `@MockitoBean` dos casos de uso; status HTTP, corpo JSON, Bean Validation |
| Repositórios e queries nativas | Integração | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + Postgres em container |
| Fluxos com transação, outbox, Redis | Integração | `@SpringBootTest` + containers, com os casos de uso reais e só o gateway externo (switch gRPC, PIX) mockado |

## Testes que mocks não substituem

Mocks não exercitam proxy do Spring, rollback, constraints do banco nem concorrência. Estes cenários exigem teste com contexto Spring e banco real:

- **Isolamento de lojista:** todo endpoint que lê ou altera dado de lojista tem um teste com JWT real (via `JwtService`, sem `@WithMockUser`) no qual o token de A acessa o recurso de B → 403/404. Num endpoint que recebe dinheiro (saque, antecipação), confira também que o saldo de B não mudou.
- **Atomicidade:** force uma falha no meio do fluxo (por exemplo, `@MockitoSpyBean` no repositório lançando exceção no `save`) e confira no banco que nada parcial ficou gravado: saldo inalterado, nenhuma `ledger_entry` órfã.
- **Registro que sobrevive ao erro:** fluxo que grava e devolve erro (transação negada) → o registro existe no banco depois da exceção.
- **Concorrência:** N requisições em paralelo (`ExecutorService` + `CountDownLatch`) contra a mesma conta/lojista, com saldo final e contagem de registros conferidos.
- **Idempotência:** mesma chave duas vezes → um único efeito e a mesma resposta. Mesma chave em lojistas diferentes → dois efeitos.
- **Outbox:** com o broker indisponível, o evento continua pendente/reenviável. Na sequência, use `Awaitility` para esperar a publicação.

## Convenções

- Nome do método: `should<Resultado>_When<Condição>`, por exemplo `shouldReturnForbidden_WhenMerchantAccessesAnotherMerchantData`. Renomeie os `test*` antigos ao tocar no arquivo.
- Corpo dividido em `// Given`, `// When`, `// Then`.
- O pacote do teste espelha o pacote da classe testada.
- Fixtures com dados explícitos no teste. Nada depende da ordem de execução nem de dados deixados por outro teste.

## Cobertura

- O relatório fica em `../../../target/site/jacoco/index.html` depois do `./mvnw test`. Cobertura em 2026-11-25: 34%.
- O `jacoco-check` exige 90% e quebra o `verify` (BLD-03). Priorize cobrir fluxos financeiros e de segurança antes de subir o número com getters e DTOs.

## Pronto quando

- Todo bug corrigido tem o teste que reproduz o problema, comprovado vermelho antes da correção.
- Todo endpoint novo ou alterado tem o teste de isolamento de lojista.
- `./mvnw test` passa com JDK 21.
