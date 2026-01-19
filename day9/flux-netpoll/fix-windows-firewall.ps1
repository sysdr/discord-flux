# Flux Netpoll - Windows Firewall Fix Script
# Run this script as Administrator in PowerShell

Write-Host "╔════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║   FLUX NETPOLL - FIREWALL FIX        ║" -ForegroundColor Green
Write-Host "╚════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""

# Check if running as Administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "❌ This script must be run as Administrator!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Right-click PowerShell and select 'Run as Administrator'" -ForegroundColor Yellow
    Write-Host "Then run this script again." -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ Running as Administrator" -ForegroundColor Green
Write-Host ""

# Remove existing rule if it exists
Write-Host "🔍 Checking for existing firewall rules..." -ForegroundColor Cyan
$existingRule = Get-NetFirewallRule -DisplayName "WSL Dashboard Port 8080" -ErrorAction SilentlyContinue
if ($existingRule) {
    Write-Host "   Removing existing rule..." -ForegroundColor Yellow
    Remove-NetFirewallRule -DisplayName "WSL Dashboard Port 8080" -ErrorAction SilentlyContinue
}

# Create new firewall rule
Write-Host "🔧 Creating firewall rule for port 8080..." -ForegroundColor Cyan
try {
    New-NetFirewallRule -DisplayName "WSL Dashboard Port 8080" `
        -Direction Inbound `
        -LocalPort 8080 `
        -Protocol TCP `
        -Action Allow `
        -Profile Any `
        -Description "Allow WSL Flux Netpoll Dashboard access on port 8080"
    
    Write-Host "✅ Firewall rule created successfully!" -ForegroundColor Green
    Write-Host ""
    
    Write-Host "📊 Try accessing the dashboard at:" -ForegroundColor Cyan
    Write-Host "   http://localhost:8080/dashboard" -ForegroundColor White
    Write-Host "   http://127.0.0.1:8080/dashboard" -ForegroundColor White
    Write-Host ""
    
    Write-Host "✅ Firewall fix complete!" -ForegroundColor Green
    
} catch {
    Write-Host "❌ Error creating firewall rule: $_" -ForegroundColor Red
    exit 1
}
