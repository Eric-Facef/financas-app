# Finanças API

Backend do app de controle financeiro (PWA). Spring Boot + Spring Security (JWT) + PostgreSQL.

## Rodar local

```bash
docker compose up -d            # Postgres local
cp .env.example .env            # opcional; o profile dev já tem defaults
mvn spring-boot:run             # http://localhost:8080
```

- Swagger (somente dev): http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Testes: `mvn test`

## Camadas (por feature)

```
auth/         register, login, refresh (cookie HttpOnly + rotação), logout
user/         entidade User + dados iniciais (contas e categorias padrão)
account/      contas (corrente/poupança); saldo é derivado das transações
category/     categorias de receita/despesa
transaction/  lançamentos (CRUD, filtros, paginação, exclusão lógica)
transfer/     transferência atômica entre contas (alocação da sobra)
dashboard/    resumo do mês: saldos, sobra, gráfico % por categoria
audit/        log de auditoria (gravado após o commit)
common/       segurança/JWT, config, exceções (ProblemDetail), paginação
```

Cada feature segue: `Controller -> Service -> Repository -> Entity`, com DTOs (`record`) em `dto/`.

## Endpoints (`/api/v1`)

| Método | Rota | Descrição |
|---|---|---|
| POST | /auth/register, /auth/login | Cria conta / entra. Retorna access token; refresh vai em cookie |
| POST | /auth/refresh, /auth/logout | Renova (rotaciona) / encerra sessão |
| GET, POST | /accounts | Lista com saldo / cria conta |
| GET, POST, PUT, DELETE | /categories | CRUD de categorias (`?kind=EXPENSE`) |
| GET, POST, PUT, DELETE | /transactions | `?month=2026-09&type=&categoryId=&accountId=&page=&size=` |
| POST | /transfers | Transfere entre contas (ex.: corrente -> poupança) |
| GET | /dashboard?month=2026-09 | Saldos, receitas, despesas, sobra e % por categoria |
| GET | /audit?page=&size= | Histórico de ações do usuário |

## Deploy (Render + Neon + UptimeRobot)

Variáveis no Render:

| Variável | Valor |
|---|---|
| SPRING_PROFILES_ACTIVE | `prod` |
| JWT_SECRET | string aleatória com 32+ caracteres |
| DATABASE_URL | `jdbc:postgresql://<host-pooler>/<db>?sslmode=require` |
| FLYWAY_URL | mesma URL, mas do host **direto** (sem `-pooler`) |
| DB_USER / DB_PASSWORD | credenciais do Neon |
| ALLOWED_ORIGINS | URL(s) do front, separadas por vírgula (inclua a origem da própria API se o front for servido por ela) |
| COOKIE_SECURE / COOKIE_SAMESITE | `true` / `None` se o front estiver em outro domínio; `true` / `Lax` se for o mesmo site |

- Tipo de serviço: Docker (usa o `Dockerfile` da raiz). O Render injeta `PORT`.
- UptimeRobot: monitor HTTP em `https://<seu-app>.onrender.com/actuator/health` a cada 5 min.

## Decisões de projeto

- Dinheiro em `BigDecimal` / `NUMERIC(19,2)`.
- Saldo nunca é guardado: é `saldo inicial + créditos - débitos`, calculado do histórico.
- Transferência = duas linhas (`TRANSFER_OUT` / `TRANSFER_IN`) com o mesmo `transfer_id`, com lock na conta de origem.
- Exclusão lógica (`deleted_at`) em lançamentos.
- Refresh token opaco, guardado só como hash SHA-256, com rotação e detecção de reuso.
- Toda consulta filtra por `user_id` do token (evita acesso a dados de outro usuário).
- Auditoria via evento `AFTER_COMMIT`: só registra o que realmente foi confirmado.
- Schema controlado só por Flyway (`ddl-auto: validate`).

## Próximos passos sugeridos

- Rate limit no `/auth/login` (Bucket4j)
- Testes de integração com Testcontainers (auth, transfer, dashboard)
- Orçamento por categoria, metas, lançamentos recorrentes
- Migrar para Spring Boot 4.x

## Front-end (PWA)

Fica em `src/main/resources/static/` e é servido pelo próprio Spring (mesma origem: sem CORS e com cookie `SameSite=Lax`).
Arquivos: `index.html`, `app.js`, `service-worker.js`, `manifest.webmanifest`, `icons/`.

- Local: `mvn spring-boot:run` e abra http://localhost:8080
- Produção: adicione a URL do próprio app (ex.: `https://financas.onrender.com`) em `ALLOWED_ORIGINS`, pois o navegador envia esse `Origin` no refresh/logout.
- Para separar o front depois, altere `API` no topo do `app.js` e use `COOKIE_SAMESITE=None` + `COOKIE_SECURE=true`.

### Testar o front com Live Server (VS Code)

1. Suba o backend: `docker compose up -d` e `mvn spring-boot:run` (porta 8080).
2. Abra a pasta `financas-api` no VS Code (o `.vscode/settings.json` já aponta o Live Server para `static/`).
3. Clique em **Go Live** e acesse **http://localhost:5500** (use `localhost`, não `127.0.0.1`, nos dois lados).

O `app.js` detecta a porta 5500/5501/5173 e chama a API em `http://localhost:8080`. O CORS do perfil dev já libera essas origens.
Como `localhost:5500` e `localhost:8080` são o mesmo *site*, o cookie de refresh (`SameSite=Lax`) funciona normalmente.
