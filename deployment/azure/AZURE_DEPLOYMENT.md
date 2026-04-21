# Azure Deployment (Beginner Friendly)

This project is best deployed on Azure as a containerized app, because the project currently targets Java 25.

Recommended Azure architecture:
- Azure App Service (Linux, custom container)
- Azure Container Registry (stores your Docker image)
- Azure Database for PostgreSQL Flexible Server (managed database)

## Why this path

- Keeps your current Docker-based setup
- Avoids Java runtime version limits from platform-managed Java images
- Simplifies custom domain + HTTPS setup in App Service

## Prerequisites

- Azure account and active subscription
- Azure CLI installed
- Docker installed locally
- Your domain DNS managed by a provider where you can add CNAME/TXT records

## 1) Login and select subscription

```powershell
az login
az account set --subscription "<YOUR_SUBSCRIPTION_ID_OR_NAME>"
```

## 2) Set deployment variables

```powershell
$LOCATION = "eastus"
$RG = "rg-volt-scheduler-prod"
$ACR = "acrvoltschedulerprod"
$PLAN = "asp-volt-scheduler-prod"
$WEBAPP = "volt-scheduler-app-prod"
$PGSERVER = "pg-volt-scheduler-prod"
$PGADMIN = "calendaradmin"
$PGPASSWORD = "<STRONG_PASSWORD>"
$PGDB = "calendar_app"
$IMAGE = "calendar-app:v1"
```

Name constraints:
- `$ACR`, `$WEBAPP`, and `$PGSERVER` must be globally unique
- Use only lowercase letters, numbers, and dashes where required

## 3) Create Azure resources

```powershell
az group create --name $RG --location $LOCATION

az acr create --resource-group $RG --name $ACR --sku Basic --admin-enabled true

az appservice plan create --resource-group $RG --name $PLAN --is-linux --sku B1

az postgres flexible-server create `
  --resource-group $RG `
  --name $PGSERVER `
  --location $LOCATION `
  --admin-user $PGADMIN `
  --admin-password $PGPASSWORD `
  --sku-name Standard_B1ms `
  --tier Burstable `
  --version 16 `
  --storage-size 32 `
  --public-access 0.0.0.0

az postgres flexible-server db create --resource-group $RG --server-name $PGSERVER --database-name $PGDB
```

## 4) Build and push the app image to ACR

From the project root folder:

```powershell
az acr build --registry $ACR --image $IMAGE .
```

## 5) Create Web App from container image

```powershell
$ACR_LOGIN_SERVER = (az acr show --resource-group $RG --name $ACR --query loginServer -o tsv)
$ACR_USER = (az acr credential show --name $ACR --query username -o tsv)
$ACR_PASS = (az acr credential show --name $ACR --query passwords[0].value -o tsv)

az webapp create `
  --resource-group $RG `
  --plan $PLAN `
  --name $WEBAPP `
  --deployment-container-image-name "$ACR_LOGIN_SERVER/$IMAGE"

az webapp config container set `
  --resource-group $RG `
  --name $WEBAPP `
  --container-image-name "$ACR_LOGIN_SERVER/$IMAGE" `
  --container-registry-url "https://$ACR_LOGIN_SERVER" `
  --container-registry-user $ACR_USER `
  --container-registry-password $ACR_PASS
```

## 6) Configure application settings (database + port)

```powershell
$PGHOST = "$PGSERVER.postgres.database.azure.com"
$JDBC_URL = "jdbc:postgresql://$PGHOST:5432/$PGDB?sslmode=require"

az webapp config appsettings set `
  --resource-group $RG `
  --name $WEBAPP `
  --settings `
    SERVER_PORT=8080 `
    WEBSITES_PORT=8080 `
    SPRING_THYMELEAF_CACHE=true `
    SPRING_DATASOURCE_URL=$JDBC_URL `
    SPRING_DATASOURCE_USERNAME=$PGADMIN `
    SPRING_DATASOURCE_PASSWORD=$PGPASSWORD
```

If authentication fails, try `SPRING_DATASOURCE_USERNAME=$PGADMIN@$PGSERVER`.

## 7) Open and test

```powershell
$APP_URL = (az webapp show --resource-group $RG --name $WEBAPP --query defaultHostName -o tsv)
"https://$APP_URL"
```

Open the printed URL in a browser and test registration/login/event creation.

## 8) Add your custom domain

In Azure Portal:
- Open App Service -> Custom domains -> Add custom domain
- Follow Azure validation steps (usually CNAME and TXT record)
- After validation, bind the hostname
- Add a managed certificate and enable HTTPS binding

Recommended hostname:
- `calendar.yourdomain.com`

## 9) Update deployment for new code

After code changes:

```powershell
az acr build --registry $ACR --image $IMAGE .
az webapp restart --resource-group $RG --name $WEBAPP
```

## Cost-saving notes

- Start with B1 tiers for App Service and PostgreSQL during portfolio phase
- Stop or scale down non-critical resources when not in use
- Set Azure Budget alerts immediately after provisioning
