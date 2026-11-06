variable "subscription_id" {
  description = "ID da assinatura do Azure (az account show --query id -o tsv)."
  type        = string
}

variable "repositorio_github" {
  description = "Repositório no formato dono/nome, exatamente como aparece no GitHub."
  type        = string
  default     = "GabrielCoelhom12/TCC-MERCADO-COELHO"
}

variable "prefixo" {
  description = "Prefixo dos recursos e do endereço público (<prefixo>.<região>.cloudapp.azure.com)."
  type        = string
  default     = "escalas-coelho"
}

variable "localizacao" {
  description = "Região do Azure."
  type        = string
  default     = "brazilsouth"
}

variable "tamanho_vm" {
  description = "Tamanho da VM. A B2s (2 vCPU, 4 GB) comporta aplicação, PostgreSQL, Prometheus e Grafana."
  type        = string
  default     = "Standard_B2s"
}

variable "dominio" {
  description = "Domínio próprio já apontado para o IP da VM. Vazio = usa o endereço gerado pelo Azure."
  type        = string
  default     = ""
}

# ---- segredos da aplicação (vão só para /etc/escalas/env na VM; nunca para o repositório) ----

variable "db_password" {
  type      = string
  sensitive = true
}

variable "jwt_secret" {
  description = "Chave Base64 com ≥ 32 bytes (openssl rand -base64 48)."
  type        = string
  sensitive   = true
}

variable "admin_senha" {
  description = "Senha do usuário gestor criado no primeiro start (8+ caracteres, maiúscula, minúscula, número e especial)."
  type        = string
  sensitive   = true
}

variable "grafana_senha" {
  type      = string
  sensitive = true
}

variable "app_seed" {
  description = "true = carrega o cenário de demonstração quando o banco está vazio."
  type        = bool
  default     = true
}
