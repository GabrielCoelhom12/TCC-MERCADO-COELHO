# Backup do PostgreSQL fora da VM: o infra/backup.sh (cron diário na VM) grava um pg_dump neste container.
# A VM entra com a própria identidade gerenciada (sem chave nem senha guardada); a conta não aceita chaves de acesso
# nem acesso público, e os backups com mais de var.backup_retencao_dias dias são apagados sozinhos.

locals {
  # Nome globalmente único, só minúsculas e números, até 24 caracteres.
  conta_backup = substr("${replace(var.prefixo, "-", "")}bkp${substr(md5(var.subscription_id), 0, 6)}", 0, 24)
}

resource "azurerm_storage_account" "backup" {
  name                            = local.conta_backup
  resource_group_name             = azurerm_resource_group.app.name
  location                        = azurerm_resource_group.app.location
  account_tier                    = "Standard"
  account_replication_type        = "GRS" # cópia em outra região: o backup sobrevive à perda da região
  min_tls_version                 = "TLS1_2"
  https_traffic_only_enabled      = true
  allow_nested_items_to_be_public = false
  shared_access_key_enabled       = false # só Microsoft Entra ID (identidade da VM)

  blob_properties {
    # Um backup apagado por engano (ou por um invasor) ainda pode ser recuperado por 7 dias.
    delete_retention_policy {
      days = 7
    }
    container_delete_retention_policy {
      days = 7
    }
  }
}

resource "azurerm_storage_container" "backups" {
  name                  = "backups"
  storage_account_id    = azurerm_storage_account.backup.id
  container_access_type = "private"
}

resource "azurerm_storage_management_policy" "backup" {
  storage_account_id = azurerm_storage_account.backup.id

  rule {
    name    = "apagar-backups-antigos"
    enabled = true
    filters {
      prefix_match = ["backups/"]
      blob_types   = ["blockBlob"]
    }
    actions {
      base_blob {
        delete_after_days_since_creation_greater_than = var.backup_retencao_dias
      }
    }
  }
}

# A VM só grava (e lê) blobs deste container.
resource "azurerm_role_assignment" "vm_backup" {
  scope                = azurerm_storage_container.backups.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_linux_virtual_machine.app.identity[0].principal_id
}
