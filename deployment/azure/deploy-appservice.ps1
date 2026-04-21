param(
  [string]$Subscription,
  [string]$Location = "eastus",
  [string]$ResourceGroup = "rg-volt-scheduler-prod",
  [string]$AcrName = "acrvoltschedulerprod",
  [string]$PlanName = "asp-volt-scheduler-prod",
  [string]$WebAppName = "volt-scheduler-app-prod",
  [string]$PgServerName = "pg-volt-scheduler-prod",
  [string]$PgAdminUser = "calendaradmin",
  [Parameter(Mandatory = $true)][string]$PgAdminPassword,
  [string]$PgDatabase = "calendar_app",
  [string]$ImageTag = "calendar-app:v1"
)

$ErrorActionPreference = "Stop"

if (-not $Subscription) {
  Write-Host "No subscription provided. Using current az account context." -ForegroundColor Yellow
} else {
  az account set --subscription $Subscription | Out-Null
}

Write-Host "Creating resource group..."
az group create --name $ResourceGroup --location $Location | Out-Null

Write-Host "Creating ACR..."
az acr create --resource-group $ResourceGroup --name $AcrName --sku Basic --admin-enabled true | Out-Null

Write-Host "Creating App Service plan..."
az appservice plan create --resource-group $ResourceGroup --name $PlanName --is-linux --sku B1 | Out-Null

Write-Host "Creating PostgreSQL flexible server..."
az postgres flexible-server create `
  --resource-group $ResourceGroup `
  --name $PgServerName `
  --location $Location `
  --admin-user $PgAdminUser `
  --admin-password $PgAdminPassword `
  --sku-name Standard_B1ms `
  --tier Burstable `
  --version 16 `
  --storage-size 32 `
  --public-access 0.0.0.0 | Out-Null

Write-Host "Creating PostgreSQL database..."
az postgres flexible-server db create --resource-group $ResourceGroup --server-name $PgServerName --database-name $PgDatabase | Out-Null

Write-Host "Building image in ACR..."
az acr build --registry $AcrName --image $ImageTag . | Out-Null

$acrLoginServer = az acr show --resource-group $ResourceGroup --name $AcrName --query loginServer -o tsv
$acrUser = az acr credential show --name $AcrName --query username -o tsv
$acrPass = az acr credential show --name $AcrName --query passwords[0].value -o tsv

Write-Host "Creating web app..."
az webapp create `
  --resource-group $ResourceGroup `
  --plan $PlanName `
  --name $WebAppName `
  --deployment-container-image-name "$acrLoginServer/$ImageTag" | Out-Null

Write-Host "Setting container registry config..."
az webapp config container set `
  --resource-group $ResourceGroup `
  --name $WebAppName `
  --container-image-name "$acrLoginServer/$ImageTag" `
  --container-registry-url "https://$acrLoginServer" `
  --container-registry-user $acrUser `
  --container-registry-password $acrPass | Out-Null

$pgHost = "$PgServerName.postgres.database.azure.com"
$jdbcUrl = "jdbc:postgresql://$pgHost:5432/$PgDatabase?sslmode=require"

Write-Host "Setting app settings..."
az webapp config appsettings set `
  --resource-group $ResourceGroup `
  --name $WebAppName `
  --settings `
    SERVER_PORT=8080 `
    WEBSITES_PORT=8080 `
    SPRING_THYMELEAF_CACHE=true `
    SPRING_DATASOURCE_URL=$jdbcUrl `
    SPRING_DATASOURCE_USERNAME=$PgAdminUser `
    SPRING_DATASOURCE_PASSWORD=$PgAdminPassword | Out-Null

$appHost = az webapp show --resource-group $ResourceGroup --name $WebAppName --query defaultHostName -o tsv
Write-Host "Deployment complete. App URL: https://$appHost" -ForegroundColor Green
Write-Host "If DB auth fails, set SPRING_DATASOURCE_USERNAME to $PgAdminUser@$PgServerName in App Settings." -ForegroundColor Yellow
