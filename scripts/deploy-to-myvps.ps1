[CmdletBinding()]
param(
    [string]$Target = "myvps"
)

$ErrorActionPreference = "Stop"

Write-Host "Deploying EventShare to SSH target '$Target'..."
$remoteCommand = 'bash -lc ''cd "$HOME/apps/eventshare" && bash scripts/deploy-prod.sh'''
ssh -o BatchMode=yes -o ConnectTimeout=10 $Target $remoteCommand

if ($LASTEXITCODE -ne 0) {
    throw "Remote deployment failed with exit code $LASTEXITCODE."
}

Write-Host "Remote deployment completed."
