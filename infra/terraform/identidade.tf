# Identidade do pipeline no Azure, sem senha guardada no GitHub: o GitHub Actions troca o token OIDC do job pelo
# acesso desta aplicação. Só jobs do ambiente "producao" deste repositório são aceitos, e a permissão vale apenas
# para a VM (executar o infra/atualizar.sh via run-command).

data "azuread_client_config" "atual" {}

resource "azuread_application" "pipeline" {
  display_name = "${var.prefixo}-github-actions"
  owners       = [data.azuread_client_config.atual.object_id]
}

resource "azuread_service_principal" "pipeline" {
  client_id = azuread_application.pipeline.client_id
  owners    = [data.azuread_client_config.atual.object_id]
}

resource "azuread_application_federated_identity_credential" "producao" {
  application_id = azuread_application.pipeline.id
  display_name   = "github-producao"
  audiences      = ["api://AzureADTokenExchange"]
  issuer         = "https://token.actions.githubusercontent.com"
  subject        = "repo:${var.repositorio_github}:environment:producao"
}

resource "azurerm_role_assignment" "pipeline_vm" {
  scope                = azurerm_linux_virtual_machine.app.id
  role_definition_name = "Virtual Machine Contributor"
  principal_id         = azuread_service_principal.pipeline.object_id
}
