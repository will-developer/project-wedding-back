# wedding-api

API do site do meu casamento: convidados confirmam presença e escolhem presentes de uma
lista (catálogo de produtos e cotas em dinheiro), com pagamento via Mercado Pago (PIX e
cartão). Um painel de administrador cadastra convidados e presentes.

> Projeto pessoal em construção, documentado publicamente como parte do meu aprendizado.

## Status

🚧 Em desenvolvimento inicial. Ainda não há endpoints públicos.

- [x] Esqueleto do projeto (Spring Boot, Maven)
- [x] Testes com Postgres real via Testcontainers
- [ ] Ambiente local com Docker
- [ ] Migration inicial do banco (Flyway)
- [ ] `/actuator/health` no ar em homologação
- [ ] Pipeline de CI/CD
- [ ] Endpoints de convite/RSVP
- [ ] Integração com Mercado Pago (sandbox)

## Stack

- **Java 21** + **Spring Boot**, Maven
- **PostgreSQL**, com migrations em **Flyway** (schema versionado, sem `ddl-auto` gerando
  tabelas automaticamente)
- **JUnit 5** + **Testcontainers** para testes (Postgres real em container, nunca banco em
  memória)
- **Spring Security** para autenticação do painel administrativo

## Decisões de arquitetura

- **O banco não tem valores padrão (`DEFAULT`)** em nenhuma coluna de negócio. IDs,
  timestamps, status iniciais e configuração de gateway de pagamento são sempre definidos
  explicitamente pela aplicação. O banco só garante integridade: `NOT NULL`, `CHECK`,
  `UNIQUE` e chaves estrangeiras.
- **Estoque de presentes é reservado na criação do pedido**, com atualização condicional
  atômica (`UPDATE ... WHERE quantidade_disponivel >= :quantidade`), nunca lendo e
  escrevendo em passos separados.
- **Cada aviso (webhook) do gateway de pagamento é único** por `(provider,
  provider_event_id)`. O processamento sempre insere primeiro e só trata quem conseguiu
  inserir, para tolerar reenvios em paralelo do mesmo aviso.
- Mais decisões e o porquê de cada uma estão em [`AGENTS.md`](AGENTS.md).

## Rodando localmente

> Ainda não configurado — parte do roadmap acima.

Pré-requisitos previstos: Java 21, Docker (para o Postgres local e para os testes com
Testcontainers).

```bash
./mvnw verify
```

Roda a suíte de testes, incluindo os testes de integração com um Postgres real via
Testcontainers. Requer o Docker em execução.

## Ambientes

| Ambiente | Onde roda | Banco |
|---|---|---|
| Local | Máquina do desenvolvedor (Docker) | Postgres em container |
| Homologação | Render (free tier) | Neon (free tier) |
| Produção | AWS Lambda | Neon |

## Licença

Projeto pessoal, sem licença de reuso definida.
