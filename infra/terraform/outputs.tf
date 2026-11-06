# Valores a cadastrar no ambiente "producao" do GitHub (Settings → Environments → producao → Variables).

output "APP_URL" {
  value = "https://${local.host}"
}

output "AZURE_CLIENT_ID" {
  value = azuread_application.pipeline.client_id
}

output "AZURE_TENANT_ID" {
  value = data.azuread_client_config.atual.tenant_id
}

output "AZURE_SUBSCRIPTION_ID" {
  value = var.subscription_id
}

output "AZURE_RESOURCE_GROUP" {
  value = azurerm_resource_group.app.name
}

output "AZURE_VM" {
  value = azurerm_linux_virtual_machine.app.name
}

output "IP_PUBLICO" {
  description = "Para apontar um domínio próprio (registro A)."
  value       = azurerm_public_ip.app.ip_address
}
