# Infraestrutura de produção no Azure: uma VM Linux com Docker que roda o docker-compose.prod.yml.
# Só as portas 80 e 443 ficam abertas; não há SSH liberado — as atualizações chegam pelo pipeline (identidade.tf).

terraform {
  required_version = ">= 1.6"
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 3.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
}

provider "azuread" {}

locals {
  host = var.dominio != "" ? var.dominio : azurerm_public_ip.app.fqdn
}

resource "azurerm_resource_group" "app" {
  name     = "${var.prefixo}-rg"
  location = var.localizacao
}

resource "azurerm_virtual_network" "app" {
  name                = "${var.prefixo}-vnet"
  address_space       = ["10.20.0.0/16"]
  location            = azurerm_resource_group.app.location
  resource_group_name = azurerm_resource_group.app.name
}

resource "azurerm_subnet" "app" {
  name                 = "app"
  resource_group_name  = azurerm_resource_group.app.name
  virtual_network_name = azurerm_virtual_network.app.name
  address_prefixes     = ["10.20.1.0/24"]
}

resource "azurerm_network_security_group" "app" {
  name                = "${var.prefixo}-nsg"
  location            = azurerm_resource_group.app.location
  resource_group_name = azurerm_resource_group.app.name

  security_rule {
    name                       = "https"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "*"
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  # Necessária para o Let's Encrypt validar o domínio e para redirecionar HTTP → HTTPS.
  security_rule {
    name                       = "http"
    priority                   = 110
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "80"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }
}

resource "azurerm_public_ip" "app" {
  name                = "${var.prefixo}-ip"
  location            = azurerm_resource_group.app.location
  resource_group_name = azurerm_resource_group.app.name
  allocation_method   = "Static"
  sku                 = "Standard"
  domain_name_label   = var.prefixo
}

resource "azurerm_network_interface" "app" {
  name                = "${var.prefixo}-nic"
  location            = azurerm_resource_group.app.location
  resource_group_name = azurerm_resource_group.app.name

  ip_configuration {
    name                          = "principal"
    subnet_id                     = azurerm_subnet.app.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.app.id
  }
}

resource "azurerm_network_interface_security_group_association" "app" {
  network_interface_id      = azurerm_network_interface.app.id
  network_security_group_id = azurerm_network_security_group.app.id
}

# O Azure exige uma chave SSH na criação da VM. Ela é gerada aqui e não é usada: a porta 22 fica fechada no NSG.
resource "tls_private_key" "nao_usada" {
  algorithm = "ED25519"
}

resource "azurerm_linux_virtual_machine" "app" {
  name                  = "${var.prefixo}-vm"
  location              = azurerm_resource_group.app.location
  resource_group_name   = azurerm_resource_group.app.name
  size                  = var.tamanho_vm
  admin_username        = "escalas"
  network_interface_ids = [azurerm_network_interface.app.id]

  admin_ssh_key {
    username   = "escalas"
    public_key = tls_private_key.nao_usada.public_key_openssh
  }

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "StandardSSD_LRS"
    disk_size_gb         = 32
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }

  custom_data = base64encode(templatefile("${path.module}/cloud-init.yaml.tftpl", {
    repositorio   = var.repositorio_github
    imagem        = "ghcr.io/${lower(var.repositorio_github)}"
    host          = local.host
    db_password   = var.db_password
    jwt_secret    = var.jwt_secret
    admin_senha   = var.admin_senha
    grafana_senha = var.grafana_senha
    app_seed      = var.app_seed
  }))
}
