# Exception Intelligence

Plataforma de monitoramento inteligente de exceções com análise automática por IA. Captura erros em aplicações Vue.js, NestJS/Express e Java Spring Boot, envia para um servidor central que usa LLM para analisar a causa raiz, criar issues no Jira/GitHub e sugerir correções via Pull Request.

## Como funciona

```
Aplicação (Vue / NestJS / Java)
        │  SDK captura exceção + contexto
        ▼
Exception Intelligence Server (porta 8090)
        │
        ├── LLM (Claude / GPT-4 / Gemini / Bedrock) → analisa causa raiz
        ├── Deduplicação → evita flood de eventos repetidos
        ├── Issue Tracker (Jira / GitHub Issues) → cria bug automaticamente
        ├── SCM (GitHub) → cria PR com sugestão de fix
        └── Notificações (Teams / Slack)
```

## Estrutura do repositório

```
ia-exception-handler/
├── exception-intelligence-server/   # Servidor Java Spring Boot (porta 8090)
├── sdks/
│   ├── vue/                         # @exception-intelligence/sdk-vue
│   ├── node/                        # @exception-intelligence/sdk-node
│   └── exception-intelligence-sdk-java/  # Maven: io.github.exceptionintelligence
├── front-mfe-menu/                  # Micro-frontend Vue 3 de demonstração
├── k8s/                             # Manifests Kubernetes
└── .github/workflows/               # CI/CD: publish NPM + Maven + deploy server
```

---

## SDKs

### Vue.js (`@exception-intelligence/sdk-vue`)

```bash
npm install @exception-intelligence/sdk-vue
```

```ts
// main.ts
import { createApp } from 'vue';
import { ExceptionIntelligencePlugin } from '@exception-intelligence/sdk-vue';
import App from './App.vue';

const app = createApp(App);

app.use(ExceptionIntelligencePlugin, {
  serverUrl: 'https://exception-intelligence-server.mycompany.com',
  serviceName: 'meu-frontend',
  environment: 'production',
  projectUrls: ['myapp.com'],
  repository: {
    owner: 'minha-org',
    name: 'meu-repo',
  },
});

app.mount('#app');
```

Captura automaticamente:
- Erros de componentes Vue (lifecycle, render, watchers)
- `window.onerror` e `unhandledrejection`

---

### NestJS / Node.js (`@exception-intelligence/sdk-node`)

```bash
npm install @exception-intelligence/sdk-node
```

**NestJS:**
```ts
// main.ts
import { NestFactory } from '@nestjs/core';
import { createNestJsExceptionFilter } from '@exception-intelligence/sdk-node';
import { AppModule } from './app.module';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  app.useGlobalFilters(createNestJsExceptionFilter({
    serverUrl: 'http://exception-intelligence-server:8090',
    serviceName: 'meu-backend',
    environment: 'production',
    projectPaths: ['/app/src'],
    repository: {
      owner: 'minha-org',
      name: 'meu-repo',
    },
  }));

  await app.listen(3000);
}
bootstrap();
```

**Express:**
```ts
import express from 'express';
import { createExpressErrorHandler } from '@exception-intelligence/sdk-node';

const app = express();

// deve ser o último middleware
app.use(createExpressErrorHandler({
  serverUrl: 'http://exception-intelligence-server:8090',
  serviceName: 'meu-express-app',
}));
```

---

### Java / Spring Boot

```xml
<!-- pom.xml -->
<dependency>
  <groupId>io.github.exceptionintelligence</groupId>
  <artifactId>exception-intelligence-sdk-java</artifactId>
  <version>1.0.0</version>
</dependency>
```

```yaml
# application.yml
exception-intelligence:
  server-url: http://exception-intelligence-server:8090
  service-name: meu-servico-java
  environment: production
```

Auto-configuração via Spring Boot — nenhum código adicional necessário.

---

## Servidor (`exception-intelligence-server`)

### Configuração

Copie `.env.example` e preencha as variáveis:

```bash
cd exception-intelligence-server
cp .env.example .env
```

Principais variáveis:

| Variável | Descrição |
|---|---|
| `ANTHROPIC_KEY` | API key do Claude (Anthropic) |
| `OPENAI_KEY` | API key do OpenAI (opcional) |
| `GEMINI_API_KEY` | API key do Gemini (opcional) |
| `GITHUB_TOKEN` | Token GitHub para criar issues/PRs |
| `JIRA_URL` / `JIRA_API_TOKEN` | Integração com Jira |
| `TEAMS_WEBHOOK_URL` | Webhook do Microsoft Teams |
| `SLACK_WEBHOOK_URL` | Webhook do Slack |

### Executar localmente

```bash
cd exception-intelligence-server
./mvnw spring-boot:run
# Servidor disponível em http://localhost:8090
```

### Docker

```bash
cd exception-intelligence-server
docker build -t exception-intelligence-server .
docker run -p 8090:8090 --env-file .env exception-intelligence-server
```

### Providers suportados

**LLM:** `claude` | `openai` | `gemini` | `bedrock`

**Issue Tracker:** `jira` | `github`

**SCM:** `github`

**Notificação:** `teams` | `slack`

Configure em `application.yml` ou via variáveis de ambiente.

---

## Deploy Kubernetes

```bash
kubectl apply -f k8s/server/namespace.yaml
kubectl apply -f k8s/server/configmap.yaml
# preencha o secret antes:
kubectl apply -f k8s/server/secret.template.yaml
kubectl apply -f k8s/server/deployment.yaml
kubectl apply -f k8s/server/service.yaml
kubectl apply -f k8s/server/ingress.yaml
```

---

## CI/CD

| Workflow | Gatilho | O que faz |
|---|---|---|
| `publish-sdks.yml` | tag `sdk-v*` ou dispatch manual | Publica `sdk-vue` e `sdk-node` no npmjs.com |
| `publish-sdk-java.yml` | tag `java-v*` ou dispatch manual | Publica SDK Java no Maven Central |
| `deploy-server.yml` | push em `main` | Build e deploy do servidor |

### Publicar nova versão dos SDKs NPM

```bash
# Atualiza versão nos package.json e faz push da tag
VERSION=2.1.0
sed -i '' "s/\"version\": \".*\"/\"version\": \"$VERSION\"/" sdks/vue/package.json sdks/node/package.json
git add sdks/vue/package.json sdks/node/package.json
git commit -m "chore: bump SDKs to v$VERSION"
git tag sdk-v$VERSION
git push && git push --tags
```

### Secrets necessários no GitHub

| Secret | Descrição |
|---|---|
| `NPM_TOKEN` | Token de automação do npmjs.com |
| `MAVEN_USERNAME` / `MAVEN_PASSWORD` | Credenciais Maven Central |

---

## Licença

MIT
