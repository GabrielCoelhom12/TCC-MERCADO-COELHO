# Conformidade — LGPD, segurança, acessibilidade e licenças

Normas aplicáveis conforme as [Normas e Regulamentações do Portfólio](https://github.com/CatolicaSC-Portfolio/The-Portfolio-Playbook/blob/main/documentation/normas.md)
e como cada uma é atendida no sistema.

## LGPD (Lei nº 13.709/2018)

O sistema trata dados pessoais de **colaboradores** do supermercado e dos **usuários** (gestores) que o operam.

| Dado | Titular | Finalidade | Base legal (art. 7º) |
|------|---------|------------|----------------------|
| Nome, matrícula, cargo, setor | Colaborador | Identificar quem está na escala | Execução de contrato de trabalho (V) |
| Salário, carga horária, % de encargos | Colaborador | Calcular o custo da escala e o limite do dia | Execução de contrato (V) e obrigação legal trabalhista (II) |
| Dias disponíveis e ausências (férias, atestado, folga) | Colaborador | Não escalar quem não pode trabalhar | Execução de contrato (V) e obrigação legal (II) |
| Foto (opcional) | Colaborador e usuário | Reconhecimento visual na matriz e no chat | Consentimento (I) — o envio é opcional e pode ser removido |
| Login, nome, senha (hash), perfil | Usuário | Autenticação e controle de acesso | Execução de contrato (V) |
| Mensagens e imagens do chat | Usuário | Comunicação interna da gestão | Legítimo interesse do controlador (IX) |

**Medidas adotadas**

- **Minimização:** só são guardados os dados necessários para montar e validar a escala. Não há CPF, endereço,
  telefone nem dado sensível (art. 5º, II). Ausências registram o motivo (férias, atestado, folga acordada) e uma
  observação livre, que **não deve conter diagnóstico** ou CID — orientação repassada aos gestores do parceiro.
- **Controle de acesso:** toda a API exige autenticação JWT (`SecurityConfig`); a gestão de usuários é restrita ao
  perfil `ADMIN`. A situação e o perfil do usuário são conferidos no banco a cada requisição (`AutenticadorJwt`):
  uma conta desativada perde o acesso imediatamente, mesmo com um token ainda dentro da validade. No chat, cada
  usuário só recebe eventos das conversas de que participa.
- **Senhas:** armazenadas apenas como hash **BCrypt**, com política de senha forte (`SenhaForteValidator`).
- **Imagens:** fotos e anexos são validados e **regravados em JPEG** no servidor (`Imagens`), o que descarta metadados
  como localização (EXIF). Imagens do chat só são servidas a participantes da conversa.
- **Exclusão:** a foto pode ser removida a qualquer momento. Colaborador sem histórico de escala é excluído; com
  histórico, é apenas **desativado**, para preservar a auditoria das escalas aprovadas (art. 16, I — cumprimento de
  obrigação legal).
- **Segredos fora do código:** credenciais do banco, chave JWT e senhas vêm de variáveis de ambiente
  ([deploy.md](deploy.md)); o repositório não contém nenhum segredo.
- **Transporte:** em produção, todo o tráfego passa por **HTTPS** (proxy Caddy com certificado Let's Encrypt).
- **Dados de demonstração:** o cenário carregado no primeiro start (`APP_SEED`) usa **nomes e valores fictícios**.
  Os dados reais do parceiro só entram no ambiente de produção, com `APP_SEED=false`.

## Segurança (OWASP Top 10)

| Risco | Medida |
|-------|--------|
| Quebra de controle de acesso | Rotas protegidas por padrão; perfil `ADMIN` para usuários; filtro por participante no chat. Testado em `*IntegracaoTest`. |
| Falhas de criptografia | BCrypt nas senhas; JWT assinado (HMAC-SHA) com chave do ambiente e expiração de 10 h; HTTPS em produção. |
| Injeção | Acesso a dados só por Spring Data JPA (consultas parametrizadas); validação de entrada com Bean Validation. |
| XSS | O React escapa todo conteúdo renderizado; não há `dangerouslySetInnerHTML`. Imagens enviadas são regravadas. |
| CSRF | Não se aplica: a autenticação é por cabeçalho `Authorization` (sem cookie de sessão), por isso o CSRF do Spring fica desligado. |
| Configuração insegura | Métricas e health check só na porta interna 8081; contêiner roda com usuário sem privilégios. |
| Componentes vulneráveis | Análise estática e de segurança no SonarCloud a cada push. |

## Acessibilidade (WCAG 2.1)

- Página declarada em português (`lang="pt-BR"`), com títulos hierárquicos por tela.
- Botões só com ícone têm `aria-label`; ícones decorativos usam `aria-hidden`; logotipos têm texto alternativo.
- Foco visível em links, botões e campos (`:focus-visible`), permitindo navegação por teclado.
- Abas com `role="tablist"`/`aria-selected`; mensagens de feedback em região `role="status"`.
- Animações respeitam `prefers-reduced-motion`.
- Layout responsivo, utilizável em celular.

## Licenças

- **Código do projeto:** licença [MIT](../LICENSE).
- **Logotipos do Supermercado Coelho** (`frontend/public/logo*`): pertencem ao parceiro e não estão cobertos pela
  licença MIT.
- **Dependências de terceiros**, todas com licenças permissivas compatíveis com o uso:

| Componente | Licença |
|------------|---------|
| Spring Boot, Spring Security, Spring Data, Micrometer, JJWT | Apache 2.0 |
| Driver JDBC do PostgreSQL | BSD-2-Clause |
| Lombok | MIT |
| ArchUnit (somente nos testes) | Apache 2.0 |
| React, React DOM, React Router | MIT |
| @stomp/stompjs | Apache 2.0 |
| lucide-react (ícones) | ISC |
| ESLint, typescript-eslint (somente no desenvolvimento) | MIT |
| Fontes Albert Sans e Newsreader (@fontsource) | SIL Open Font License 1.1 |
| PostgreSQL, Prometheus, Grafana, Caddy (imagens Docker) | PostgreSQL License, Apache 2.0, AGPL 3.0 (usado sem modificação), Apache 2.0 |
