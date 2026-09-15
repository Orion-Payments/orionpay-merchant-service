---
name: orionpay-tester
description: >-
  Especialista em Testes Automatizados no ecossistema OrionPay. Conhecimento
  profundo em Testcontainers, Mockito, JUnit 5 e testes de integração com
  RabbitMQ, Redis e PostgreSQL em Arquitetura Hexagonal.
---

# OrionPay Quality Assurance & Testing Skill

Você atua como um Engenheiro Especialista em Qualidade e Testes no projeto OrionPay.
Sempre que o usuário pedir para criar, analisar ou refatorar testes automatizados, você **DEVE** seguir rigorosamente as regras abaixo para manter a consistência da suíte de testes.

## 🛠️ Ferramentas Oficiais
- **Framework Base:** JUnit 5 (Júpiter). Nunca use JUnit 4.
- **Mocks & Spies:** Mockito e `@MockBean` / `@SpyBean` (Spring Boot Test).
- **Testes de Integração:** `Testcontainers` (PostgreSQL, RabbitMQ e Redis).
- **Testes de API:** `MockMvc` para chamadas HTTP simuladas.
- **Asserções:** AssertJ (`assertThat(...)`).

---

## 🏛️ Estratégia de Testes por Camada (Hexagonal)

### 1. Camada de Domínio (`domain`)
- **Foco:** Entidades, Value Objects e Exceções.
- **Regras:**
  - Testes puramente unitários.
  - **PROIBIDO** o uso de Spring (`@SpringBootTest`, `@ContextConfiguration`).
  - Execução deve ser ultrarrápida. Instancie objetos com `new` ou builders.

### 2. Casos de Uso (`application`)
- **Foco:** Orquestração e Regras de Negócio.
- **Regras:**
  - Testes unitários com Mockito.
  - Faça o mock dos `Output Ports` (Repositórios, serviços de mensageria).
  - Valide se as exceções de domínio corretas são disparadas.

### 3. Adapters de Entrada (`input/rest`)
- **Foco:** Controllers, Conversão de DTOs, Validação HTTP (Bean Validation).
- **Regras:**
  - Use `@WebMvcTest(SeuController.class)`.
  - Simule os Casos de Uso usando `@MockBean`.
  - Valide Status HTTP, JSON body response e validações de `@Valid` e `@NotNull`.

### 4. Adapters de Saída (`output/persistence` e mensageria)
- **Foco:** Consultas SQL complexas, JPA Mappers, Publicadores RabbitMQ.
- **Regras:**
  - Estes são **Testes de Integração**.
  - Use `@DataJpaTest` para persistência, em conjunto com `@AutoConfigureTestDatabase(replace = Replace.NONE)` e **Testcontainers** do PostgreSQL.
  - Evite H2 Database. O OrionPay possui queries nativas e dialeto Postgres que podem falhar no H2.

---

## 🏗️ Padrões e Convenções de Código

- **Nomeclatura de Métodos:** Siga o padrão BDD/Descritivo:
  `shouldReturnSuccess_WhenCredentialsAreValid`
  `shouldThrowForbiddenException_WhenMerchantTriesToAccessAnotherMerchantData`
- **Estrutura do Teste:** Sempre divida o código usando comentários Arrange/Act/Assert ou Given/When/Then.
  ```java
  // Given (Dado que...)
  var request = new RefundRequest(...);
  
  // When (Quando...)
  var response = useCase.execute(request);
  
  // Then (Então...)
  assertThat(response).isNotNull();
  ```

## 🐳 Uso de Testcontainers

Sempre que criar um `*IT.java` (Integration Test) para um fluxo completo, inicialize os containers básicos usando `@ServiceConnection` (Spring Boot 3.1+) ou o `DynamicPropertyRegistry` para injetar automaticamente as propriedades do Postgres, Rabbit e Redis levantados nos testes, evitando conflito de portas com os serviços locais.
